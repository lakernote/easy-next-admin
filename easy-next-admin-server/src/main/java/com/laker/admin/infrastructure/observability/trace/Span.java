package com.laker.admin.infrastructure.observability.trace;

import java.util.ArrayList;
import java.util.List;

final class Span {
    private final String name;
    private final String tag;
    private final SpanType spanType;
    private final Span parent;
    private final long startNanos;
    private final List<Span> children = new ArrayList<>();
    private long endNanos;

    Span(String name, SpanType spanType) {
        this(name, spanType, "", null);
    }

    Span(String name, SpanType spanType, String tag, Span parent) {
        this.name = normalizeName(name);
        this.tag = normalizeTag(tag);
        this.spanType = spanType == null ? SpanType.Others : spanType;
        this.parent = parent;
        this.startNanos = System.nanoTime();
    }

    void addChild(Span child) {
        children.add(child);
    }

    void removeFromParent() {
        if (parent != null) {
            parent.children.remove(this);
        }
    }

    void stop() {
        if (endNanos == 0) {
            endNanos = System.nanoTime();
        }
    }

    long costMs() {
        long end = endNanos == 0 ? System.nanoTime() : endNanos;
        return (end - startNanos) / 1_000_000;
    }

    void appendTree(StringBuilder builder, String prefix, boolean last) {
        appendNode(builder, prefix, last);
        String childPrefix = prefix + (last ? "   " : "|  ");
        for (int i = 0; i < children.size(); ) {
            int end = aggregateEnd(i);
            boolean childLast = end >= children.size();
            if (end - i > 1) {
                appendAggregatedLeaf(builder, children.subList(i, end), childPrefix, childLast);
            } else {
                children.get(i).appendTree(builder, childPrefix, childLast);
            }
            i = end;
        }
    }

    private void appendNode(StringBuilder builder, String prefix, boolean last) {
        builder.append(prefix)
                .append(last ? "`- " : "|- ")
                .append('[').append(spanType).append("] ")
                .append(name);
        if (!tag.isBlank()) {
            builder.append(" tag=\"").append(tag).append("\"");
        }
        builder
                .append(" ")
                .append(costMs())
                .append("ms")
                .append(System.lineSeparator());
    }

    private int aggregateEnd(int start) {
        Span first = children.get(start);
        if (!first.canAggregate()) {
            return start + 1;
        }
        int end = start + 1;
        while (end < children.size() && first.sameAggregateKey(children.get(end))) {
            end++;
        }
        return end;
    }

    private static void appendAggregatedLeaf(StringBuilder builder, List<Span> spans, String prefix, boolean last) {
        Span first = spans.get(0);
        long total = 0;
        long min = Long.MAX_VALUE;
        long max = 0;
        for (Span span : spans) {
            long costMs = span.costMs();
            total += costMs;
            min = Math.min(min, costMs);
            max = Math.max(max, costMs);
        }
        builder.append(prefix)
                .append(last ? "`- " : "|- ")
                .append('[').append(first.spanType).append("] ")
                .append(first.name);
        if (!first.tag.isBlank()) {
            builder.append(" tag=\"").append(first.tag).append("\"");
        }
        builder
                .append(" [count=").append(spans.size())
                .append(" total=").append(total).append("ms")
                .append(" min=").append(min).append("ms")
                .append(" max=").append(max).append("ms]")
                .append(System.lineSeparator());
    }

    private boolean canAggregate() {
        return children.isEmpty();
    }

    private boolean sameAggregateKey(Span other) {
        return other != null
                && other.canAggregate()
                && spanType == other.spanType
                && name.equals(other.name)
                && tag.equals(other.tag);
    }

    private static String normalizeName(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private static String normalizeTag(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }
}
