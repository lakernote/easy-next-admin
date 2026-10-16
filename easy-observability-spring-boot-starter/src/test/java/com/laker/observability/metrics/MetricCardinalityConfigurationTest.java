package com.laker.observability.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricCardinalityConfigurationTest {

    private final MetricCardinalityConfiguration configuration = new MetricCardinalityConfiguration();

    @Test
    void rejectsBusinessMetricWithoutItsRequiredTags() {
        SimpleMeterRegistry registry = registryWithSeriesLimit();

        Timer.builder(BusinessMetrics.API_ACCESS_TIMER)
                .tag("controller", "SysUserController")
                .tag("result", "success")
                .register(registry)
                .record(1, java.util.concurrent.TimeUnit.MILLISECONDS);

        assertThat(registry.find(BusinessMetrics.API_ACCESS_TIMER).timer()).isNull();
    }

    @Test
    void boundsCompleteTagCombinationsInsteadOfOnlyIndividualTagValues() {
        SimpleMeterRegistry registry = registryWithSeriesLimit();
        int attempts = BusinessMetrics.API_ACCESS_SERIES_LIMIT + 25;

        for (int i = 0; i < attempts; i++) {
            Timer.builder(BusinessMetrics.API_ACCESS_TIMER)
                    .tags("controller", "Controller", "action", "action" + i, "result", "success")
                    .register(registry);
        }

        long admitted = registry.getMeters().stream()
                .filter(meter -> BusinessMetrics.API_ACCESS_TIMER.equals(meter.getId().getName()))
                .count();
        assertThat(admitted).isEqualTo(BusinessMetrics.API_ACCESS_SERIES_LIMIT);
    }

    @Test
    void includesUnexpectedExtraTagsInTheTotalSeriesLimit() {
        SimpleMeterRegistry registry = registryWithSeriesLimit();
        int attempts = BusinessMetrics.RATE_LIMIT_SERIES_LIMIT + 20;

        for (int i = 0; i < attempts; i++) {
            Counter.builder(BusinessMetrics.RATE_LIMIT_BLOCKED_COUNTER)
                    .tags("rule", "login", "type", "CLIENT_IP", "unexpected", "value-" + i)
                    .register(registry);
        }

        assertThat(registry.find(BusinessMetrics.RATE_LIMIT_BLOCKED_COUNTER).counters())
                .hasSize(BusinessMetrics.RATE_LIMIT_SERIES_LIMIT);
    }

    @Test
    void appliesRequiredTagContractToCounterMetricsToo() {
        SimpleMeterRegistry registry = registryWithSeriesLimit();

        Counter.builder(BusinessMetrics.OUTBOX_MESSAGE_COUNTER)
                .tag("operation", "workflow_notify")
                .register(registry)
                .increment();
        Counter.builder(BusinessMetrics.OUTBOX_MESSAGE_COUNTER)
                .tags("operation", "workflow_notify", "status", "SENT")
                .register(registry)
                .increment();

        assertThat(registry.find(BusinessMetrics.OUTBOX_MESSAGE_COUNTER).counters()).hasSize(1);
        assertThat(registry.find(BusinessMetrics.OUTBOX_MESSAGE_COUNTER)
                .tags("operation", "workflow_notify", "status", "SENT")
                .counter()).isNotNull();
    }

    @Test
    void retainsSqlMapperDimensionLimitInsideTheCombinedGuard() {
        SimpleMeterRegistry registry = registryWithSeriesLimit();

        for (int i = 0; i < 525; i++) {
            Timer.builder("easy.sql.queries")
                    .tags("mapper", "Mapper" + i, "command", "SELECT", "result", "success")
                    .register(registry);
        }

        assertThat(registry.find("easy.sql.queries").timers()).hasSize(500);
    }

    @Test
    void boundsOperationNamesUsedByRequestStatistics() {
        SimpleMeterRegistry registry = registryWithSeriesLimit();

        for (int i = 0; i < 125; i++) {
            Timer.builder("easy.operations.duration")
                    .tags("type", "HTTP", "source", "HTTP", "operation", "GET /route-" + i,
                            "outcome", "success")
                    .register(registry);
        }

        assertThat(registry.find("easy.operations.duration").timers()).hasSize(100);
    }

    @Test
    void reservesSqlSeriesCapacityForEveryOverflowCommandAndOutcome() {
        SimpleMeterRegistry registry = registryWithSeriesLimit();

        for (int mapper = 0; mapper < 494; mapper++) {
            for (String outcome : new String[]{"success", "failure"}) {
                Timer.builder("easy.sql.queries")
                        .tags("mapper", "Mapper" + mapper, "command", "SELECT", "result", outcome)
                        .register(registry);
            }
        }
        for (String command : new String[]{"UNKNOWN", "INSERT", "UPDATE", "DELETE", "SELECT", "FLUSH"}) {
            for (String outcome : new String[]{"success", "failure"}) {
                Timer.builder("easy.sql.queries")
                        .tags("mapper", "unknown", "command", command, "result", outcome)
                        .register(registry);
            }
        }

        assertThat(registry.find("easy.sql.queries").timers()).hasSize(1_000);
        assertThat(registry.find("easy.sql.queries").tag("mapper", "unknown").timers()).hasSize(12);
    }

    @Test
    void leavesUnrelatedMetricsUntouched() {
        SimpleMeterRegistry registry = registryWithSeriesLimit();

        Counter counter = Counter.builder("unrelated.metric").register(registry);
        counter.increment();

        assertThat(registry.find("unrelated.metric").counter().count()).isEqualTo(1);
    }

    private SimpleMeterRegistry registryWithSeriesLimit() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.config().meterFilter(configuration.metricSeriesCardinalityLimit());
        return registry;
    }
}
