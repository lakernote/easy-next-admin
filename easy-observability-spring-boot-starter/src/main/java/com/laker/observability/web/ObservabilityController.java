package com.laker.observability.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laker.observability.trace.TraceHistory;
import com.laker.observability.trace.OperationRecord;
import com.laker.observability.trace.IncidentHistory;
import com.laker.observability.trace.TraceArchive;
import com.laker.observability.metrics.history.MetricHistoryStore;
import com.laker.observability.metrics.history.MetricHistoryPoint;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.DistributionSummary;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Read-only data API used by the bundled Easy Observability page. */
@RestController
@RequestMapping("/api/easy-observability")
public class ObservabilityController {
    private final MeterRegistry meterRegistry;
    private final TraceHistory traceHistory;
    private final IncidentHistory incidentHistory;
    private final MetricHistoryStore metricHistoryStore;
    private final TraceArchive traceArchive;
    private final ObjectMapper objectMapper;
    private final ObservabilityAccessPolicy accessPolicy;

    @Autowired
    public ObservabilityController(MeterRegistry meterRegistry, TraceHistory traceHistory,
                                  IncidentHistory incidentHistory, MetricHistoryStore metricHistoryStore,
                                  TraceArchive traceArchive, ObjectMapper objectMapper,
                                  ObservabilityAccessPolicy accessPolicy) {
        this.meterRegistry = meterRegistry;
        this.traceHistory = traceHistory;
        this.incidentHistory = incidentHistory;
        this.metricHistoryStore = metricHistoryStore;
        this.traceArchive = traceArchive;
        this.objectMapper = objectMapper;
        this.accessPolicy = accessPolicy;
    }

    public ObservabilityController(MeterRegistry meterRegistry, TraceHistory traceHistory,
                                  ObservabilityAccessPolicy accessPolicy) {
        this(meterRegistry, traceHistory, null, null, null, new ObjectMapper(), accessPolicy);
    }

    @GetMapping("/metrics")
    public List<MetricRecord> metrics(@RequestParam(defaultValue = "500") int limit) {
        requireAccess();
        return meterRegistry.getMeters().stream().map(ObservabilityController::metricRecord)
                .sorted(Comparator.comparing(MetricRecord::name).thenComparing(item -> item.tags().toString()))
                .limit(Math.max(1, Math.min(limit, 2000))).toList();
    }

    @GetMapping("/traces")
    public List<TraceHistory.TraceRecord> traces(@RequestParam(defaultValue = "100") int limit) {
        requireAccess();
        return allTraces(limit);
    }

    @GetMapping("/request-statistics")
    public List<RequestStatistic> requestStatistics(@RequestParam(defaultValue = "") String source,
                                                    @RequestParam(defaultValue = "200") int limit) {
        requireAccess();
        Map<StatisticKey, MutableStatistic> statistics = new HashMap<>();
        for (Meter meter : meterRegistry.getMeters()) {
            Meter.Id id = meter.getId();
            if (!(meter instanceof Timer timer) || !"easy.operations.duration".equals(id.getName())) continue;
            String operationSource = id.getTag("source");
            String operation = id.getTag("operation");
            if (operationSource == null || operation == null || operation.isBlank()) continue;
            if (!List.of("HTTP", "JOB", "KAFKA_CONSUMER").contains(operationSource)) continue;
            if (!source.isBlank() && !"ALL".equalsIgnoreCase(source)
                    && !source.equalsIgnoreCase(operationSource)) continue;
            StatisticKey key = new StatisticKey(operationSource, operation);
            MutableStatistic value = statistics.computeIfAbsent(key, ignored -> new MutableStatistic());
            long count = timer.count();
            value.count += count;
            value.totalDurationMs += timer.totalTime(TimeUnit.MILLISECONDS);
            value.maxDurationMs = Math.max(value.maxDurationMs, timer.max(TimeUnit.MILLISECONDS));
            if ("failure".equalsIgnoreCase(id.getTag("outcome"))) value.errorCount += count;
        }
        addResourceStatistics(statistics, "easy.operations.cpu.time", true);
        addResourceStatistics(statistics, "easy.operations.allocated", false);
        return statistics.entrySet().stream()
                .map(entry -> entry.getValue().toRecord(entry.getKey()))
                .sorted(Comparator.comparingDouble(RequestStatistic::totalDurationMs).reversed())
                .limit(Math.max(1, Math.min(limit, 500))).toList();
    }

    private void addResourceStatistics(Map<StatisticKey, MutableStatistic> statistics, String metricName,
                                       boolean cpuTime) {
        for (Meter meter : meterRegistry.getMeters()) {
            Meter.Id id = meter.getId();
            if (!(meter instanceof DistributionSummary summary) || !metricName.equals(id.getName())) continue;
            String source = id.getTag("source");
            String operation = id.getTag("operation");
            if (source == null || operation == null || operation.isBlank()) continue;
            MutableStatistic value = statistics.get(new StatisticKey(source, operation));
            if (value == null) continue;
            double total = summary.totalAmount();
            long count = summary.count();
            if (cpuTime) {
                value.cpuTotal += total;
                value.cpuCount += count;
            } else {
                value.allocatedTotal += total;
                value.allocatedCount += count;
            }
        }
    }

    @GetMapping("/incidents")
    public List<TraceHistory.TraceRecord> incidents(@RequestParam String kind,
                                                     @RequestParam(defaultValue = "") String source,
                                                     @RequestParam(defaultValue = "100") int limit) {
        requireAccess();
        if (!"slow".equalsIgnoreCase(kind) && !"error".equalsIgnoreCase(kind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kind must be slow or error");
        }
        String sourceFilter = source.trim();
        return allTraces(500).stream()
                .filter(trace -> "slow".equalsIgnoreCase(kind) ? trace.slow() : trace.error())
                .filter(trace -> sourceFilter.isEmpty() || sourceFilter.equalsIgnoreCase(trace.sourceType()))
                .limit(Math.max(1, Math.min(limit, 500))).toList();
    }

    private List<TraceHistory.TraceRecord> allTraces(int limit) {
        Map<String, TraceHistory.TraceRecord> merged = new LinkedHashMap<>();
        if (traceArchive != null) traceArchive.recent(limit).forEach(trace -> merged.put(trace.traceId(), trace));
        traceHistory.recent(limit).forEach(trace -> merged.putIfAbsent(trace.traceId(), trace));
        return merged.values().stream().sorted(Comparator.comparing(TraceHistory.TraceRecord::finishedAt).reversed())
                .limit(Math.max(1, Math.min(limit, 500))).toList();
    }

    @GetMapping("/traces/{traceId}")
    public TraceHistory.TraceRecord trace(@PathVariable String traceId) {
        requireAccess();
        TraceHistory.TraceRecord trace = traceHistory.find(traceId);
        return trace != null || traceArchive == null ? trace : traceArchive.find(traceId);
    }

    @GetMapping("/operations")
    public List<OperationRecord> operations(@RequestParam(defaultValue = "") String kind,
                                            @RequestParam(defaultValue = "200") int limit) {
        requireAccess();
        return traceHistory.recentOperations(limit, kind);
    }

    @GetMapping("/slow")
    public List<OperationRecord> slowOperations(@RequestParam(defaultValue = "100") int limit) {
        requireAccess();
        return incidentHistory == null ? traceHistory.recentOperations(limit, "slow")
                : incidentHistory.recent(limit, "slow");
    }

    @GetMapping("/errors")
    public List<OperationRecord> errorOperations(@RequestParam(defaultValue = "100") int limit) {
        requireAccess();
        return incidentHistory == null ? traceHistory.recentOperations(limit, "error")
                : incidentHistory.recent(limit, "error");
    }

    @GetMapping("/metrics/series")
    public List<MetricHistoryStore.MetricSeriesDescriptor> metricSeries() {
        requireAccess();
        return metricHistoryStore == null ? List.of() : metricHistoryStore.seriesCatalog();
    }

    @GetMapping("/metrics/history")
    public List<MetricHistoryPoint> metricHistory(@RequestParam String name,
                                                   @RequestParam(defaultValue = "") String tags,
                                                   @RequestParam(defaultValue = "1000") int limit) {
        requireAccess();
        Map<String, String> tagFilter = Map.of();
        try {
            if (!tags.isBlank()) tagFilter = objectMapper.readValue(tags, new TypeReference<>() { });
        } catch (Exception ignored) {
            return List.of();
        }
        return metricHistoryStore == null ? List.of()
                : metricHistoryStore.metricHistory(name, tagFilter, Math.max(1, Math.min(limit, 50000)));
    }

    @GetMapping("/metrics/storage")
    public MetricHistoryStore.StorageStatus metricStorage() {
        requireAccess();
        return metricHistoryStore == null ? null : metricHistoryStore.status();
    }

    private void requireAccess() {
        if (!accessPolicy.canView()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private static MetricRecord metricRecord(Meter meter) {
        Map<String, String> tags = new LinkedHashMap<>();
        meter.getId().getTags().forEach(tag -> tags.put(tag.getKey(), tag.getValue()));
        Map<String, Double> values = new LinkedHashMap<>();
        for (Measurement measurement : meter.measure()) {
            double value = measurement.getValue();
            if (Double.isFinite(value)) values.put(measurement.getStatistic().name(), value);
        }
        return new MetricRecord(meter.getId().getName(), meter.getId().getType().name(), tags, values);
    }

    public record MetricRecord(String name, String type, Map<String, String> tags, Map<String, Double> values) { }

    public record RequestStatistic(String source, String operation, long count, long errorCount,
                                   double errorRate, double totalDurationMs, double meanDurationMs,
                                   double maxDurationMs, double meanCpuTimeMs, double meanAllocatedBytes) { }

    private record StatisticKey(String source, String operation) { }

    private static final class MutableStatistic {
        private long count;
        private long errorCount;
        private double totalDurationMs;
        private double maxDurationMs;
        private double cpuTotal;
        private long cpuCount;
        private double allocatedTotal;
        private long allocatedCount;

        private RequestStatistic toRecord(StatisticKey key) {
            return new RequestStatistic(key.source(), key.operation(), count, errorCount,
                    count == 0 ? 0 : (double) errorCount / count * 100, totalDurationMs,
                    count == 0 ? 0 : totalDurationMs / count, maxDurationMs,
                    cpuCount == 0 ? 0 : cpuTotal / cpuCount,
                    allocatedCount == 0 ? 0 : allocatedTotal / allocatedCount);
        }
    }
}
