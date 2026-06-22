package com.laker.admin.infrastructure.lock.base;

import com.laker.admin.common.constant.EasyNextAdminConstants;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractSimpleIEasyLockerTest {

    @Test
    void lockLifecycleShouldNotCreateTraceId() {
        MDC.clear();
        CapturingTaskScheduler taskScheduler = new CapturingTaskScheduler();
        RecordingLocker locker = new RecordingLocker(taskScheduler);

        EasyLocker lock = locker.tryAcquire("sample", Duration.ofSeconds(30));
        assertThat(lock).isNotNull();
        assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID)).isNull();

        taskScheduler.runCapturedTask();
        assertThat(locker.refreshTraceId()).isNull();
        assertThat(locker.refreshCount()).isOne();

        locker.release(lock);
        assertThat(locker.releaseTraceId()).isNull();
        assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID)).isNull();
    }

    static class RecordingLocker extends AbstractSimpleIEasyLocker {
        private final AtomicInteger refreshCount = new AtomicInteger();
        private final AtomicReference<String> refreshTraceId = new AtomicReference<>();
        private final AtomicReference<String> releaseTraceId = new AtomicReference<>();

        protected RecordingLocker(CapturingTaskScheduler taskScheduler) {
            super(taskScheduler);
        }

        @Override
        protected boolean acquire(String key, String token, Duration expiration) {
            return true;
        }

        @Override
        protected boolean release0(EasyLocker lock) {
            releaseTraceId.set(MDC.get(EasyNextAdminConstants.TRACE_ID));
            return true;
        }

        @Override
        protected boolean refresh(String key, String token, Duration expiration) {
            refreshTraceId.set(MDC.get(EasyNextAdminConstants.TRACE_ID));
            refreshCount.incrementAndGet();
            return true;
        }

        int refreshCount() {
            return refreshCount.get();
        }

        String refreshTraceId() {
            return refreshTraceId.get();
        }

        String releaseTraceId() {
            return releaseTraceId.get();
        }
    }

    static class CapturingTaskScheduler extends ThreadPoolTaskScheduler {
        private final AtomicReference<Runnable> task = new AtomicReference<>();

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
            this.task.set(task);
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
