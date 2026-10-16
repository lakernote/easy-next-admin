package com.laker.observability.autoconfigure;

import com.laker.observability.config.EasyObservabilityProperties;
import com.laker.observability.integration.feign.TraceIdRequestInterceptor;
import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/** Propagates trace IDs through OpenFeign when it is present. */
@AutoConfiguration(after = ObservabilityAutoConfiguration.class)
@ConditionalOnClass(RequestInterceptor.class)
@ConditionalOnProperty(prefix = "easy.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "easy.observability.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FeignTraceAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(TraceIdRequestInterceptor.class)
    TraceIdRequestInterceptor traceIdRequestInterceptor(EasyObservabilityProperties properties) {
        return new TraceIdRequestInterceptor(properties);
    }
}
