package com.laker.admin.infrastructure.observability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ThreadPoolExecutor;

/** Keeps monitoring maintenance work off Spring's single-thread fallback scheduler. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "easy.features", name = "monitor", havingValue = "true", matchIfMissing = true)
public class DiagnosisSchedulingConfiguration {

    public static final String PERSISTENCE_EXECUTOR = "diagnosisPersistenceExecutor";

    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler diagnosisTaskScheduler(
            @Value("${easy.monitor.diagnosis.scheduler-pool-size:2}") int configuredPoolSize) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(Math.max(2, Math.min(configuredPoolSize, 8)));
        scheduler.setThreadNamePrefix("easy-diagnosis-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }

    /** Keeps sparse slow/error compatibility writes away from request, job and Kafka consumer threads. */
    @Bean(name = PERSISTENCE_EXECUTOR)
    public ThreadPoolTaskExecutor diagnosisPersistenceExecutor(
            @Value("${easy.monitor.diagnosis.persistence-queue-capacity:512}") int configuredQueueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(Math.max(64, Math.min(configuredQueueCapacity, 4_096)));
        executor.setThreadNamePrefix("easy-diagnosis-db-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setAcceptTasksAfterContextClose(false);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
