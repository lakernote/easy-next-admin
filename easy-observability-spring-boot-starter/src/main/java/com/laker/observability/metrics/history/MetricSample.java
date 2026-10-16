package com.laker.observability.metrics.history;

import java.util.Map;

/** One bounded snapshot of a Micrometer meter. */
public record MetricSample(String name, Map<String, String> tags, Map<String, Double> measurements) {
    public MetricSample {
        name = name == null ? "" : name;
        tags = tags == null ? Map.of() : Map.copyOf(tags);
        measurements = measurements == null ? Map.of() : Map.copyOf(measurements);
    }
}
