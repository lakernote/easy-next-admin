package com.laker.admin.module.assistant.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantRuntimeConfigurationTest {

    @Test
    void shouldCreateDedicatedAssistantRuntimeResourcesFromConfiguration() {
        AssistantRuntimeProperties properties = new AssistantRuntimeProperties();
        properties.getExecutor().setCoreSize(3);
        properties.getExecutor().setMaxSize(7);
        properties.getExecutor().setQueueCapacity(11);
        properties.setHeartbeatSchedulerPoolSize(4);
        AssistantRuntimeConfiguration configuration = new AssistantRuntimeConfiguration();

        ThreadPoolTaskExecutor executor = configuration.assistantStreamExecutor(properties);
        ThreadPoolTaskScheduler scheduler = configuration.assistantSseHeartbeatScheduler(properties);
        scheduler.initialize();

        assertThat(executor.getCorePoolSize()).isEqualTo(3);
        assertThat(executor.getMaxPoolSize()).isEqualTo(7);
        assertThat(executor.getQueueCapacity()).isEqualTo(11);
        assertThat(executor.getThreadNamePrefix()).isEqualTo("assistant-runtime-");
        assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(4);
        assertThat(scheduler.getThreadNamePrefix()).isEqualTo("assistant-heartbeat-");
        assertThat(scheduler.getScheduledThreadPoolExecutor().getRemoveOnCancelPolicy()).isTrue();

        scheduler.shutdown();
    }
}
