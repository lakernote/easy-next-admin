package com.laker.observability.metrics;

import com.laker.observability.config.EasyObservabilityProperties;
import com.laker.observability.trace.OperationRecord;
import com.laker.observability.trace.TraceHistory;
import com.laker.observability.trace.IncidentHistory;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** Records bounded operation metrics and recent details for the bundled monitoring page. */
public final class OperationRecorder {
    private static final List<String> TYPES = List.of(
            "HTTP", "SQL", "DATABASE", "REDIS", "TRANSACTION", "REMOTE", "KAFKA", "JOB", "OTHER");
    private static final List<String> OUTCOMES = List.of("success", "failure");

    private final MeterRegistry meterRegistry;
    private final TraceHistory traceHistory;
    private final IncidentHistory incidentHistory;
    private final EasyObservabilityProperties properties;

    public OperationRecorder(MeterRegistry meterRegistry, TraceHistory traceHistory,
                             EasyObservabilityProperties properties) {
        this(meterRegistry, traceHistory, null, properties);
    }

    public OperationRecorder(MeterRegistry meterRegistry, TraceHistory traceHistory,
                             IncidentHistory incidentHistory, EasyObservabilityProperties properties) {
        this.meterRegistry = meterRegistry;
        this.traceHistory = traceHistory;
        this.incidentHistory = incidentHistory;
        this.properties = properties;
    }

    /** Never propagates monitoring failures into the observed business operation. */
    public void record(OperationRecord operation) {
        if (operation == null) return;
        if (properties.getMetrics().isEnabled()) safely(() -> recordMetrics(operation));
        if (properties.getTrace().isEnabled()) safely(() -> traceHistory.addOperation(operation));
        if (incidentHistory != null) safely(() -> incidentHistory.record(operation));
    }

    private static void safely(Runnable action) {
        try { action.run(); }
        catch (Throwable ignored) {
            // One broken backend must not suppress the other monitoring outputs or affect application work.
        }
    }

    private void recordMetrics(OperationRecord operation) {
        String type = operation.type().startsWith("KAFKA_") ? "KAFKA"
                : TYPES.contains(operation.type()) ? operation.type() : "OTHER";
        String outcome = OUTCOMES.contains(operation.outcome()) ? operation.outcome() : "failure";
        Timer.builder("easy.operations.duration")
                .description("Observed operation duration")
                .tag("type", type).tag("outcome", outcome).register(meterRegistry)
                .record(operation.durationMs(), TimeUnit.MILLISECONDS);
        record("easy.operations.cpu.time", "milliseconds", type, outcome, operation.cpuTimeMs());
        record("easy.operations.allocated", "bytes", type, outcome, operation.allocatedBytes());
        record("easy.operations.blocked.count", "events", type, outcome, operation.blockedCount());
        record("easy.operations.blocked.time", "milliseconds", type, outcome, operation.blockedTimeMs());
        record("easy.operations.waited.count", "events", type, outcome, operation.waitedCount());
        record("easy.operations.waited.time", "milliseconds", type, outcome, operation.waitedTimeMs());
    }

    private void record(String name, String unit, String type, String outcome, long value) {
        if (value < 0) return;
        DistributionSummary.builder(name).baseUnit(unit).tag("type", type).tag("outcome", outcome)
                .register(meterRegistry).record(value);
    }
}
