package com.laker.admin.config.thread;

import com.laker.admin.common.constant.EasyNextAdminConstants;
import com.laker.admin.infrastructure.thread.EasyNextAdminMdcThreadPoolExecutor;
import io.github.lakernote.meerkat.trace.TraceContextTaskDecorator;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class EasyThreadPoolConfigTest {

    @Test
    void shouldNotCreateTraceIdWhenObservabilityDecoratorIsUnavailable() throws InterruptedException {
        MDC.clear();
        ThreadPoolTaskExecutor executor = new EasyThreadPoolConfig().easyThreadPool();
        executor.initialize();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<String> traceId = new AtomicReference<>();

        executor.execute(() -> {
            try {
                traceId.set(MDC.get(EasyNextAdminConstants.TRACE_ID));
            } catch (Throwable ex) {
                error.set(ex);
            } finally {
                latch.countDown();
            }
        });

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(error).hasValue(null);
        assertThat(traceId.get()).isNull();
        executor.shutdown();
    }

    @Test
    void shouldNotCreateTraceIdInRawExecutorWithoutObservabilityDecorator() throws InterruptedException {
        MDC.clear();
        EasyNextAdminMdcThreadPoolExecutor executor = new EasyThreadPoolConfig().businessMdcThreadPool();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> traceId = new AtomicReference<>();

        executor.execute(() -> {
            traceId.set(MDC.get(EasyNextAdminConstants.TRACE_ID));
            latch.countDown();
        });

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(traceId.get()).isNull();
        executor.shutdownNow();
    }

    @Test
    void shouldCreateTraceIdInRawExecutorWhenObservabilityDecoratorIsAvailable() throws InterruptedException {
        MDC.clear();
        EasyThreadPoolConfig config = new EasyThreadPoolConfig(
                new EasyThreadPoolProperties(), new TraceContextTaskDecorator());
        EasyNextAdminMdcThreadPoolExecutor executor = config.businessMdcThreadPool();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> traceId = new AtomicReference<>();

        executor.execute(() -> {
            traceId.set(MDC.get(EasyNextAdminConstants.TRACE_ID));
            latch.countDown();
        });

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(traceId.get()).hasSize(32);
        executor.shutdownNow();
    }

    @Test
    void periodicSchedulerShouldNotInheritRegistrationRequestTrace() throws InterruptedException {
        String registrationTraceId = "0123456789abcdef0123456789abcdef";
        MDC.put(EasyNextAdminConstants.TRACE_ID, registrationTraceId);
        ThreadPoolTaskScheduler scheduler = new EasyThreadPoolConfig().easyTaskThreadPool();
        scheduler.initialize();
        CountDownLatch latch = new CountDownLatch(2);
        AtomicBoolean inheritedRegistrationTrace = new AtomicBoolean();

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            inheritedRegistrationTrace.compareAndSet(false,
                    registrationTraceId.equals(MDC.get(EasyNextAdminConstants.TRACE_ID)));
            latch.countDown();
        }, Instant.now().plusMillis(100), Duration.ofMillis(20));
        MDC.clear();

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(inheritedRegistrationTrace).isFalse();
        future.cancel(true);
        scheduler.shutdown();
    }

    @Test
    void shouldApplyConfiguredBusinessExecutorProperties() {
        EasyThreadPoolProperties properties = new EasyThreadPoolProperties();
        properties.getBusiness().setCoreSize(3);
        properties.getBusiness().setMaxSize(7);
        properties.getBusiness().setQueueCapacity(11);
        properties.getBusiness().setThreadNamePrefix("custom-business-");
        EasyThreadPoolConfig config = new EasyThreadPoolConfig(properties);

        ThreadPoolTaskExecutor executor = config.easyThreadPool();

        assertThat(executor.getCorePoolSize()).isEqualTo(3);
        assertThat(executor.getMaxPoolSize()).isEqualTo(7);
        assertThat(executor.getQueueCapacity()).isEqualTo(11);
        assertThat(executor.getThreadNamePrefix()).isEqualTo("custom-business-");
    }

    @Test
    void shouldApplyConfiguredSchedulerProperties() {
        EasyThreadPoolProperties properties = new EasyThreadPoolProperties();
        properties.getScheduler().setPoolSize(4);
        properties.getScheduler().setThreadNamePrefix("custom-job-");
        properties.getScheduler().setAwaitTerminationSeconds(15);
        EasyThreadPoolConfig config = new EasyThreadPoolConfig(properties);

        ThreadPoolTaskScheduler scheduler = config.easyTaskThreadPool();
        scheduler.initialize();

        assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(4);
        assertThat(scheduler.getThreadNamePrefix()).isEqualTo("custom-job-");
        assertThat(scheduler.getScheduledThreadPoolExecutor().getRemoveOnCancelPolicy()).isTrue();
        scheduler.shutdown();
    }

    @Test
    void rejectionHandlerShouldFailFastInsteadOfDroppingTasks() {
        EasyThreadPoolConfig.CustomRejectedExecutionHandler handler = new EasyThreadPoolConfig.CustomRejectedExecutionHandler();

        assertThatThrownBy(() -> handler.rejectedExecution(() -> {
        }, mock(ThreadPoolExecutor.class)))
                .isInstanceOf(RejectedExecutionException.class);
    }
}
