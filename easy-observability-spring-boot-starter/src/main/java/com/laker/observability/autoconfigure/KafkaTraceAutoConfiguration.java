package com.laker.observability.autoconfigure;

import com.laker.observability.config.EasyObservabilityProperties;
import com.laker.observability.integration.kafka.TraceIdRecordInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.listener.RecordInterceptor;

/** Installs Kafka trace ID propagation only when Spring Kafka is on the application classpath. */
@AutoConfiguration(after = ObservabilityAutoConfiguration.class)
@ConditionalOnClass(RecordInterceptor.class)
@ConditionalOnProperty(prefix = "easy.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "easy.observability.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KafkaTraceAutoConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "easy.observability.trace", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(TraceIdRecordInterceptor.class)
    TraceIdRecordInterceptor traceIdRecordInterceptor(EasyObservabilityProperties properties) {
        return new TraceIdRecordInterceptor(properties);
    }
}
