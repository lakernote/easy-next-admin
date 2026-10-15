package com.laker.admin.module.schedule.core;

import com.laker.admin.infrastructure.lock.IEasyLocker;
import com.laker.admin.infrastructure.lock.base.EasyLocker;
import com.laker.admin.module.schedule.enums.JobExecutionMode;
import com.laker.admin.module.schedule.enums.JobStateEnum;
import com.laker.admin.module.schedule.service.IScheduleJobService;
import org.junit.jupiter.api.BeforeEach;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduleJobManagerTest {
    private ScheduleInstanceRegistry instanceRegistry;

    @BeforeEach
    void setUp() {
        instanceRegistry = mock(ScheduleInstanceRegistry.class);
        when(instanceRegistry.getInstanceId()).thenReturn("test-instance");
    }

    @Test
    void scheduledJobSkipsExecutionWhenAnotherInstanceOwnsLock() throws Exception {
        CapturingTaskScheduler scheduler = new CapturingTaskScheduler();
        ScheduleJobStore store = singleJobStore();
        RecordingJobHandler handler = new RecordingJobHandler();
        ScheduleJobCallback callback = mock(ScheduleJobCallback.class);
        IEasyLocker easyLocker = mock(IEasyLocker.class);
        when(easyLocker.tryAcquire(eq("schedule:job:sample_job"), any(Duration.class))).thenReturn(null);
        ScheduleJobManager manager = new ScheduleJobManager(scheduler, store, List.of(callback), mock(IScheduleJobService.class),
                Map.of("sampleJob", handler), easyLocker, instanceRegistry);

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
                Map.of("sampleJob", handler), easyLocker, instanceRegistry);

        manager.run();
        scheduler.runCapturedTask();

        assertThat(handler.executeCount()).isOne();
        verify(callback).start(any());
        verify(callback).end(any());
        verify(easyLocker).release(lock);
    }

    @Test
    void broadcastJobExecutesOnCurrentInstanceWithoutDistributedLock() throws Exception {
        CapturingTaskScheduler scheduler = new CapturingTaskScheduler();
        ScheduleJobStore store = jobStoreWithExecutionMode(JobExecutionMode.BROADCAST);
        RecordingJobHandler handler = new RecordingJobHandler();
        ScheduleJobCallback callback = mock(ScheduleJobCallback.class);
        IEasyLocker easyLocker = mock(IEasyLocker.class);
        ScheduleJobManager manager = new ScheduleJobManager(scheduler, store, List.of(callback), mock(IScheduleJobService.class),
                Map.of("sampleJob", handler), easyLocker, instanceRegistry);

        manager.run();
        scheduler.runCapturedTask();

        assertThat(handler.executeCount()).isOne();
        verify(easyLocker, never()).tryAcquire(any(), any(Duration.class));
        verify(easyLocker, never()).release(any());
        verify(callback).start(any());
        verify(callback).end(any());
    }

    @Test
    void scheduledJobSkipsRegistrationWhenHandlerClassIsMissing() throws Exception {
        CapturingTaskScheduler scheduler = new CapturingTaskScheduler();
        ScheduleJobStore store = jobStoreWithClassName("com.laker.admin.MissingJobHandler");
        RecordingJobHandler handler = new RecordingJobHandler();
        ScheduleJobCallback callback = mock(ScheduleJobCallback.class);
        IEasyLocker easyLocker = mock(IEasyLocker.class);
        ScheduleJobManager manager = new ScheduleJobManager(scheduler, store, List.of(callback), mock(IScheduleJobService.class),
                Map.of("sampleJob", handler), easyLocker, instanceRegistry);

        manager.run();

        assertThat(handler.executeCount()).isZero();
        assertThat(manager.jvmJobList()).isEmpty();
        verify(callback, never()).start(any());
        verify(easyLocker, never()).tryAcquire(any(), any(Duration.class));
    }

    @Test
    void fatalJobErrorIsReportedReleasedAndRethrown() throws Exception {
        CapturingTaskScheduler scheduler = new CapturingTaskScheduler();
        ScheduleJobStore store = jobStore(FatalJobHandler.class.getName(), JobExecutionMode.SINGLETON);
        FatalJobHandler handler = new FatalJobHandler();
        ScheduleJobCallback callback = mock(ScheduleJobCallback.class);
        IEasyLocker easyLocker = mock(IEasyLocker.class);
        EasyLocker lock = EasyLocker.builder().key("schedule:job:sample_job").token("token-fatal").build();
        when(easyLocker.tryAcquire(eq("schedule:job:sample_job"), any(Duration.class))).thenReturn(lock);
        ScheduleJobManager manager = new ScheduleJobManager(scheduler, store, List.of(callback),
                mock(IScheduleJobService.class), Map.of("fatalJob", handler), easyLocker, instanceRegistry);

        manager.run();

        assertThatThrownBy(scheduler::runCapturedTask)
                .isInstanceOf(OutOfMemoryError.class)
                .hasMessage("simulated job allocation failure");
        verify(callback).start(any());
        verify(callback).exception(any(), any(OutOfMemoryError.class));
        verify(callback).end(any());
        verify(easyLocker).release(lock);
    }

    @Test
    void callbackCleanupFailuresDoNotReplaceFatalJobError() throws Exception {
        CapturingTaskScheduler scheduler = new CapturingTaskScheduler();
        OutOfMemoryError failure = new OutOfMemoryError("original job allocation failure");
        FatalJobHandler handler = new FatalJobHandler(failure);
        ScheduleJobStore store = jobStore(FatalJobHandler.class.getName(), JobExecutionMode.SINGLETON);
        ScheduleJobCallback exceptionFailureCallback = mock(ScheduleJobCallback.class);
        ScheduleJobCallback endFailureCallback = mock(ScheduleJobCallback.class);
        ScheduleJobCallback healthyCallback = mock(ScheduleJobCallback.class);
        doThrow(new IllegalStateException("exception callback failed"))
                .when(exceptionFailureCallback).exception(any(), same(failure));
        doThrow(new AssertionError("end callback failed"))
                .when(endFailureCallback).end(any());
        IEasyLocker easyLocker = mock(IEasyLocker.class);
        EasyLocker lock = EasyLocker.builder().key("schedule:job:sample_job").token("token-callback-failure").build();
        when(easyLocker.tryAcquire(eq("schedule:job:sample_job"), any(Duration.class))).thenReturn(lock);
        IllegalStateException releaseFailure = new IllegalStateException("lock release failed after OOM");
        doThrow(releaseFailure).when(easyLocker).release(lock);
        ScheduleJobManager manager = new ScheduleJobManager(scheduler, store,
                List.of(exceptionFailureCallback, endFailureCallback, healthyCallback),
                mock(IScheduleJobService.class), Map.of("fatalJob", handler), easyLocker, instanceRegistry);

        manager.run();

        assertThatThrownBy(scheduler::runCapturedTask).isSameAs(failure);
        assertThat(failure.getSuppressed()).containsExactly(releaseFailure);
        verify(endFailureCallback).exception(any(), same(failure));
        verify(healthyCallback).exception(any(), same(failure));
        verify(exceptionFailureCallback).end(any());
        verify(healthyCallback).end(any());
        verify(easyLocker).release(lock);
    }

    @Test
    void lockReleaseFailureAfterSuccessfulJobIsPropagated() throws Exception {
        CapturingTaskScheduler scheduler = new CapturingTaskScheduler();
        ScheduleJobStore store = singleJobStore();
        RecordingJobHandler handler = new RecordingJobHandler();
        ScheduleJobCallback callback = mock(ScheduleJobCallback.class);
        IEasyLocker easyLocker = mock(IEasyLocker.class);
        EasyLocker lock = EasyLocker.builder().key("schedule:job:sample_job").token("token-release-failure").build();
        when(easyLocker.tryAcquire(eq("schedule:job:sample_job"), any(Duration.class))).thenReturn(lock);
        IllegalStateException releaseFailure = new IllegalStateException("lock release failed");
        doThrow(releaseFailure).when(easyLocker).release(lock);
        ScheduleJobManager manager = new ScheduleJobManager(scheduler, store, List.of(callback),
                mock(IScheduleJobService.class), Map.of("sampleJob", handler), easyLocker, instanceRegistry);

        manager.run();

        assertThatThrownBy(scheduler::runCapturedTask).isSameAs(releaseFailure);
        assertThat(handler.executeCount()).isOne();
        verify(callback).end(any());
        verify(easyLocker).release(lock);
    }

    @Test
    void callbackStartFailureMustNotSkipTheBusinessJobOrRemainingCallbacks() throws Exception {
        CapturingTaskScheduler scheduler = new CapturingTaskScheduler();
        ScheduleJobStore store = singleJobStore();
        RecordingJobHandler handler = new RecordingJobHandler();
        ScheduleJobCallback startedCallback = mock(ScheduleJobCallback.class);
        ScheduleJobCallback failingStartCallback = mock(ScheduleJobCallback.class);
        ScheduleJobCallback untouchedCallback = mock(ScheduleJobCallback.class);
        IllegalStateException startFailure = new IllegalStateException("callback start failed");
        doThrow(startFailure).when(failingStartCallback).start(any());
        IEasyLocker easyLocker = mock(IEasyLocker.class);
        EasyLocker lock = EasyLocker.builder().key("schedule:job:sample_job").token("token-start-failure").build();
        when(easyLocker.tryAcquire(eq("schedule:job:sample_job"), any(Duration.class))).thenReturn(lock);
        ScheduleJobManager manager = new ScheduleJobManager(scheduler, store,
                List.of(startedCallback, failingStartCallback, untouchedCallback),
                mock(IScheduleJobService.class), Map.of("sampleJob", handler), easyLocker, instanceRegistry);

        manager.run();
        scheduler.runCapturedTask();

        assertThat(handler.executeCount()).isOne();
        verify(startedCallback, never()).exception(any(), any());
        verify(failingStartCallback, never()).exception(any(), any());
        verify(startedCallback).end(any());
        verify(failingStartCallback).end(any());
        verify(untouchedCallback).start(any());
        verify(untouchedCallback, never()).exception(any(), any());
        verify(untouchedCallback).end(any());
        verify(easyLocker).release(lock);
    }

    private ScheduleJobStore singleJobStore() {
        return jobStoreWithClassName(RecordingJobHandler.class.getName());
    }

    private ScheduleJobStore jobStoreWithExecutionMode(JobExecutionMode executionMode) {
        return jobStore(RecordingJobHandler.class.getName(), executionMode);
    }

    private ScheduleJobStore jobStoreWithClassName(String jobClassName) {
        return jobStore(jobClassName, JobExecutionMode.SINGLETON);
    }

    private ScheduleJobStore jobStore(String jobClassName, JobExecutionMode executionMode) {
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
                        .executionMode(executionMode)
                        .enable(true)
                        .jobState(JobStateEnum.START)
                        .build();
            }

            @Override
            public List<ScheduleJobDefinition> findAll() {
                return List.of(findByJobCode("sample_job"));
            }
        };
    }

    @EasyJob(jobCode = "sample_job", jobName = "测试任务", cron = "0/1 * * * * ?")
    static class RecordingJobHandler implements EasyJobHandler {
        private final AtomicInteger executeCount = new AtomicInteger();

        @Override
        public void execute(Map<String, Object> params) {
            executeCount.incrementAndGet();
        }

        int executeCount() {
            return executeCount.get();
        }
    }

    @EasyJob(jobCode = "sample_job", jobName = "OOM 测试任务", cron = "0/1 * * * * ?")
    static class FatalJobHandler implements EasyJobHandler {
        private final OutOfMemoryError failure;

        FatalJobHandler() {
            this(new OutOfMemoryError("simulated job allocation failure"));
        }

        FatalJobHandler(OutOfMemoryError failure) {
            this.failure = failure;
        }

        @Override
        public void execute(Map<String, Object> params) {
            throw failure;
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

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
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
