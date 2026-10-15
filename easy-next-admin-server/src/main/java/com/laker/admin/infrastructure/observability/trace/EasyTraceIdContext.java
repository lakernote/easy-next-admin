package com.laker.admin.infrastructure.observability.trace;

import com.laker.admin.common.constant.EasyNextAdminConstants;
import com.laker.admin.infrastructure.id.EasyIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

/**
 * EasyNextAdmin 业务链路号上下文。
 *
 * <p>企业后台的业务响应体只承载业务字段，链路号统一放在 {@code X-Trace-Id} 请求头和响应头。
 * 日志、远程 HTTP、Kafka 发送端都从 MDC 读取同一个值，便于排查一次用户操作贯穿的调用链。</p>
 */
public final class EasyTraceIdContext {

    /**
     * W3C trace id 为 32 位十六进制，Brave/B3 常见 16 或 32 位，UUID 为 36 位。
     * 预留业务前缀后将上限定为 64，避免外部输入无限放大日志、JSONL 与 JFR 事件。
     */
    public static final int MAX_TRACE_ID_LENGTH = 64;

    private EasyTraceIdContext() {
    }

    public static String currentTraceId() {
        return MDC.get(EasyNextAdminConstants.TRACE_ID);
    }

    public static String getOrCreateTraceId() {
        String traceId = currentTraceId();
        if (!isValidTraceId(traceId)) {
            traceId = EasyIdGenerator.uuid32();
            putTraceId(traceId);
        }
        return traceId;
    }

    public static String putOrCreateTraceId(String traceId) {
        if (isValidTraceId(traceId)) {
            putTraceId(traceId);
            return traceId;
        }
        String generatedTraceId = EasyIdGenerator.uuid32();
        putTraceId(generatedTraceId);
        return generatedTraceId;
    }

    public static void putTraceId(String traceId) {
        if (StringUtils.hasText(traceId)) {
            MDC.put(EasyNextAdminConstants.TRACE_ID, traceId);
        }
    }

    public static void clear() {
        MDC.remove(EasyNextAdminConstants.TRACE_ID);
    }

    public static String resolveFromRequest(HttpServletRequest request) {
        String traceId = request.getHeader(EasyNextAdminConstants.TRACE_ID_HEADER);
        return isValidTraceId(traceId) ? traceId : EasyIdGenerator.uuid32();
    }

    /**
     * 只接收可安全写入日志、JSONL 和 JFR 字符串字段的短 ASCII 标识。
     * 支持十六进制 trace id、UUID，以及带 {@code . _ : -} 分隔符的网关业务链路号。
     */
    public static boolean isValidTraceId(String traceId) {
        if (!StringUtils.hasText(traceId)
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
