package com.laker.observability.autoconfigure;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.SchedulingConfigurer;

/** Enables Spring's native @Scheduled observations through its scheduling extension point. */
@AutoConfiguration(after = ObservabilityAutoConfiguration.class)
@ConditionalOnProperty(prefix = "easy.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "easy.observability.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledTaskObservationAutoConfiguration {
    @Bean
    SchedulingConfigurer easyObservabilitySchedulingConfigurer(ObservationRegistry registry) {
        return registrar -> registrar.setObservationRegistry(registry);
    }
}
