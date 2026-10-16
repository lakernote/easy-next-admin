package com.laker.observability.integration.job;

import com.laker.observability.config.EasyObservabilityProperties;
import com.laker.observability.integration.observation.ObservationTraceHandler;
import com.laker.observability.metrics.OperationRecorder;
import com.laker.observability.trace.TraceHistory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobObservationTest {
    @Test
    void recordsCustomScheduledWorkThroughTheObservationExtension() {
        ObservationRegistry registry = ObservationRegistry.create();
        EasyObservabilityProperties properties = new EasyObservabilityProperties();
        TraceHistory history = new TraceHistory(10);
        OperationRecorder recorder = new OperationRecorder(new SimpleMeterRegistry(), history, properties);
        registry.observationConfig().observationHandler(new ObservationTraceHandler(recorder, history, properties));

        new JobObservation(registry).observe("nightly-reconciliation", () -> { });

        assertThat(history.recentOperations(10, null)).singleElement().satisfies(operation -> {
            assertThat(operation.type()).isEqualTo("JOB");
            assertThat(operation.name()).isEqualTo("nightly-reconciliation");
        });
    }
}
