package com.laker.admin.infrastructure.observability.trace;

import java.util.function.Supplier;

public final class TraceCodeBlock {
    private TraceCodeBlock() {
    }

    public static <T> T trace(String spanName, Supplier<T> supplier) {
        return trace(spanName, SpanType.CodeBlock, supplier);
    }

    public static <T> T trace(String spanName, SpanType spanType, Supplier<T> supplier) {
        return trace(spanName, spanType, "", supplier);
    }

    public static <T> T trace(String spanName, SpanType spanType, String tag, Supplier<T> supplier) {
        TraceContext.addSpan(spanName, spanType, tag);
        try {
            return supplier.get();
        } finally {
            TraceContext.stopSpan();
        }
    }

    public static void trace(String spanName, Runnable runnable) {
        trace(spanName, SpanType.CodeBlock, runnable);
    }

    public static void trace(String spanName, SpanType spanType, Runnable runnable) {
        trace(spanName, spanType, "", runnable);
    }

    public static void trace(String spanName, SpanType spanType, String tag, Runnable runnable) {
        TraceContext.addSpan(spanName, spanType, tag);
        try {
            runnable.run();
        } finally {
            TraceContext.stopSpan();
        }
    }
}
