package com.laker.observability.metrics;

import com.laker.observability.config.EasyObservabilityProperties;
import com.laker.observability.trace.OperationRecord;
import com.laker.observability.trace.TraceHistory;
import com.laker.observability.trace.IncidentHistory;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Records bounded operation metrics and recent details for the bundled monitoring page. */
public final class OperationRecorder {
    private static final Logger log = LoggerFactory.getLogger(OperationRecorder.class);
    private static final List<String> TYPES = List.of(
            "HTTP", "SQL", "DATABASE", "REDIS", "ELASTICSEARCH", "TRANSACTION", "REMOTE", "KAFKA", "JOB", "OTHER");
    private static final List<String> OUTCOMES = List.of("success", "failure");

    private final MeterRegistry meterRegistry;
    private final TraceHistory traceHistory;
    private final IncidentHistory incidentHistory;
    private final EasyObservabilityProperties properties;
    private final AtomicBoolean failureLogged = new AtomicBoolean();

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
        if (properties.getMetrics().isEnabled()) safely("记录操作指标", () -> recordMetrics(operation));
        if (properties.getTrace().isEnabled()) safely("保留 Trace 操作", () -> traceHistory.addOperation(operation));
        if (incidentHistory != null) safely("保留 slow/error 操作", () -> incidentHistory.record(operation));
    }

    private void safely(String actionName, Runnable action) {
        try { action.run(); }
        catch (RuntimeException failure) {
            if (failureLogged.compareAndSet(false, true)) {
                log.warn("Easy Observability {}失败；业务操作会继续执行", actionName, failure);
            }
        }
    }

    private void recordMetrics(OperationRecord operation) {
        String type = operation.type().startsWith("KAFKA_") ? "KAFKA"
                : TYPES.contains(operation.type()) ? operation.type() : "OTHER";
        String outcome = OUTCOMES.contains(operation.outcome()) ? operation.outcome() : "failure";
        String source = safeTag(operation.type());
        String name = safeTag(operation.detail().isBlank() ? operation.name()
                : "HTTP".equals(operation.type()) ? operation.detail() + " · " + operation.name()
                : operation.name() + " · " + operation.detail());
        Timer.builder("easy.operations.duration")
                .description("Observed operation duration")
                .tag("type", type).tag("source", source).tag("operation", name).tag("outcome", outcome)
                .register(meterRegistry)
                .record(operation.durationMs(), TimeUnit.MILLISECONDS);
        record("easy.operations.cpu.time", "milliseconds", type, source, name, outcome, operation.cpuTimeMs());
        record("easy.operations.allocated", "bytes", type, source, name, outcome, operation.allocatedBytes());
        record("easy.operations.blocked.count", "events", type, source, name, outcome, operation.blockedCount());
        record("easy.operations.blocked.time", "milliseconds", type, source, name, outcome, operation.blockedTimeMs());
        record("easy.operations.waited.count", "events", type, source, name, outcome, operation.waitedCount());
        record("easy.operations.waited.time", "milliseconds", type, source, name, outcome, operation.waitedTimeMs());
    }

    private void record(String name, String unit, String type, String source, String operation,
                        String outcome, long value) {
        if (value < 0) return;
        DistributionSummary.builder(name).baseUnit(unit).tag("type", type).tag("source", source)
                .tag("operation", operation).tag("outcome", outcome)
                .register(meterRegistry).record(value);
    }

    private static String safeTag(String value) {
        if (value == null || value.isBlank()) return "unknown";
        String clean = value.replace('\r', ' ').replace('\n', ' ').trim();
        return clean.length() <= 200 ? clean : clean.substring(0, 200);
    }
}
