package com.laker.admin.config.thread;

import com.laker.admin.common.constant.EasyNextAdminConstants;
import com.laker.admin.infrastructure.thread.EasyNextAdminMDCThreadPoolExecutor;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class EasyThreadPoolConfigTest {

    @Test
    void shouldCreateTraceIdWhenMdcContextIsEmpty() throws InterruptedException {
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
        assertThat(traceId.get()).hasSize(32);
        executor.shutdown();
    }

    @Test
    void shouldCreateTraceIdAndKeepUserIdWhenCopiedContextHasNoTraceId() throws InterruptedException {
        MDC.clear();
        MDC.put(EasyNextAdminConstants.USER_ID, "7");
        ThreadPoolTaskExecutor executor = new EasyThreadPoolConfig().easyThreadPool();
        executor.initialize();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> traceId = new AtomicReference<>();
        AtomicReference<String> userId = new AtomicReference<>();

        executor.execute(() -> {
            traceId.set(MDC.get(EasyNextAdminConstants.TRACE_ID));
            userId.set(MDC.get(EasyNextAdminConstants.USER_ID));
            latch.countDown();
        });

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(traceId.get()).hasSize(32);
        assertThat(userId).hasValue("7");
        executor.shutdown();
        MDC.clear();
    }

    @Test
    void shouldPropagateMdcContextToBusinessExecutor() throws InterruptedException {
        MDC.put(EasyNextAdminConstants.TRACE_ID, "trace-1");
        ThreadPoolTaskExecutor executor = new EasyThreadPoolConfig().easyThreadPool();
        executor.initialize();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> traceId = new AtomicReference<>();

        executor.execute(() -> {
            traceId.set(MDC.get(EasyNextAdminConstants.TRACE_ID));
            latch.countDown();
        });

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(traceId).hasValue("trace-1");
        executor.shutdown();
        MDC.clear();
    }

    @Test
    void shouldCreateTraceIdInBusinessMdcExecutorWhenCallerHasNoTraceId() throws InterruptedException {
        MDC.clear();
        EasyNextAdminMDCThreadPoolExecutor executor = new EasyThreadPoolConfig().businessMDCThreadPool();
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
    void shouldCreateTraceIdInSchedulerExecutorWhenTaskHasNoTraceId() throws InterruptedException {
        MDC.clear();
        ThreadPoolTaskScheduler scheduler = new EasyThreadPoolConfig().easyTaskThreadPool();
        scheduler.initialize();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> traceId = new AtomicReference<>();

        scheduler.schedule(() -> {
            traceId.set(MDC.get(EasyNextAdminConstants.TRACE_ID));
            latch.countDown();
        }, Instant.now());

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(traceId.get()).hasSize(32);
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
