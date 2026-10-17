package com.laker.observability.trace;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Keeps a small, recent in-memory view of completed traces for the bundled dashboard. */
public class TraceHistory {
    private final ConcurrentLinkedDeque<TraceRecord> records = new ConcurrentLinkedDeque<>();
    private final AtomicInteger recordCount = new AtomicInteger();
    private final AtomicLong nextId = new AtomicLong();
    private final ConcurrentLinkedDeque<OperationRecord> operations = new ConcurrentLinkedDeque<>();
    private final ConcurrentMap<String, ConcurrentLinkedDeque<OperationRecord>> operationsByTraceId =
            new ConcurrentHashMap<>();
    private final AtomicInteger operationCount = new AtomicInteger();
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
        if (!isEntrySource(sourceType)) return;
        records.addFirst(new TraceRecord(nextId.incrementAndGet(), traceId, name, method, status,
                Math.max(0, durationMs), slow, error, tree, Instant.now(), -1, -1, -1, List.of(), sourceType));
        recordCount.incrementAndGet();
        while (recordCount.get() > capacity) {
            if (records.pollLast() == null) break;
            recordCount.decrementAndGet();
        }
    }

    public List<TraceRecord> recent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, capacity));
        List<TraceRecord> result = new ArrayList<>(safeLimit);
        for (TraceRecord record : records) {
            if (!isEntrySource(record.sourceType())) continue;
            if (result.size() == safeLimit) break;
            OperationRecord root = firstRootOperation(record.traceId());
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
        TraceRecord record = records.stream().filter(item -> traceId.equals(item.traceId())
                && isEntrySource(item.sourceType())).findFirst().orElse(null);
        return record == null ? null : withOperations(record);
    }

    public void addOperation(OperationRecord operation) {
        if (operation == null) return;
        operations.addFirst(operation);
        operationCount.incrementAndGet();
        if (!operation.traceId().isBlank()) {
            operationsByTraceId.compute(operation.traceId(), (traceId, related) -> {
                ConcurrentLinkedDeque<OperationRecord> indexed = related == null
                        ? new ConcurrentLinkedDeque<>() : related;
                indexed.addFirst(operation);
                return indexed;
            });
        }
        while (operationCount.get() > operationCapacity) {
            OperationRecord expired = operations.pollLast();
            if (expired == null) break;
            operationCount.decrementAndGet();
            removeIndexedOperation(expired);
        }
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
        TraceRecord trace = records.stream().filter(item -> traceId.equals(item.traceId())
                && isEntrySource(item.sourceType())).findFirst().orElse(null);
        if (trace == null) return List.of();
        int safeLimit = Math.max(1, Math.min(limit, 1_000));
        ConcurrentLinkedDeque<OperationRecord> related = operationsByTraceId.get(traceId);
        if (related == null) return List.of();
        List<OperationRecord> result = new ArrayList<>();
        for (OperationRecord operation : related) {
            if (result.size() == safeLimit) break;
            result.add(operation);
        }
        return List.copyOf(result);
    }

    private OperationRecord firstRootOperation(String traceId) {
        ConcurrentLinkedDeque<OperationRecord> related = operationsByTraceId.get(traceId);
        if (related == null) return null;
        return related.stream().filter(TraceHistory::isRootOperation).findFirst().orElse(null);
    }

    private void removeIndexedOperation(OperationRecord operation) {
        String traceId = operation.traceId();
        if (traceId.isBlank()) return;
        operationsByTraceId.computeIfPresent(traceId, (ignored, related) -> {
            related.remove(operation);
            return related.isEmpty() ? null : related;
        });
    }

    private TraceRecord withOperations(TraceRecord record) {
        List<OperationRecord> related = operationsFor(record.traceId(), 500);
        OperationRecord root = related.stream().filter(TraceHistory::isRootOperation).findFirst().orElse(null);
        return record.withDetails(root, related);
    }

    private static boolean isRootOperation(OperationRecord operation) {
        return isEntrySource(operation.type());
    }

    /** Trace history contains entry points; dependencies remain visible under the entry's detail. */
    public static boolean isEntrySource(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) return true; // legacy caller without a source tag
        return "HTTP".equals(sourceType) || "KAFKA_CONSUMER".equals(sourceType) || "JOB".equals(sourceType);
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
