package com.laker.admin.infrastructure.observability.trace;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class TraceContext {
    private static final ThreadLocal<Trace> LOCAL_TRACE = new ThreadLocal<>();

    private TraceContext() {
    }

    public static boolean active() {
        return LOCAL_TRACE.get() != null;
    }

    public static void startRoot(String name, SpanType type) {
        startRoot(name, type, "", 8, 0);
    }

    public static void startRoot(String name, SpanType type, String tag, int maxDepth, long minNodeCostMs) {
        if (LOCAL_TRACE.get() == null) {
            LOCAL_TRACE.set(new Trace(name, type, tag, maxDepth, minNodeCostMs));
        } else {
            addSpan(name, type, tag);
        }
    }

    public static void addSpan(String name, SpanType type) {
        addSpan(name, type, "");
    }

    public static void addSpan(String name, SpanType type, String tag) {
        Trace trace = LOCAL_TRACE.get();
        if (trace != null) {
            trace.addSpan(name, type, tag);
        }
    }

    public static void stopSpan() {
        Trace trace = LOCAL_TRACE.get();
        if (trace != null) {
            trace.stopSpan();
        }
    }

    public static void stopRoot(long slowThresholdMs, String description) {
        stopRoot(slowThresholdMs, description, null);
    }

    public static void stopRoot(long slowThresholdMs, String description, Throwable throwable) {
        Trace trace = LOCAL_TRACE.get();
        if (trace == null) {
            return;
        }
        try {
            long costMs = trace.stopRoot();
            if (throwable != null) {
                log.error("{}, exception={}, message={}, threshold={}ms\n{}",
                        description,
                        throwable.getClass().getSimpleName(),
                        throwable.getMessage(),
                        slowThresholdMs,
                        trace.renderTree(),
                        throwable);
            } else if (slowThresholdMs > 0 && costMs >= slowThresholdMs) {
                log.warn("{}, threshold={}ms\n{}",
                        description,
                        slowThresholdMs,
                        trace.renderTree());
            }
        } finally {
            clear();
        }
    }

    public static void clear() {
        LOCAL_TRACE.remove();
    }
}
