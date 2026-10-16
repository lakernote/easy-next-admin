package com.laker.admin.module.assistant.config;

import com.laker.observability.trace.MdcContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ThreadPoolExecutor;

/** Assistant 长耗时任务使用独立资源池，避免占满普通企业业务线程。 */
@Configuration
@EnableConfigurationProperties(AssistantRuntimeProperties.class)
public class AssistantRuntimeConfiguration {
    public static final String STREAM_EXECUTOR_BEAN = "assistantStreamExecutor";
    public static final String HEARTBEAT_SCHEDULER_BEAN = "assistantSseHeartbeatScheduler";

    @Bean(name = STREAM_EXECUTOR_BEAN)
    public ThreadPoolTaskExecutor assistantStreamExecutor(AssistantRuntimeProperties properties) {
        AssistantRuntimeProperties.Executor executor = properties.getExecutor();
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(executor.getCoreSize());
        taskExecutor.setMaxPoolSize(executor.getMaxSize());
        taskExecutor.setQueueCapacity(executor.getQueueCapacity());
        taskExecutor.setThreadNamePrefix("assistant-runtime-");
        taskExecutor.setWaitForTasksToCompleteOnShutdown(true);
        taskExecutor.setAwaitTerminationSeconds(executor.getAwaitTerminationSeconds());
        taskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        taskExecutor.setTaskDecorator(runnable -> MdcContext.wrap(runnable, MdcContext.copy()));
        return taskExecutor;
    }

    @Bean(name = HEARTBEAT_SCHEDULER_BEAN)
    public ThreadPoolTaskScheduler assistantSseHeartbeatScheduler(AssistantRuntimeProperties properties) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.getHeartbeatSchedulerPoolSize());
        scheduler.setThreadNamePrefix("assistant-heartbeat-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }
}
