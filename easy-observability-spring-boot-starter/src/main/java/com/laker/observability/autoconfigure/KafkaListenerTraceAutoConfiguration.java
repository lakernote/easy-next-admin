package com.laker.observability.autoconfigure;

import com.laker.observability.integration.kafka.TraceIdRecordInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.ContainerCustomizer;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.RecordInterceptor;

/** Adds trace propagation to Boot's listener container while keeping interceptors already configured on it. */
@AutoConfiguration(after = KafkaTraceAutoConfiguration.class)
@ConditionalOnClass({ConcurrentMessageListenerContainer.class, ContainerCustomizer.class, RecordInterceptor.class})
@ConditionalOnProperty(prefix = "easy.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "easy.observability.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "easy.observability.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KafkaListenerTraceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ContainerCustomizer.class)
    ContainerCustomizer<Object, Object, ConcurrentMessageListenerContainer<Object, Object>>
    easyObservabilityKafkaListenerTraceInstaller(TraceIdRecordInterceptor traceInterceptor) {
        return container -> {
            RecordInterceptor<Object, Object> propagation = cast(traceInterceptor);
            container.setRecordInterceptor(propagation);
        };
    }

    @SuppressWarnings("unchecked")
    private static RecordInterceptor<Object, Object> cast(TraceIdRecordInterceptor interceptor) {
        return (RecordInterceptor<Object, Object>) (RecordInterceptor<?, ?>) interceptor;
    }
}
