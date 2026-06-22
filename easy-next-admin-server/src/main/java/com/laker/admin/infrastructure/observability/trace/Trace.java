package com.laker.admin.infrastructure.observability.trace;

import java.util.ArrayDeque;
import java.util.Deque;

final class Trace {
    private final Span root;
    private final Deque<Span> spanStack = new ArrayDeque<>();
    private final Deque<Boolean> callStack = new ArrayDeque<>();
    private final int maxDepth;
    private final long minNodeCostMs;

    Trace(String rootName, SpanType rootType) {
        this(rootName, rootType, "", 8, 0);
    }

    Trace(String rootName, SpanType rootType, String rootTag, int maxDepth, long minNodeCostMs) {
        this.root = new Span(rootName, rootType, rootTag, null);
        this.spanStack.push(root);
        this.maxDepth = Math.max(1, maxDepth);
        this.minNodeCostMs = Math.max(0, minNodeCostMs);
    }

    void addSpan(String name, SpanType type, String tag) {
        int nextDepth = callStack.size() + 2;
        Span parent = spanStack.peek();
        if (parent == null || nextDepth > maxDepth) {
            callStack.push(false);
            return;
        }
        Span child = new Span(name, type, tag, parent);
        parent.addChild(child);
        spanStack.push(child);
        callStack.push(true);
    }

    void stopSpan() {
        if (callStack.isEmpty()) {
            return;
        }
        boolean recorded = callStack.pop();
        if (!recorded || spanStack.size() <= 1) {
            return;
        }
        Span span = spanStack.pop();
        span.stop();
        if (span.costMs() < minNodeCostMs) {
            span.removeFromParent();
        }
    }

    long stopRoot() {
        while (!callStack.isEmpty()) {
            stopSpan();
        }
        root.stop();
        spanStack.clear();
        return root.costMs();
    }

    String renderTree() {
        StringBuilder builder = new StringBuilder();
        root.appendTree(builder, "", true);
        return builder.toString();
    }
}
