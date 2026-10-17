package com.laker.observability.autoconfigure;

import com.laker.observability.integration.kafka.TraceIdProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Adds trace ID propagation to Boot's producer factory without replacing configured producer interceptors. */
@AutoConfiguration(after = KafkaTraceAutoConfiguration.class)
@ConditionalOnClass({ProducerInterceptor.class, DefaultKafkaProducerFactoryCustomizer.class})
@ConditionalOnProperty(prefix = "easy.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "easy.observability.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "easy.observability.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KafkaProducerTraceAutoConfiguration {

    @Bean
    DefaultKafkaProducerFactoryCustomizer easyObservabilityProducerTraceInstaller() {
        String interceptorClass = TraceIdProducerInterceptor.class.getName();
        return factory -> {
            Object configured = factory.getConfigurationProperties().get(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG);
            List<Object> interceptors = interceptorClasses(configured);
            if (interceptors.stream().noneMatch(value -> interceptorClass.equals(className(value)))) {
                interceptors.add(interceptorClass);
                factory.updateConfigs(Map.of(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, List.copyOf(interceptors)));
            }
        };
    }

    private static List<Object> interceptorClasses(Object configured) {
        if (configured instanceof Collection<?> values) return new ArrayList<>(values);
        if (configured instanceof String value && !value.isBlank()) {
            return new ArrayList<>(List.of(value.split("\\s*,\\s*")));
        }
        if (configured == null) return new ArrayList<>();
        return new ArrayList<>(List.of(configured));
    }

    private static String className(Object value) {
        return value instanceof Class<?> type ? type.getName() : String.valueOf(value).trim();
    }
}
