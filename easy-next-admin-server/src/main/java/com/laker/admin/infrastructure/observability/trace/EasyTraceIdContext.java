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

    private EasyTraceIdContext() {
    }

    public static String currentTraceId() {
        return MDC.get(EasyNextAdminConstants.TRACE_ID);
    }

    public static String getOrCreateTraceId() {
        String traceId = currentTraceId();
        if (!StringUtils.hasText(traceId)) {
            traceId = EasyIdGenerator.uuid32();
            putTraceId(traceId);
        }
        return traceId;
    }

    public static String putOrCreateTraceId(String traceId) {
        if (StringUtils.hasText(traceId)) {
            putTraceId(traceId);
            return traceId;
        }
        return getOrCreateTraceId();
    }

    public static void putTraceId(String traceId) {
        if (StringUtils.hasText(traceId)) {
            MDC.put(EasyNextAdminConstants.TRACE_ID, traceId);
        }
    }

    public static String resolveFromRequest(HttpServletRequest request) {
        String traceId = request.getHeader(EasyNextAdminConstants.TRACE_ID_HEADER);
        return StringUtils.hasText(traceId) ? traceId : EasyIdGenerator.uuid32();
    }
}
