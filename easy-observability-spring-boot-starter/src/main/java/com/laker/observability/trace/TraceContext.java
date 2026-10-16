package com.laker.observability.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Manages the in-process trace tree associated with the current thread. */
public final class TraceContext {
    private static final Logger log = LoggerFactory.getLogger(TraceContext.class);
    static final int MAX_INCIDENT_LOGS_PER_WINDOW = 20;
    static final long INCIDENT_LOG_WINDOW_NANOS = TimeUnit.MINUTES.toNanos(1);
    private static final int MAX_LOG_DESCRIPTION_CHARS = 256;
    private static final int MAX_LOG_REASON_CHARS = 256;
    private static final ThreadLocal<TraceTree> LOCAL_TRACE = new ThreadLocal<>();
    /** Error and slow summaries have separate budgets so a slow-call storm cannot hide every root failure. */
    private static final IncidentLogLimiter ERROR_LOG_LIMITER =
            new IncidentLogLimiter(MAX_INCIDENT_LOGS_PER_WINDOW, INCIDENT_LOG_WINDOW_NANOS);
    private static final IncidentLogLimiter SLOW_LOG_LIMITER =
            new IncidentLogLimiter(MAX_INCIDENT_LOGS_PER_WINDOW, INCIDENT_LOG_WINDOW_NANOS);

    private TraceContext() {
    }

    /** Returns whether the current thread has an open trace. */
    public static boolean active() {
        return LOCAL_TRACE.get() != null;
    }

    /** Starts a trace root if none exists, or adds a child span to the active trace. */
    public static void startRoot(String name, SpanKind kind) {
        startRoot(name, kind, "", 8, 0);
    }

    public static void startRoot(String name, SpanKind kind, String tag, int maxDepth, long minNodeCostMs) {
        if (LOCAL_TRACE.get() == null) {
            LOCAL_TRACE.set(new TraceTree(name, kind, tag, maxDepth, minNodeCostMs));
        } else {
            addSpan(name, kind, tag);
        }
    }

    /** Adds a child span to the active trace. Does nothing when no trace is active. */
    public static void addSpan(String name, SpanKind kind) {
        addSpan(name, kind, "");
    }

    public static void addSpan(String name, SpanKind kind, String tag) {
        TraceTree trace = LOCAL_TRACE.get();
        if (trace != null) {
            trace.addSpan(name, kind, tag);
        }
    }

    public static void stopSpan() {
        TraceTree trace = LOCAL_TRACE.get();
        if (trace != null) {
            trace.stopSpan();
        }
    }

    /** Rebases the root/current span after all surrounding monitoring setup and before application code runs. */
    public static void beginCurrentSpanBoundary() {
        TraceTree trace = LOCAL_TRACE.get();
        if (trace != null) trace.beginCurrentSpanBoundary();
    }

    /** Freezes root and any still-open child durations without rendering or clearing the trace. */
    public static void freezeRootBoundary() {
        TraceTree trace = LOCAL_TRACE.get();
        if (trace != null) trace.stopRoot();
    }

    /** Marks the current root as an incident even when the root itself stays below its slow threshold. */
    public static void markSlow(String reason) {
        TraceTree trace = LOCAL_TRACE.get();
        if (trace != null) trace.markSlow(reason);
    }

    /** Marks the current root as an incident and retains the first available failure evidence. */
    public static void markError(String reason, Throwable failure) {
        TraceTree trace = LOCAL_TRACE.get();
        if (trace != null) trace.markError(reason, failure);
    }

    /** Reuses failure evidence already captured after the operation's resource counters were frozen. */
    public static void markError(String reason, BoundedThrowableFormatter.FailureEvidence failure) {
        TraceTree trace = LOCAL_TRACE.get();
        if (trace != null) trace.markError(reason, failure);
    }

    /** Starts a root-level span whose lifecycle is managed by an external callback such as transaction completion. */
    public static DetachedSpan startDetachedSpan(String name, SpanKind kind, String tag) {
        TraceTree trace = LOCAL_TRACE.get();
        if (trace == null) return DetachedSpan.NOOP;
        Span span = trace.addDetachedSpan(name, kind, tag);
        return span == null ? DetachedSpan.NOOP : new DetachedSpan(span);
    }

    public static final class DetachedSpan implements Runnable {
        private static final DetachedSpan NOOP = new DetachedSpan(null);
        private final Span span;

        private DetachedSpan(Span span) {
            this.span = span;
        }

        public void beginBusinessBoundary() {
            if (span != null) span.beginBusinessBoundary();
        }

        @Override
        public void run() {
            if (span != null) span.stop();
        }
    }

    public static void stopRoot(long slowThresholdMs, String description) {
        stopRoot(slowThresholdMs, description, null);
    }

    public static void stopRoot(long slowThresholdMs, String description, Throwable throwable) {
        stopRoot(slowThresholdMs, description, throwable, (tree, cost) -> { });
    }

    public static void stopRoot(long slowThresholdMs, String description, Throwable throwable,
                                java.util.function.BiConsumer<String, Long> completion) {
        stopRoot(slowThresholdMs, description, throwable,
                result -> completion.accept(result.tree(), result.costMs()));
    }

    public static void stopRoot(long slowThresholdMs, String description, Throwable throwable,
                                Consumer<Completion> completion) {
        TraceTree trace = LOCAL_TRACE.get();
        if (trace == null) {
            return;
        }
        try {
            long costMs = trace.stopRoot();
            if (slowThresholdMs > 0 && costMs >= slowThresholdMs && !trace.slow()) {
                trace.markSlow("SLOW_ROOT");
            }
            if (throwable != null && !trace.error()) {
                trace.markError("ROOT_ERROR", throwable);
            }
            String tree = trace.slow() || trace.error() ? trace.renderTree() : "";
            completion.accept(new Completion(tree, costMs, trace.slow(), trace.error(),
                    trace.incidentReasons(), trace.errorType(), trace.errorMessage(), trace.errorStackTrace()));
            if (throwable != null) {
                logIncidentSummary(ERROR_LOG_LIMITER, true, description, costMs, slowThresholdMs,
                        throwable.getClass().getName(), trace.incidentReasons());
            } else if (slowThresholdMs > 0 && costMs >= slowThresholdMs) {
                logIncidentSummary(SLOW_LOG_LIMITER, false, description, costMs, slowThresholdMs,
                        "", trace.incidentReasons());
            }
        } finally {
            clear();
        }
    }

    /**
     * Stops a trace segment while retaining its bounded tree in object form. Async requests use this so their common
     * successful path never renders a tree String; the caller materializes it only if the completed request is slow
     * or failed.
     */
    public static void stopRootDeferred(Throwable throwable, Consumer<DeferredCompletion> completion) {
        TraceTree trace = LOCAL_TRACE.get();
        if (trace == null) return;
        try {
            long costMs = trace.stopRoot();
            if (throwable != null && !trace.error()) trace.markError("ROOT_ERROR", throwable);
            completion.accept(new DeferredCompletion(trace, costMs));
        } finally {
            clear();
        }
    }


    public static void clear() {
        LOCAL_TRACE.remove();
    }

    private static void logIncidentSummary(IncidentLogLimiter limiter, boolean error, String description,
                                           long costMs, long thresholdMs, String exceptionType,
                                           List<String> reasons) {
        long suppressed = limiter.tryAcquire(System.nanoTime());
        if (suppressed < 0) return;
        String safeDescription = oneLine(description, MAX_LOG_DESCRIPTION_CHARS);
        String safeExceptionType = oneLine(exceptionType, MAX_LOG_DESCRIPTION_CHARS);
        String safeReasons = summarizeReasons(reasons, MAX_LOG_REASON_CHARS);
        if (error) {
            // Throwable details and the bounded tree are retained by the structured incident. Repeating them through
            // Logback multiplies allocation, console and file I/O during an error storm and may leak raw messages.
            log.error("运行诊断 incident 摘要: description={}, cost={}ms, threshold={}ms, exception={}, "
                            + "reasons={}, previousWindowSuppressed={}",
                    safeDescription, costMs, thresholdMs, safeExceptionType, safeReasons, suppressed);
        } else {
            log.warn("运行诊断 incident 摘要: description={}, cost={}ms, threshold={}ms, reasons={}, "
                            + "previousWindowSuppressed={}",
                    safeDescription, costMs, thresholdMs, safeReasons, suppressed);
        }
    }

    private static String oneLine(String value, int maxChars) {
        if (value == null || value.isBlank()) return "-";
        int limit = Math.max(1, maxChars);
        StringBuilder result = new StringBuilder(Math.min(value.length(), limit));
        for (int index = 0; index < value.length() && result.length() < limit; index++) {
            char character = value.charAt(index);
            result.append(character == '\r' || character == '\n' ? ' ' : character);
        }
        return result.toString();
    }

    private static String summarizeReasons(List<String> reasons, int maxChars) {
        if (reasons == null || reasons.isEmpty()) return "-";
        int limit = Math.max(1, maxChars);
        StringBuilder result = new StringBuilder(Math.min(limit, 64));
        for (String reason : reasons) {
            if (reason == null || reason.isBlank()) continue;
            if (!result.isEmpty() && result.length() < limit) result.append(',');
            for (int index = 0; index < reason.length() && result.length() < limit; index++) {
                char character = reason.charAt(index);
                result.append(character == '\r' || character == '\n' ? ' ' : character);
            }
            if (result.length() >= limit) break;
        }
        return result.isEmpty() ? "-" : result.toString();
    }

    /**
     * Lock-free fixed-window limiter. The lower 16 bits hold the emitted count and the upper bits hold a monotonic
     * window id. Suppressed calls return without formatting a logging event.
     */
    static final class IncidentLogLimiter {
        private static final long COUNT_MASK = 0xFFFFL;
        private final int limit;
        private final long windowNanos;
        private final AtomicLong windowAndCount = new AtomicLong();
        private final AtomicLong suppressed = new AtomicLong();

        IncidentLogLimiter(int limit, long windowNanos) {
            if (limit <= 0 || limit > COUNT_MASK) throw new IllegalArgumentException("invalid limit");
            if (windowNanos <= 0) throw new IllegalArgumentException("invalid window");
            this.limit = limit;
            this.windowNanos = windowNanos;
        }

        /** Returns -1 when suppressed, otherwise the number suppressed in the previous window. */
        long tryAcquire(long nowNanos) {
            long window = Math.floorDiv(nowNanos, windowNanos);
            while (true) {
                long current = windowAndCount.get();
                long currentWindow = current >> 16;
                if (current == 0 || window > currentWindow) {
                    long next = (window << 16) | 1L;
                    if (windowAndCount.compareAndSet(current, next)) return suppressed.getAndSet(0);
                    continue;
                }
                // A caller paused across a window boundary must not move the shared monotonic window backwards.
                if (window < currentWindow || (current & COUNT_MASK) >= limit) {
                    suppressed.incrementAndGet();
                    return -1;
                }
                if (windowAndCount.compareAndSet(current, current + 1)) return 0;
            }
        }
    }

    public record Completion(String tree, long costMs, boolean slow, boolean error, List<String> reasons,
                             String errorType, String errorMessage, String stackTrace) {
        public Completion {
            tree = tree == null ? "" : tree;
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
            errorType = errorType == null ? "" : errorType;
            errorMessage = errorMessage == null ? "" : errorMessage;
            stackTrace = stackTrace == null ? "" : stackTrace;
        }
    }

    public static final class DeferredCompletion {
        private final TraceTree trace;
        private final long costMs;
        private volatile String renderedTree;

        private DeferredCompletion(TraceTree trace, long costMs) {
            this.trace = trace;
            this.costMs = costMs;
        }

        public long costMs() { return costMs; }
        public boolean slow() { return trace.slow(); }
        public boolean error() { return trace.error(); }
        public List<String> reasons() { return trace.incidentReasons(); }
        public String errorType() { return trace.errorType(); }
        public String errorMessage() { return trace.errorMessage(); }
        public String stackTrace() { return trace.errorStackTrace(); }

        public String tree() {
            String value = renderedTree;
            if (value != null) return value;
            synchronized (this) {
                if (renderedTree == null) renderedTree = trace.renderTree();
                return renderedTree;
            }
        }
    }
}
