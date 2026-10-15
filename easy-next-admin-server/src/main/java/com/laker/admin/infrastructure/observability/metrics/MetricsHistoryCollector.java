package com.laker.admin.infrastructure.observability.metrics;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.infrastructure.observability.trace.RecentTraceStore;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;

/** Periodically snapshots selected low-cardinality Actuator/Micrometer meters to RRD4J. */
@Component
@Slf4j
public class MetricsHistoryCollector {
    private static final String CANDIDATE_METRIC_PREFIX = "easy.diagnosis.metrics.candidates.";
    private static final long RRD_COLLECTION_INTERVAL_MS = 60_000L;
    private static final long MAX_PERSIST_INTERVAL_MS = 120_000L;
    private final MeterRegistry meterRegistry;
    private final LocalMetricTimeSeriesStore metricStore;
    private final EasyNextAdminConfig config;
    private final long persistIntervalMs;
    private final int maxCandidateMeters;
    private final AtomicLong lastPersistedAt = new AtomicLong();
    private final AtomicLong lastMatchingMeters = new AtomicLong();
    private final AtomicLong lastSelectedMeters = new AtomicLong();
    private final AtomicLong droppedCandidateMeters = new AtomicLong();

    private static final Comparator<Meter> PERSIST_ORDER = (left, right) -> {
        Meter.Id leftId = left.getId();
        Meter.Id rightId = right.getId();
        int priority = Integer.compare(LocalMetricTimeSeriesStore.metricPriority(leftId.getName()),
                LocalMetricTimeSeriesStore.metricPriority(rightId.getName()));
        if (priority != 0) return priority;
        int name = leftId.getName().compareTo(rightId.getName());
        if (name != 0) return name;
        return compareTags(leftId, rightId);
    };

    @Autowired
    public MetricsHistoryCollector(MeterRegistry meterRegistry,
                                   LocalMetricTimeSeriesStore metricStore,
                                   EasyNextAdminConfig config,
                                   @Value("${easy.monitor.diagnosis.metrics-persist-interval-ms:60000}")
                                   long persistIntervalMs,
                                   @Value("${easy.monitor.diagnosis.metrics-max-series:512}")
                                   int maxCandidateMeters) {
        this.meterRegistry = meterRegistry;
        this.metricStore = metricStore;
        this.config = config;
        // RRD files use a 60-second step and a 180-second heartbeat. Keeping writes within two minutes
        // preserves a margin for scheduler drift and prevents custom configuration from creating UNKNOWN gaps.
        this.persistIntervalMs = Math.min(MAX_PERSIST_INTERVAL_MS,
                Math.max(RRD_COLLECTION_INTERVAL_MS, persistIntervalMs));
        this.maxCandidateMeters = Math.max(1, maxCandidateMeters);
        registerCollectorGauges(meterRegistry);
    }

    MetricsHistoryCollector(MeterRegistry meterRegistry,
                            LocalMetricTimeSeriesStore metricStore,
                            EasyNextAdminConfig config,
                            long persistIntervalMs) {
        this(meterRegistry, metricStore, config, persistIntervalMs, 512);
    }

    @Scheduled(fixedDelay = RRD_COLLECTION_INTERVAL_MS, initialDelay = 15_000L)
    public void collect() {
        if (!config.getFeatures().isMonitor() || !config.getMonitor().getDiagnosis().isEnabled()) return;
        if (!isPersistDue(System.currentTimeMillis())) return;
        try {
            List<Meter> candidates = selectCandidateMeters();
            List<RecentTraceStore.MetricSample> samples = candidates.stream()
                    .map(MetricsHistoryCollector::snapshot)
                    .filter(sample -> !sample.measurements().isEmpty())
                    .toList();
            persistIfDue(samples);
        } catch (Exception exception) {
            log.warn("采集本地 Metrics RRD4J 历史失败", exception);
        }
    }

    /**
     * Selects at most the number of meters the RRD store can retain before reading any measurements. A bounded
     * max-heap avoids materializing and sorting every high-cardinality meter during the once-per-minute collection.
     */
    private List<Meter> selectCandidateMeters() {
        PriorityQueue<Meter> selected = new PriorityQueue<>(maxCandidateMeters, PERSIST_ORDER.reversed());
        long matching = 0;
        for (Meter meter : meterRegistry.getMeters()) {
            if (!isUsefulMetric(meter.getId().getName())) continue;
            matching++;
            if (selected.size() < maxCandidateMeters) {
                selected.offer(meter);
            } else if (PERSIST_ORDER.compare(meter, selected.peek()) < 0) {
                selected.poll();
                selected.offer(meter);
            }
        }
        long dropped = Math.max(0, matching - selected.size());
        lastMatchingMeters.set(matching);
        lastSelectedMeters.set(selected.size());
        if (dropped > 0) droppedCandidateMeters.addAndGet(dropped);
        List<Meter> result = new ArrayList<>(selected);
        result.sort(PERSIST_ORDER);
        return result;
    }

    private static int compareTags(Meter.Id left, Meter.Id right) {
        List<io.micrometer.core.instrument.Tag> leftTags = left.getTags();
        List<io.micrometer.core.instrument.Tag> rightTags = right.getTags();
        int common = Math.min(leftTags.size(), rightTags.size());
        for (int index = 0; index < common; index++) {
            io.micrometer.core.instrument.Tag leftTag = leftTags.get(index);
            io.micrometer.core.instrument.Tag rightTag = rightTags.get(index);
            int key = leftTag.getKey().compareTo(rightTag.getKey());
            if (key != 0) return key;
            int value = leftTag.getValue().compareTo(rightTag.getValue());
            if (value != 0) return value;
        }
        return Integer.compare(leftTags.size(), rightTags.size());
    }

    long droppedCandidateMeters() {
        return droppedCandidateMeters.get();
    }

    private void registerCollectorGauges(MeterRegistry registry) {
        Gauge.builder(CANDIDATE_METRIC_PREFIX + "matched", lastMatchingMeters, AtomicLong::get)
                .description("当前采集周期匹配到的本地历史指标数量")
                .strongReference(true)
                .register(registry);
        Gauge.builder(CANDIDATE_METRIC_PREFIX + "selected", lastSelectedMeters, AtomicLong::get)
                .description("当前采集周期实际选择的本地历史指标数量")
                .strongReference(true)
                .register(registry);
        Gauge.builder(CANDIDATE_METRIC_PREFIX + "dropped", droppedCandidateMeters, AtomicLong::get)
                .description("因候选指标上限累计跳过的本地历史指标数量")
                .strongReference(true)
                .register(registry);
    }

    private boolean isPersistDue(long now) {
        long previous = lastPersistedAt.get();
        return previous <= 0 || now - previous >= persistIntervalMs;
    }

    private void persistIfDue(List<RecentTraceStore.MetricSample> samples) {
        long now = System.currentTimeMillis();
        while (true) {
            long previous = lastPersistedAt.get();
            if (previous > 0 && now - previous < persistIntervalMs) return;
            if (lastPersistedAt.compareAndSet(previous, now)) {
                metricStore.recordMetricsSnapshot(samples);
                return;
            }
        }
    }

    private static RecentTraceStore.MetricSample snapshot(Meter meter) {
        Map<String, String> tags = new LinkedHashMap<>();
        meter.getId().getTags().forEach(tag -> tags.put(tag.getKey(), tag.getValue()));
        Map<String, Double> measurements = new LinkedHashMap<>();
        meter.measure().forEach(measurement -> {
            double value = measurement.getValue();
            if (Double.isFinite(value)) measurements.put(measurement.getStatistic().name(), value);
        });
        return new RecentTraceStore.MetricSample(meter.getId().getName(), tags, measurements);
    }

    private static boolean isUsefulMetric(String name) {
        // These gauges describe the collector itself. Keeping them out of its own RRD candidate set avoids
        // recursive cardinality and preserves all configured slots for application and JVM observations.
        if (name.startsWith(CANDIDATE_METRIC_PREFIX)) return false;
        return name.startsWith("easy.") || name.startsWith("jvm.") || name.startsWith("process.")
                || name.startsWith("system.") || name.startsWith("http.server.requests")
                || name.startsWith("hikaricp.") || name.startsWith("jdbc.connections.")
                || name.startsWith("tomcat.threads.") || name.startsWith("executor.");
    }
}
