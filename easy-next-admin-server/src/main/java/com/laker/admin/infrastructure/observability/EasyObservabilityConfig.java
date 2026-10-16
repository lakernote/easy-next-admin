package com.laker.admin.infrastructure.observability;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.observability.config.EasyObservabilityProperties;
import com.laker.observability.metrics.BusinessMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class EasyObservabilityConfig {

    @Bean
    BusinessMetrics businessMetrics(MeterRegistry meterRegistry,
                                    EasyNextAdminConfig config,
                                    EasyObservabilityProperties observability) {
        return new BusinessMetrics(meterRegistry,
                observability.isEnabled()
                        && observability.getMetrics().isEnabled()
                        && config.getFeatures().isMonitor()
                        && config.getMonitor().getDiagnosis().isEnabled());
    }
}
