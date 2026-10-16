package com.laker.observability.trace;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Accesses the current trace ID stored in SLF4J MDC.
 *
 * <p>The trace ID is stored in SLF4J MDC so logging and transport adapters can read and propagate the same value.
 * An HTTP integration can expose it through the {@code X-Trace-Id} request and response headers.</p>
 */
public final class TraceIdContext {
    /** MDC key used to store the current trace ID. */
    public static final String MDC_KEY = "traceId";

    /**
     * W3C trace id 为 32 位十六进制，Brave/B3 常见 16 或 32 位，UUID 为 36 位。
     * 预留业务前缀后将上限定为 64，避免外部输入无限放大日志、JSONL 与 JFR 事件。
     */
    public static final int MAX_TRACE_ID_LENGTH = 64;

    private TraceIdContext() {
    }

    public static String currentTraceId() {
        return MDC.get(MDC_KEY);
    }

    public static String getOrCreateTraceId() {
        String traceId = currentTraceId();
        if (!isValidTraceId(traceId)) {
            traceId = newTraceId();
            putTraceId(traceId);
        }
        return traceId;
    }

    public static String putOrCreateTraceId(String traceId) {
        if (isValidTraceId(traceId)) {
            putTraceId(traceId);
            return traceId;
        }
        String generatedTraceId = newTraceId();
        putTraceId(generatedTraceId);
        return generatedTraceId;
    }

    public static void putTraceId(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(MDC_KEY, traceId);
        }
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }

    public static String resolve(String traceId) {
        return isValidTraceId(traceId) ? traceId : newTraceId();
    }

    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 只接收可安全写入日志、JSONL 和 JFR 字符串字段的短 ASCII 标识。
     * 支持十六进制 trace id、UUID，以及带 {@code . _ : -} 分隔符的网关业务链路号。
     */
    public static boolean isValidTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()
                || traceId.length() > MAX_TRACE_ID_LENGTH
                || !isAsciiLetterOrDigit(traceId.charAt(0))) {
            return false;
        }
        for (int i = 1; i < traceId.length(); i++) {
            char character = traceId.charAt(i);
            if (!isAsciiLetterOrDigit(character)
                    && character != '.'
                    && character != '_'
                    && character != ':'
                    && character != '-') {
                return false;
            }
        }
        return true;
    }

    private static boolean isAsciiLetterOrDigit(char character) {
        return character >= '0' && character <= '9'
                || character >= 'A' && character <= 'Z'
                || character >= 'a' && character <= 'z';
    }
}
