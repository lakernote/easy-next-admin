package com.laker.admin.module.schedule.core;

import com.laker.admin.infrastructure.lock.IEasyLocker;
import com.laker.admin.infrastructure.lock.base.EasyLocker;
import com.laker.admin.module.schedule.enums.JobStateEnum;
import com.laker.admin.module.schedule.service.IScheduleJobService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.SimpleTriggerContext;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduleJobManagerTest {

    @Test
    void scheduledJobSkipsExecutionWhenAnotherInstanceOwnsLock() throws Exception {
        CapturingTaskScheduler scheduler = new CapturingTaskScheduler();
        ScheduleJobStore store = singleJobStore();
        RecordingJobHandler handler = new RecordingJobHandler();
        ScheduleJobCallback callback = mock(ScheduleJobCallback.class);
        IEasyLocker easyLocker = mock(IEasyLocker.class);
        when(easyLocker.tryAcquire(eq("schedule:job:sample_job"), any(Duration.class))).thenReturn(null);
        ScheduleJobManager manager = new ScheduleJobManager(scheduler, store, List.of(callback), mock(IScheduleJobService.class),
                Map.of("sampleJob", handler), easyLocker);

        manager.run();
        scheduler.runCapturedTask();

        assertThat(handler.executeCount()).isZero();
        verify(callback, never()).start(any());
        verify(easyLocker, never()).release(any());
    }

    @Test
    void scheduledJobReleasesDistributedLockAfterExecution() throws Exception {
        CapturingTaskScheduler scheduler = new CapturingTaskScheduler();
        ScheduleJobStore store = singleJobStore();
        RecordingJobHandler handler = new RecordingJobHandler();
        ScheduleJobCallback callback = mock(ScheduleJobCallback.class);
        IEasyLocker easyLocker = mock(IEasyLocker.class);
        EasyLocker lock = EasyLocker.builder().key("schedule:job:sample_job").token("token-1").build();
        when(easyLocker.tryAcquire(eq("schedule:job:sample_job"), any(Duration.class))).thenReturn(lock);
        ScheduleJobManager manager = new ScheduleJobManager(scheduler, store, List.of(callback), mock(IScheduleJobService.class),
                Map.of("sampleJob", handler), easyLocker);

        manager.run();
        scheduler.runCapturedTask();

        assertThat(handler.executeCount()).isOne();
        verify(callback).start(any());
        verify(callback).end(any());
        verify(easyLocker).release(lock);
    }

    @Test
    void scheduledJobRecordsFailureWhenHandlerClassIsMissing() throws Exception {
        CapturingTaskScheduler scheduler = new CapturingTaskScheduler();
        ScheduleJobStore store = jobStoreWithClassName("com.laker.admin.MissingJobHandler");
        RecordingJobHandler handler = new RecordingJobHandler();
        ScheduleJobCallback callback = mock(ScheduleJobCallback.class);
        IEasyLocker easyLocker = mock(IEasyLocker.class);
        EasyLocker lock = EasyLocker.builder().key("schedule:job:sample_job").token("token-1").build();
        when(easyLocker.tryAcquire(eq("schedule:job:sample_job"), any(Duration.class))).thenReturn(lock);
        ScheduleJobManager manager = new ScheduleJobManager(scheduler, store, List.of(callback), mock(IScheduleJobService.class),
                Map.of("sampleJob", handler), easyLocker);

        manager.run();
        scheduler.runCapturedTask();

        assertThat(handler.executeCount()).isZero();
        verify(callback).exception(any(), any(IllegalStateException.class));
        verify(callback).end(any());
        verify(easyLocker).release(lock);
    }

    private ScheduleJobStore singleJobStore() {
        return jobStoreWithClassName(RecordingJobHandler.class.getName());
    }

    private ScheduleJobStore jobStoreWithClassName(String jobClassName) {
        return new ScheduleJobStore() {
            @Override
            public void saveDefinition(ScheduleJobDefinition jobDefinition) {
                // no-op for unit test
            }

            @Override
            public ScheduleJobDefinition findByJobCode(String jobCode) {
                return ScheduleJobDefinition.builder()
                        .jobCode(jobCode)
                        .jobName("测试任务")
                        .jobClassName(jobClassName)
                        .cronExpression("0/1 * * * * ?")
                        .enable(true)
                        .jobState(JobStateEnum.START)
                        .build();
            }
        };
    }

    @EasyJob(jobCode = "sample_job", jobName = "测试任务", cron = "0/1 * * * * ?")
    static class RecordingJobHandler implements EasyJobHandler {
        private final AtomicInteger executeCount = new AtomicInteger();

        @Override
        public void execute(Map map) {
            executeCount.incrementAndGet();
        }

        int executeCount() {
            return executeCount.get();
        }
    }

    static class CapturingTaskScheduler extends ThreadPoolTaskScheduler {
        private final AtomicReference<Runnable> task = new AtomicReference<>();

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
            this.task.set(task);
            Instant nextExecution = trigger.nextExecution(new SimpleTriggerContext());
            assertThat(nextExecution).isNotNull();
            return new NoOpScheduledFuture();
        }

        void runCapturedTask() {
            Runnable captured = task.get();
            assertThat(captured).isNotNull();
            captured.run();
        }
    }

    static class NoOpScheduledFuture implements ScheduledFuture<Object> {
        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed other) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return true;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return null;
        }
    }
}
