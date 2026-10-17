package com.laker.observability.autoconfigure;

import com.laker.observability.metrics.RuntimeMetricsBinder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/** Supplies standard JVM and host meters when the host application has not configured them. */
@AutoConfiguration(afterName = {
        "org.springframework.boot.actuate.autoconfigure.metrics.JvmMetricsAutoConfiguration",
        "org.springframework.boot.actuate.autoconfigure.metrics.SystemMetricsAutoConfiguration"
})
@ConditionalOnProperty(prefix = "easy.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "easy.observability.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass({JvmMemoryMetrics.class, ProcessorMetrics.class})
public class RuntimeMetricsAutoConfiguration {
    @Bean(destroyMethod = "close")
    RuntimeMetricsBinder runtimeMetricsBinder(MeterRegistry registry) {
        return new RuntimeMetricsBinder(registry);
    }
}
