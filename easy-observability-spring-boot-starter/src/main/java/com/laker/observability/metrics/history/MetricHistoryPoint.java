package com.laker.observability.metrics.history;

import java.util.Map;

/** A timestamped RRD4J sample returned by metric history queries. */
public record MetricHistoryPoint(String capturedAt, Map<String, String> tags, Map<String, Double> measurements) {
    public MetricHistoryPoint {
        tags = tags == null ? Map.of() : Map.copyOf(tags);
        measurements = measurements == null ? Map.of() : Map.copyOf(measurements);
    }
}
