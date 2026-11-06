package com.laker.admin.infrastructure.observability.metrics;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EasyAdminMetricsConfigTest {

    @Test
    void shouldRecordApplicationMetricsWhenMonitoringFeatureIsEnabled() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        EasyNextAdminConfig config = new EasyNextAdminConfig();

        EasyAdminMetrics metrics = new EasyAdminMetricsConfig().easyAdminMetrics(registry, config);
        metrics.recordOutboxMessage("publish", "success");

        assertThat(registry.get(EasyAdminMetrics.OUTBOX_MESSAGES).counter().count()).isEqualTo(1);
    }

    @Test
    void shouldKeepNoOpBeanWhenMonitoringFeatureIsDisabled() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getFeatures().setMonitor(false);

        EasyAdminMetrics metrics = new EasyAdminMetricsConfig().easyAdminMetrics(registry, config);
        metrics.recordOutboxMessage("publish", "success");

        assertThat(registry.find(EasyAdminMetrics.OUTBOX_MESSAGES).counter()).isNull();
    }
}
