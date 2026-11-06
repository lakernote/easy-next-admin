package com.laker.admin.infrastructure.observability.metrics;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers metrics owned by EasyNextAdmin, independently of the Meerkat starter. */
@Configuration(proxyBeanMethods = false)
public class EasyAdminMetricsConfig {

    @Bean
    EasyAdminMetrics easyAdminMetrics(MeterRegistry meterRegistry, EasyNextAdminConfig config) {
        return new EasyAdminMetrics(meterRegistry, config.getFeatures().isMonitor());
    }
}
