package com.laker.admin.infrastructure.observability.metrics;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.infrastructure.observability.trace.RecentTraceStore;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class MetricsHistoryCollectorTest {

    @Test
    void shouldPersistOnlySelectedActuatorAndBusinessMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        gauge(registry, "jvm.memory.used", 7);
        gauge(registry, "easy.operations.active", 2);
        gauge(registry, "hikaricp.connections.active", 3);
        gauge(registry, "http.server.requests", 4);
        gauge(registry, "connection.current.gauge", 9);
        Gauge.builder("jvm.invalid", new Object(), ignored -> Double.NaN)
                .strongReference(true)
                .register(registry);
        LocalMetricTimeSeriesStore metricStore = mock(LocalMetricTimeSeriesStore.class);
        MetricsHistoryCollector collector = collector(registry, metricStore, 60_000L);

        collector.collect();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecentTraceStore.MetricSample>> samples = ArgumentCaptor.forClass(List.class);
        verify(metricStore).recordMetricsSnapshot(samples.capture());
        assertThat(samples.getValue()).extracting(RecentTraceStore.MetricSample::name)
                .containsExactly("easy.operations.active", "jvm.memory.used",
                        "hikaricp.connections.active", "http.server.requests");
    }

    @Test
    void shouldNotWriteAgainBeforePersistInterval() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicInteger measurementReads = new AtomicInteger();
        Gauge.builder("process.cpu.usage", measurementReads, ignored -> measurementReads.incrementAndGet())
                .strongReference(true)
                .register(registry);
        LocalMetricTimeSeriesStore metricStore = mock(LocalMetricTimeSeriesStore.class);
        MetricsHistoryCollector collector = collector(registry, metricStore, 300_000L);

        collector.collect();
        collector.collect();

        verify(metricStore, times(1)).recordMetricsSnapshot(org.mockito.ArgumentMatchers.anyList());
        assertThat(measurementReads).hasValue(1);
    }

    @Test
    void shouldReadOnlyTheBoundedHighestPriorityCandidates() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicInteger coreReads = measuredGauge(registry, "jvm.memory.used");
        AtomicInteger platformReads = measuredGauge(registry, "hikaricp.connections.active");
        AtomicInteger optionalReads = measuredGauge(registry, "http.server.requests");
        LocalMetricTimeSeriesStore metricStore = mock(LocalMetricTimeSeriesStore.class);
        MetricsHistoryCollector collector = new MetricsHistoryCollector(
                registry, metricStore, new EasyNextAdminConfig(), 60_000L, 2);

        collector.collect();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecentTraceStore.MetricSample>> samples = ArgumentCaptor.forClass(List.class);
        verify(metricStore).recordMetricsSnapshot(samples.capture());
        assertThat(samples.getValue()).extracting(RecentTraceStore.MetricSample::name)
                .containsExactly("jvm.memory.used", "hikaricp.connections.active");
        assertThat(coreReads).hasValue(1);
        assertThat(platformReads).hasValue(1);
        assertThat(optionalReads).hasValue(0);
        assertThat(collector.droppedCandidateMeters()).isEqualTo(1);
        assertThat(registry.get("easy.diagnosis.metrics.candidates.matched").gauge().value()).isEqualTo(3);
        assertThat(registry.get("easy.diagnosis.metrics.candidates.selected").gauge().value()).isEqualTo(2);
        assertThat(registry.get("easy.diagnosis.metrics.candidates.dropped").gauge().value()).isEqualTo(1);
    }

    private static MetricsHistoryCollector collector(SimpleMeterRegistry registry,
                                                      LocalMetricTimeSeriesStore metricStore,
                                                      long persistIntervalMs) {
        return new MetricsHistoryCollector(registry, metricStore, new EasyNextAdminConfig(), persistIntervalMs);
    }

    private static AtomicInteger gauge(SimpleMeterRegistry registry, String name, int initialValue) {
        AtomicInteger value = new AtomicInteger(initialValue);
        Gauge.builder(name, value, AtomicInteger::get).strongReference(true).register(registry);
        return value;
    }

    private static AtomicInteger measuredGauge(SimpleMeterRegistry registry, String name) {
        AtomicInteger reads = new AtomicInteger();
        Gauge.builder(name, reads, ignored -> reads.incrementAndGet()).strongReference(true).register(registry);
        return reads;
    }
}
