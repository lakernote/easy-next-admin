package com.laker.admin.config.thread;

import com.laker.admin.infrastructure.observability.trace.EasyMdcContext;
import com.laker.admin.infrastructure.observability.trace.EasyTraceIdContext;
import com.laker.admin.infrastructure.thread.EasyNextAdminMDCThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableConfigurationProperties(EasyThreadPoolProperties.class)
@Slf4j
public class EasyThreadPoolConfig {

    private final EasyThreadPoolProperties properties;

    @Autowired
    public EasyThreadPoolConfig(EasyThreadPoolProperties properties) {
        this.properties = properties;
    }

    EasyThreadPoolConfig() {
        this(new EasyThreadPoolProperties());
    }

    static {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> log.error("Thread {} got exception", thread, throwable));
    }

    /**
     * 定时任务线程池
     */
    @Bean
    public ThreadPoolTaskScheduler easyTaskThreadPool() {
        EasyThreadPoolProperties.Scheduler scheduler = properties.getScheduler();
        ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
        threadPoolTaskScheduler.setPoolSize(scheduler.getPoolSize());
        threadPoolTaskScheduler.setThreadNamePrefix(scheduler.getThreadNamePrefix());
        threadPoolTaskScheduler.setRemoveOnCancelPolicy(scheduler.isRemoveOnCancelPolicy());
        threadPoolTaskScheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(scheduler.isExecuteExistingDelayedTasksAfterShutdownPolicy());
        threadPoolTaskScheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(scheduler.isContinueExistingPeriodicTasksAfterShutdownPolicy());
        threadPoolTaskScheduler.setWaitForTasksToCompleteOnShutdown(scheduler.isWaitForTasksToCompleteOnShutdown());
        threadPoolTaskScheduler.setAwaitTerminationSeconds(scheduler.getAwaitTerminationSeconds());
        threadPoolTaskScheduler.setRejectedExecutionHandler(new CustomRejectedExecutionHandler());
        threadPoolTaskScheduler.setTaskDecorator(runnable -> wrapWithTrace(runnable, EasyMdcContext.copy()));
        return threadPoolTaskScheduler;
    }

    /**
     * 业务线程池
     */
    @Bean
    public ThreadPoolTaskExecutor easyThreadPool() {
        EasyThreadPoolProperties.Business business = properties.getBusiness();
        ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
        threadPoolTaskExecutor.setCorePoolSize(business.getCoreSize());
        threadPoolTaskExecutor.setMaxPoolSize(business.getMaxSize());
        threadPoolTaskExecutor.setQueueCapacity(business.getQueueCapacity());
        threadPoolTaskExecutor.setThreadNamePrefix(business.getThreadNamePrefix());
        threadPoolTaskExecutor.setWaitForTasksToCompleteOnShutdown(business.isWaitForTasksToCompleteOnShutdown());
        threadPoolTaskExecutor.setAwaitTerminationSeconds(business.getAwaitTerminationSeconds());
        threadPoolTaskExecutor.setRejectedExecutionHandler(new CustomRejectedExecutionHandler());
        threadPoolTaskExecutor.setTaskDecorator(runnable -> wrapWithTrace(runnable, EasyMdcContext.copy()));
        return threadPoolTaskExecutor;
    }

    /**
     * 业务线程池
     */
    @Bean
    public EasyNextAdminMDCThreadPoolExecutor businessMDCThreadPool() {
        EasyThreadPoolProperties.BusinessMdc businessMdc = properties.getBusinessMdc();
        return new EasyNextAdminMDCThreadPoolExecutor(businessMdc.getPoolSize(), businessMdc.getQueueSize(), businessMdc.getThreadNamePrefix());
    }

    // 自定义的拒绝执行处理器，以更好地处理任务被拒绝的情况
    public static class CustomRejectedExecutionHandler implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            log.warn("Task {} rejected from {}", r, executor);
            throw new RejectedExecutionException("Task " + r + " rejected from " + executor);
        }
    }

    private static Runnable wrapWithTrace(Runnable runnable, Map<String, String> contextMap) {
        return EasyMdcContext.wrap(() -> {
            EasyTraceIdContext.getOrCreateTraceId();
            runnable.run();
        }, contextMap);
    }
}
