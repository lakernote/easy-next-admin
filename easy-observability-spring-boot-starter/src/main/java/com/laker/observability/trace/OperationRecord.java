package com.laker.observability.trace;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** A completed HTTP, database, Redis, Kafka, transaction, or remote operation. */
public record OperationRecord(
        String type,
        String traceId,
        String name,
        String threadName,
        long threadId,
        String outcome,
        long durationMs,
        long cpuTimeMs,
        long allocatedBytes,
        long blockedCount,
        long blockedTimeMs,
        long waitedCount,
        long waitedTimeMs,
        String detail,
        String lockInfo,
        String stackTrace,
        boolean slow,
        boolean error,
        List<String> reasons,
        String errorType,
        String errorMessage,
        Map<String, OperationSummary> childSummaries,
        Instant finishedAt,
        String spanId,
        String parentSpanId) {

    public OperationRecord {
        type = type == null || type.isBlank() ? "OTHER" : type;
        traceId = safe(traceId, 128);
        name = safe(name, 300);
        threadName = safe(threadName, 200);
        outcome = "success".equalsIgnoreCase(outcome) ? "success" : "failure";
        durationMs = Math.max(0, durationMs);
        detail = safe(detail, 4_000);
        lockInfo = safe(lockInfo, 8_000);
        stackTrace = safe(stackTrace, 8_000);
        reasons = reasons == null ? List.of() : reasons.stream().filter(value -> value != null && !value.isBlank())
                .distinct().limit(16).toList();
        errorType = safe(errorType, 300);
        errorMessage = safe(errorMessage, 2_000);
        childSummaries = childSummaries == null ? Map.of() : Map.copyOf(childSummaries);
        finishedAt = finishedAt == null ? Instant.now() : finishedAt;
        spanId = safe(spanId, 128);
        parentSpanId = safe(parentSpanId, 128);
    }

    /** Keeps source compatibility for integrations that do not yet provide span relationships. */
    public OperationRecord(String type, String traceId, String name, String threadName, long threadId,
                           String outcome, long durationMs, long cpuTimeMs, long allocatedBytes,
                           long blockedCount, long blockedTimeMs, long waitedCount, long waitedTimeMs,
                           String detail, String lockInfo, String stackTrace, boolean slow, boolean error,
                           List<String> reasons, String errorType, String errorMessage,
                           Map<String, OperationSummary> childSummaries, Instant finishedAt) {
        this(type, traceId, name, threadName, threadId, outcome, durationMs, cpuTimeMs, allocatedBytes,
                blockedCount, blockedTimeMs, waitedCount, waitedTimeMs, detail, lockInfo, stackTrace,
                slow, error, reasons, errorType, errorMessage, childSummaries, finishedAt, "", "");
    }

    private static String safe(String value, int limit) {
        if (value == null) return "";
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    public record OperationSummary(long count, long errorCount, long totalDurationMs, long maxDurationMs) { }
}
