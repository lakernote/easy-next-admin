package com.laker.observability.integration.observation;

import com.laker.observability.config.EasyObservabilityProperties;
import com.laker.observability.metrics.OperationRecorder;
import com.laker.observability.trace.OperationRecord;
import com.laker.observability.trace.BoundedThrowableFormatter;
import com.laker.observability.trace.SpanKind;
import com.laker.observability.trace.ThreadResourceSnapshot;
import com.laker.observability.trace.TraceContext;
import com.laker.observability.trace.TraceHistory;
import com.laker.observability.trace.TraceArchive;
import com.laker.observability.trace.TraceIdContext;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Bridges framework Micrometer observations into the bounded trace history and the custom trace tree. */
public final class ObservationTraceHandler implements ObservationHandler<Observation.Context> {
    private static final Object STATE_KEY = ObservationTraceHandler.class.getName() + ".state";

    private final OperationRecorder recorder;
    private final TraceHistory history;
    private final EasyObservabilityProperties properties;
    private final TraceArchive archive;

    public ObservationTraceHandler(OperationRecorder recorder, TraceHistory history,
                                   EasyObservabilityProperties properties) {
        this(recorder, history, null, properties);
    }

    public ObservationTraceHandler(OperationRecorder recorder, TraceHistory history,
                                   TraceArchive archive, EasyObservabilityProperties properties) {
        this.recorder = recorder;
        this.history = history;
        this.archive = archive;
        this.properties = properties;
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        String type = resolveType(context);
        if (type == null) return false;
        return switch (type) {
            case "HTTP" -> properties.getTrace().isHttpServerObservationEnabled();
            case "REDIS" -> properties.getTrace().isRedisObservationEnabled();
            case "DATABASE" -> properties.getTrace().isDatabaseObservationEnabled();
            default -> true;
        };
    }

    @Override
    public void onStart(Observation.Context context) {
        try {
            String type = resolveType(context);
            if (type == null) return;
            // Only inbound work starts a trace. Client calls, SQL, Redis and transactions are
            // dependencies; without an active entry they remain operation metrics, not fake roots.
            boolean root = isEntryType(type) && !TraceContext.active();
            boolean generatedTraceId = !TraceIdContext.isValidTraceId(TraceIdContext.currentTraceId());
            String traceId = TraceIdContext.getOrCreateTraceId();
            String name = operationName(context, type);
            long threadId = Thread.currentThread().getId();
            ThreadResourceSnapshot resources = ThreadResourceSnapshot.capture(threadId,
                    properties.getTrace().isResourceSamplingEnabled());
            TraceContext.DetachedSpan span = null;
            String spanId;
            String parentSpanId;
            if (root) {
                spanId = TraceContext.startRootSpan(name, kind(type), safeTag(context), 8, 0);
                parentSpanId = "";
            } else {
                span = TraceContext.startDetachedSpan(name, kind(type), safeTag(context));
                spanId = span.spanId();
                parentSpanId = span.parentSpanId();
            }
            context.put(STATE_KEY, new State(type, name, safeTag(context), traceId, root, generatedTraceId,
                    System.nanoTime(), threadId, Thread.currentThread().getName(), resources, span,
                    spanId, parentSpanId));
        } catch (Throwable ignored) {
            // Do not disturb the application's current trace if an instrumentation callback fails.
        }
    }

    @Override
    public void onError(Observation.Context context) {
        State state = context.get(STATE_KEY);
        if (state == null || context.getError() == null) return;
        try { TraceContext.markError(state.type + "_ERROR", context.getError()); }
        catch (Throwable ignored) { }
    }

    @Override
    public void onStop(Observation.Context context) {
        State state = context.get(STATE_KEY);
        if (state == null) return;
        try {
            long durationMs = Math.max(0, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - state.startedNanos));
            ThreadResourceSnapshot resourcesAfter = Thread.currentThread().getId() == state.threadId
                    ? ThreadResourceSnapshot.capture(state.threadId, properties.getTrace().isResourceSamplingEnabled())
                    : new ThreadResourceSnapshot(state.threadId, -1, -1, -1, -1, -1, -1);
            Throwable error = context.getError();
            int status = httpStatus(context);
            boolean failed = error != null || status >= 500;
            BoundedThrowableFormatter.FailureEvidence failure = error == null
                    ? BoundedThrowableFormatter.FailureEvidence.EMPTY
                    : BoundedThrowableFormatter.capture(error);
            if (error == null && status >= 500) {
                TraceContext.markError("HTTP_" + status,
                        new BoundedThrowableFormatter.FailureEvidence("HTTP_" + status,
                                "HTTP status " + status, ""));
            }
            long threshold = threshold(state.type);
            boolean slow = threshold > 0 && durationMs >= threshold;
            if (slow) TraceContext.markSlow("SLOW_" + state.type);
            if (state.span != null) state.span.run();
            String outcome = failed ? "failure" : "success";
            recorder.record(new OperationRecord(state.type, state.traceId, state.name, state.threadName,
                    state.threadId, outcome, durationMs, resourcesAfter.cpuTimeMsSince(state.resources),
                    resourcesAfter.allocatedBytesSince(state.resources),
                    resourcesAfter.blockedCountSince(state.resources),
                    resourcesAfter.blockedTimeMsSince(state.resources),
                    resourcesAfter.waitedCountSince(state.resources),
                    resourcesAfter.waitedTimeMsSince(state.resources), state.detail, "", failure.stackTrace(), slow,
                    failed, slow ? List.of("SLOW_" + state.type) : List.of(),
                    error == null ? (failed ? "HTTP_" + status : "") : error.getClass().getName(),
                    error == null && failed ? "HTTP status " + status : failure.message(),
                    Map.of(), Instant.now(), state.spanId, state.parentSpanId));
            if (state.root) {
                TraceContext.stopRoot(threshold, state.type + " observation", error, completion -> {
                    boolean traceSlow = slow || completion.slow();
                    boolean traceFailed = failed || completion.error();
                    history.add(state.traceId, state.name, state.detail, state.type,
                            status > 0 ? status : failed ? 500 : 200, durationMs, traceSlow, traceFailed, completion.tree());
                    if (archive != null && (traceSlow || traceFailed)) {
                        try { archive.record(history.find(state.traceId)); }
                        catch (Throwable ignored) { }
                    }
                });
            }
        } catch (Throwable ignored) {
            if (state.root) TraceContext.clear();
        } finally {
            if (state.generatedTraceId) TraceIdContext.clear();
            context.remove(STATE_KEY);
        }
    }

    private long threshold(String type) {
        if ("REDIS".equals(type)) return properties.getTrace().getRedisSlowThresholdMs();
        if ("JOB".equals(type)) return properties.getTrace().getJobSlowThresholdMs();
        return properties.getTrace().getSlowThresholdMs();
    }

    private static String resolveType(Observation.Context context) {
        String name = context.getName();
        if (name == null) return null;
        String normalized = name.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("spring.data.redis") || normalized.startsWith("lettuce")) return "REDIS";
        if (normalized.equals("http.client.requests")) return "REMOTE";
        if (normalized.equals("http.server.requests")) return "HTTP";
        if (normalized.startsWith("spring.kafka.listener")) return "KAFKA_CONSUMER";
        if (normalized.startsWith("spring.kafka.template")) return "KAFKA_PRODUCER";
        if (normalized.startsWith("spring.data.elasticsearch") || normalized.startsWith("elasticsearch")) return "ELASTICSEARCH";
        if (normalized.startsWith("spring.jdbc") || normalized.startsWith("jdbc.")) return "DATABASE";
        if (normalized.startsWith("spring.transaction")) return "TRANSACTION";
        if (normalized.equals("tasks.scheduled.execution")) return "JOB";
        return null;
    }

    private static String operationName(Observation.Context context, String type) {
        String operation = lowCardinality(context, "db.operation");
        if (operation.isBlank()) operation = lowCardinality(context, "http.method");
        if (operation.isBlank()) operation = lowCardinality(context, "http.request.method");
        if (operation.isBlank()) operation = lowCardinality(context, "messaging.operation");
        if (operation.isBlank()) operation = lowCardinality(context, "spring.kafka.listener.id");
        if (operation.isBlank()) operation = context.getContextualName();
        if (operation.isBlank()) operation = context.getName();
        if (operation == null || operation.isBlank()) operation = type;
        return safe(operation, 200);
    }

    private static String safeTag(Observation.Context context) {
        String route = lowCardinality(context, "http.route");
        if (!route.isBlank()) return safe(route, 256);
        String uri = lowCardinality(context, "uri");
        if (!uri.isBlank()) return safe(uri, 256);
        String destination = lowCardinality(context, "messaging.destination.name");
        if (!destination.isBlank()) return safe(destination, 256);
        return "";
    }

    private static String lowCardinality(Observation.Context context, String key) {
        KeyValue value = context.getLowCardinalityKeyValue(key);
        return value == null || value.getValue() == null ? "" : value.getValue();
    }

    private static int httpStatus(Observation.Context context) {
        String status = lowCardinality(context, "http.response.status_code");
        if (status.isBlank()) status = lowCardinality(context, "status");
        try { return Integer.parseInt(status); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private static boolean isEntryType(String type) {
        return "HTTP".equals(type) || "JOB".equals(type) || "KAFKA_CONSUMER".equals(type);
    }

    private static SpanKind kind(String type) {
        return switch (type) {
            case "REDIS" -> SpanKind.REDIS;
            case "DATABASE" -> SpanKind.DATABASE;
            case "ELASTICSEARCH" -> SpanKind.REMOTE;
            case "KAFKA", "KAFKA_CONSUMER", "KAFKA_PRODUCER" -> SpanKind.KAFKA;
            case "TRANSACTION" -> SpanKind.TRANSACTION;
            case "HTTP" -> SpanKind.HTTP;
            case "JOB" -> SpanKind.SCHEDULE;
            default -> SpanKind.REMOTE;
        };
    }

    private static String safe(String value, int limit) {
        if (value == null) return "";
        String clean = value.replace('\r', ' ').replace('\n', ' ');
        return clean.length() <= limit ? clean : clean.substring(0, limit);
    }

    private record State(String type, String name, String detail, String traceId, boolean root,
                         boolean generatedTraceId, long startedNanos, long threadId, String threadName,
                         ThreadResourceSnapshot resources, TraceContext.DetachedSpan span,
                         String spanId, String parentSpanId) { }
}
