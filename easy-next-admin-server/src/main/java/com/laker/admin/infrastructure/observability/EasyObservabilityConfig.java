package com.laker.admin.infrastructure.observability;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import io.github.lakernote.observability.config.EasyObservabilityProperties;
import com.laker.admin.infrastructure.observability.metrics.EasyAdminMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class EasyObservabilityConfig {

    @Bean
    EasyAdminMetrics easyAdminMetrics(MeterRegistry meterRegistry,
                                      EasyNextAdminConfig config,
                                      EasyObservabilityProperties observability) {
        return new EasyAdminMetrics(meterRegistry,
                observability.isEnabled()
                        && config.getFeatures().isMonitor());
    }
}
