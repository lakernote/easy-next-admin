package com.laker.observability.trace;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/** Keeps a small, recent in-memory view of completed traces for the bundled dashboard. */
public class TraceHistory {
    private final ConcurrentLinkedDeque<TraceRecord> records = new ConcurrentLinkedDeque<>();
    private final AtomicLong nextId = new AtomicLong();
    private final ConcurrentLinkedDeque<OperationRecord> operations = new ConcurrentLinkedDeque<>();
    private final AtomicLong nextOperationId = new AtomicLong();
    private final int capacity;
    private final int operationCapacity;

    public TraceHistory(int capacity) {
        this(capacity, Math.max(100, capacity * 8));
    }

    public TraceHistory(int capacity, int operationCapacity) {
        this.capacity = Math.max(1, capacity);
        this.operationCapacity = Math.max(1, operationCapacity);
    }

    public void add(String traceId, String name, String method, int status, long durationMs,
                    boolean slow, boolean error, String tree) {
        add(traceId, name, method, "", status, durationMs, slow, error, tree);
    }

    public void add(String traceId, String name, String method, String sourceType, int status, long durationMs,
                    boolean slow, boolean error, String tree) {
        records.addFirst(new TraceRecord(nextId.incrementAndGet(), traceId, name, method, status,
                Math.max(0, durationMs), slow, error, tree, Instant.now(), -1, -1, -1, List.of(), sourceType));
        while (records.size() > capacity) records.pollLast();
    }

    public List<TraceRecord> recent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, capacity));
        List<TraceRecord> result = new ArrayList<>(safeLimit);
        for (TraceRecord record : records) {
            if (result.size() == safeLimit) break;
            OperationRecord root = operationsFor(record.traceId(), 500).stream()
                    .filter(TraceHistory::isRootOperation).findFirst().orElse(null);
            result.add(root == null ? record : record.withDetails(root, List.of()));
        }
        return List.copyOf(result);
    }

    public List<TraceRecord> recentIncidents(int limit, String kind) {
        String filter = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
        return recent(capacity).stream()
                .filter(trace -> "slow".equals(filter) ? trace.slow()
                        : ("error".equals(filter) || "errors".equals(filter)) && trace.error())
                .limit(Math.max(1, Math.min(limit, capacity))).toList();
    }

    public TraceRecord find(String traceId) {
        if (traceId == null || traceId.isBlank()) return null;
        TraceRecord record = records.stream().filter(item -> traceId.equals(item.traceId())).findFirst().orElse(null);
        return record == null ? null : withOperations(record);
    }

    public void addOperation(OperationRecord operation) {
        if (operation == null) return;
        operations.addFirst(operation);
        while (operations.size() > operationCapacity) operations.pollLast();
    }

    public List<OperationRecord> recentOperations(int limit, String kind) {
        int safeLimit = Math.max(1, Math.min(limit, operationCapacity));
        String filter = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
        List<OperationRecord> result = new ArrayList<>(Math.min(safeLimit, 100));
        for (OperationRecord operation : operations) {
            if ("slow".equals(filter) && !operation.slow()) continue;
            if (("error".equals(filter) || "errors".equals(filter)) && !operation.error()) continue;
            if (result.size() == safeLimit) break;
            result.add(operation);
        }
        return List.copyOf(result);
    }

    public List<OperationRecord> operationsFor(String traceId, int limit) {
        if (traceId == null || traceId.isBlank()) return List.of();
        TraceRecord trace = records.stream().filter(item -> traceId.equals(item.traceId())).findFirst().orElse(null);
        if (trace == null) return List.of();
        Instant start = trace.finishedAt().minusMillis(trace.durationMs()).minusMillis(1);
        Instant end = trace.finishedAt().plusMillis(1);
        int safeLimit = Math.max(1, Math.min(limit, 1_000));
        List<OperationRecord> result = new ArrayList<>();
        for (OperationRecord operation : operations) {
            if (!traceId.equals(operation.traceId()) || operation.finishedAt().isBefore(start)
                    || operation.finishedAt().isAfter(end)) continue;
            if (result.size() == safeLimit) break;
            result.add(operation);
        }
        return List.copyOf(result);
    }

    private TraceRecord withOperations(TraceRecord record) {
        List<OperationRecord> related = operationsFor(record.traceId(), 500);
        OperationRecord root = related.stream().filter(TraceHistory::isRootOperation).findFirst().orElse(null);
        return record.withDetails(root, related);
    }

    private static boolean isRootOperation(OperationRecord operation) {
        return "HTTP".equals(operation.type()) || "REDIS".equals(operation.type())
                || "KAFKA".equals(operation.type()) || "KAFKA_CONSUMER".equals(operation.type())
                || "KAFKA_PRODUCER".equals(operation.type()) || "JOB".equals(operation.type())
                || "TRANSACTION".equals(operation.type()) || "REMOTE".equals(operation.type())
                || "DATABASE".equals(operation.type()) || "SQL".equals(operation.type());
    }

    /** One completed request or manually recorded trace. */
    public record TraceRecord(long id, String traceId, String name, String method, int status,
                              long durationMs, boolean slow, boolean error, String tree, Instant finishedAt,
                              long threadId, long cpuTimeMs, long allocatedBytes,
                              List<OperationRecord> operations, String sourceType) {
        public TraceRecord {
            operations = operations == null ? List.of() : List.copyOf(operations);
            sourceType = sourceType == null || sourceType.isBlank()
                    ? inferSourceType(operations) : sourceType;
        }

        public TraceRecord(long id, String traceId, String name, String method, int status,
                           long durationMs, boolean slow, boolean error, String tree, Instant finishedAt) {
            this(id, traceId, name, method, status, durationMs, slow, error, tree, finishedAt,
                    -1, -1, -1, List.of(), "");
        }

        public TraceRecord(long id, String traceId, String name, String method, int status,
                           long durationMs, boolean slow, boolean error, String tree, Instant finishedAt,
                           long threadId, long cpuTimeMs, long allocatedBytes, List<OperationRecord> operations) {
            this(id, traceId, name, method, status, durationMs, slow, error, tree, finishedAt,
                    threadId, cpuTimeMs, allocatedBytes, operations, "");
        }

        private TraceRecord withDetails(OperationRecord root, List<OperationRecord> related) {
            if (root == null) return new TraceRecord(id, traceId, name, method, status, durationMs, slow, error,
                    tree, finishedAt, threadId, cpuTimeMs, allocatedBytes, related, sourceType);
            return new TraceRecord(id, traceId, name, method, status, durationMs, slow, error, tree, finishedAt,
                    root.threadId(), root.cpuTimeMs(), root.allocatedBytes(), related,
                    sourceType.isBlank() ? root.type() : sourceType);
        }

        private static String inferSourceType(List<OperationRecord> operations) {
            return operations.stream().filter(TraceHistory::isRootOperation)
                    .map(OperationRecord::type).findFirst().orElse("");
        }
    }
}
