package com.laker.observability.trace;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class TraceTree {
    static final int DEFAULT_MAX_NODES = 2_048;
    /**
     * Keep one rendered tree within the same text budget used by the local incident store. The node budget protects
     * object retention; this separate character budget protects rendering when a span name or tag is unexpectedly
     * large. The renderer stops as soon as the budget is exhausted instead of creating a larger temporary String.
     */
    static final int MAX_RENDER_CHARS = 8_000;
    private static final String TRUNCATED_REASON = "TRACE_TRUNCATED";
    private static final String OUTPUT_TRUNCATED_MARKER = System.lineSeparator()
            + "[Trace] TRACE_OUTPUT_TRUNCATED maxChars=" + MAX_RENDER_CHARS;
    private final Span root;
    private final int maxDepth;
    private final int maxNodes;
    private final long minNodeCostMs;
    /** Normal traces are discarded; allocate incident metadata only after a slow/error/truncation signal. */
    private Set<String> incidentReasons;
    private boolean slow;
    private boolean error;
    private String errorType = "";
    private String errorMessage = "";
    private String errorStackTrace = "";
    private int recordedNodes = 1;
    private long omittedSpans;
    /** Number of addSpan calls that have not yet been paired with stopSpan. */
    private int callDepth;
    /** Deepest recorded span. Skipped depth/node-budget frames leave this unchanged. */
    private Span currentSpan;
    private int currentSpanDepth = 1;

    TraceTree(String rootName, SpanKind rootKind) {
        this(rootName, rootKind, "", 8, 0, DEFAULT_MAX_NODES);
    }

    TraceTree(String rootName, SpanKind rootKind, String rootTag, int maxDepth, long minNodeCostMs) {
        this(rootName, rootKind, rootTag, maxDepth, minNodeCostMs, DEFAULT_MAX_NODES);
    }

    TraceTree(String rootName, SpanKind rootKind, String rootTag, int maxDepth, long minNodeCostMs, int maxNodes) {
        this.root = new Span(rootName, rootKind, rootTag, null);
        this.currentSpan = root;
        this.maxDepth = Math.max(1, maxDepth);
        this.minNodeCostMs = Math.max(0, minNodeCostMs);
        this.maxNodes = Math.max(1, maxNodes);
    }

    void addSpan(String name, SpanKind kind, String tag) {
        int nextDepth = callDepth + 2;
        callDepth++;
        Span parent = currentSpan;
        if (parent == null || nextDepth > maxDepth) {
            if (nextDepth > maxDepth) recordOmittedSpan();
            return;
        }
        if (recordedNodes >= maxNodes) {
            recordOmittedSpan();
            return;
        }
        Span child = new Span(name, kind, tag, parent);
        parent.addChild(child);
        recordedNodes++;
        currentSpan = child;
        currentSpanDepth = nextDepth;
    }

    Span addDetachedSpan(String name, SpanKind kind, String tag) {
        if (recordedNodes >= maxNodes) {
            recordOmittedSpan();
            return null;
        }
        Span span = new Span(name, kind, tag, root);
        root.addChild(span);
        recordedNodes++;
        return span;
    }

    void stopSpan() {
        if (callDepth <= 0) {
            return;
        }
        int stoppingDepth = callDepth + 1;
        callDepth--;
        if (currentSpan == null || currentSpan == root || currentSpanDepth != stoppingDepth) {
            return;
        }
        Span span = currentSpan;
        currentSpan = span.parent();
        currentSpanDepth--;
        span.stop();
        if (span.costMs() < minNodeCostMs) {
            recordedNodes = Math.max(1, recordedNodes - span.subtreeSize());
            span.removeFromParent();
        }
    }

    void beginCurrentSpanBoundary() {
        if (currentSpan != null) currentSpan.beginBusinessBoundary();
    }

    long stopRoot() {
        while (callDepth > 0) {
            stopSpan();
        }
        root.stop();
        currentSpan = null;
        currentSpanDepth = 0;
        return root.costMs();
    }

    void markSlow(String reason) {
        slow = true;
        addReason(reason);
    }

    void markError(String reason, Throwable failure) {
        error = true;
        addReason(reason);
        if (failure == null || !errorType.isBlank()) return;
        applyFailure(BoundedThrowableFormatter.capture(failure));
    }

    void markError(String reason, BoundedThrowableFormatter.FailureEvidence failure) {
        error = true;
        addReason(reason);
        if (failure == null || !failure.present() || !errorType.isBlank()) return;
        applyFailure(failure);
    }

    private void applyFailure(BoundedThrowableFormatter.FailureEvidence failure) {
        errorType = failure.type();
        errorMessage = failure.message();
        errorStackTrace = failure.stackTrace();
    }

    boolean slow() {
        return slow;
    }

    boolean error() {
        return error;
    }

    List<String> incidentReasons() {
        return incidentReasons == null ? List.of() : List.copyOf(incidentReasons);
    }

    String errorType() {
        return errorType;
    }

    String errorMessage() {
        return errorMessage;
    }

    String errorStackTrace() {
        return errorStackTrace;
    }

    private void addReason(String reason) {
        if (reason == null || reason.isBlank()) return;
        if (incidentReasons == null) incidentReasons = new LinkedHashSet<>();
        incidentReasons.add(reason);
    }

    private void recordOmittedSpan() {
        if (omittedSpans < Long.MAX_VALUE) omittedSpans++;
        addReason(TRUNCATED_REASON);
    }

    String renderTree() {
        RenderBuffer builder = new RenderBuffer(MAX_RENDER_CHARS, OUTPUT_TRUNCATED_MARKER);
        root.appendTree(builder, "", true);
        if (omittedSpans > 0 && !builder.truncated()) {
            builder.append("[Trace] ").append(TRUNCATED_REASON)
                    .append(" omitted=").append(omittedSpans)
                    .append(" maxNodes=").append(maxNodes)
                    .append(System.lineSeparator());
        }
        return builder.value();
    }

    /** Bounded append target shared with {@link Span}; once truncated, all later tree traversal stops. */
    static final class RenderBuffer {
        private final int maxChars;
        private final String marker;
        private final StringBuilder value;
        private boolean truncated;

        private RenderBuffer(int maxChars, String marker) {
            this.maxChars = Math.max(0, maxChars);
            this.marker = marker == null ? "" : marker;
            this.value = new StringBuilder(Math.min(this.maxChars, 512));
        }

        RenderBuffer append(String text) {
            if (truncated || text == null || text.isEmpty()) return this;
            int remaining = maxChars - value.length();
            if (text.length() <= remaining) {
                value.append(text);
                return this;
            }
            truncateWithMarker(text);
            return this;
        }

        RenderBuffer append(char character) {
            if (truncated) return this;
            if (value.length() < maxChars) {
                value.append(character);
            } else {
                truncateWithMarker("");
            }
            return this;
        }

        RenderBuffer append(long number) {
            return append(Long.toString(number));
        }

        boolean truncated() {
            return truncated;
        }

        String value() {
            return value.toString();
        }

        private void truncateWithMarker(String overflow) {
            int markerLength = Math.min(marker.length(), maxChars);
            int contentLimit = maxChars - markerLength;
            if (value.length() > contentLimit) value.setLength(contentLimit);
            int copyLength = Math.min(overflow.length(), contentLimit - value.length());
            if (copyLength > 0) value.append(overflow, 0, copyLength);
            if (markerLength > 0) {
                value.append(marker, marker.length() - markerLength, marker.length());
            }
            truncated = true;
        }
    }
}
