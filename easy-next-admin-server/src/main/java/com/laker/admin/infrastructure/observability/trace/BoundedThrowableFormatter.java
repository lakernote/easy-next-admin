package com.laker.admin.infrastructure.observability.trace;

import com.laker.admin.infrastructure.security.masking.EasySensitiveDataMasker;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Builds incident evidence without copying an attacker-controlled or pathological throwable graph without bounds.
 */
public final class BoundedThrowableFormatter {
    static final int MAX_CAUSE_DEPTH = 8;
    static final int MAX_STACK_FRAMES = 64;
    private static final int MAX_CHARS = EasySensitiveDataMasker.DEFAULT_TEXT_LIMIT;
    private static final String TRUNCATED_MARKER = System.lineSeparator() + "... throwable evidence truncated";

    private BoundedThrowableFormatter() {
    }

    /** Captures the bounded failure payload once so Trace, operation and incident projections can share it. */
    public static FailureEvidence capture(Throwable failure) {
        if (failure == null) return FailureEvidence.EMPTY;
        return new FailureEvidence(failure.getClass().getName(), message(failure), format(failure));
    }

    public static String format(Throwable failure) {
        if (failure == null) return "";
        BoundedBuilder result = new BoundedBuilder(MAX_CHARS);
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = failure;
        int causeDepth = 0;
        int remainingFrames = MAX_STACK_FRAMES;
        while (current != null && causeDepth < MAX_CAUSE_DEPTH && !result.truncated()) {
            if (!visited.add(current)) {
                result.append(System.lineSeparator() + "Caused by: [cycle detected]");
                break;
            }
            if (causeDepth > 0) result.append(System.lineSeparator() + "Caused by: ");
            result.append(current.getClass().getName());
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                result.append(": ");
                result.append(message);
            }

            StackTraceElement[] frames = current.getStackTrace();
            int included = Math.min(frames.length, remainingFrames);
            for (int index = 0; index < included && !result.truncated(); index++) {
                result.append(System.lineSeparator() + "\tat ");
                result.append(frames[index].toString());
            }
            remainingFrames -= included;
            if (included < frames.length && !result.truncated()) {
                result.append(System.lineSeparator() + "\t... " + (frames.length - included) + " frames omitted");
            }

            Throwable cause = current.getCause();
            if (cause == null) break;
            current = cause;
            causeDepth++;
        }
        if (current != null && causeDepth >= MAX_CAUSE_DEPTH && !result.truncated()) {
            result.append(System.lineSeparator() + "... additional causes omitted");
        }
        return result.value();
    }

    public static String message(Throwable failure) {
        if (failure == null || failure.getMessage() == null) return "";
        String message = failure.getMessage();
        if (message.length() <= MAX_CHARS) return message;
        int contentLength = Math.max(0, MAX_CHARS - TRUNCATED_MARKER.length());
        return message.substring(0, contentLength) + TRUNCATED_MARKER;
    }

    public record FailureEvidence(String type, String message, String stackTrace) {
        public static final FailureEvidence EMPTY = new FailureEvidence("", "", "");

        public FailureEvidence {
            type = type == null ? "" : type;
            message = message == null ? "" : message;
            stackTrace = stackTrace == null ? "" : stackTrace;
        }

        public boolean present() {
            return !type.isBlank() || !message.isBlank() || !stackTrace.isBlank();
        }
    }

    private static final class BoundedBuilder {
        private final int contentLimit;
        private final StringBuilder value;
        private boolean truncated;

        private BoundedBuilder(int maxChars) {
            contentLimit = Math.max(0, maxChars - TRUNCATED_MARKER.length());
            value = new StringBuilder(Math.min(maxChars, 512));
        }

        private void append(String text) {
            if (truncated || text == null || text.isEmpty()) return;
            int remaining = contentLimit - value.length();
            if (text.length() <= remaining) {
                value.append(text);
                return;
            }
            if (remaining > 0) value.append(text, 0, remaining);
            value.append(TRUNCATED_MARKER);
            truncated = true;
        }

        private boolean truncated() {
            return truncated;
        }

        private String value() {
            return value.toString();
        }
    }
}
