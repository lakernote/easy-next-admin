package com.laker.observability.autoconfigure;

import com.laker.observability.integration.executor.ExecutorMetricsBeanPostProcessor;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = ObservabilityAutoConfiguration.class)
@ConditionalOnProperty(prefix = "easy.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "easy.observability.metrics.executor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ExecutorMetricsAutoConfiguration {
    @Bean
    static ExecutorMetricsBeanPostProcessor easyObservabilityExecutorMetrics(ObjectProvider<MeterRegistry> registry) {
        return new ExecutorMetricsBeanPostProcessor(registry);
    }
}
