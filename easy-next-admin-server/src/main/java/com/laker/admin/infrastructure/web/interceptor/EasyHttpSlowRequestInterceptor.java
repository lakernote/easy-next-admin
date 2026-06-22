package com.laker.admin.infrastructure.web.interceptor;

import com.laker.admin.infrastructure.observability.trace.SpanType;
import com.laker.admin.infrastructure.observability.trace.TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class EasyHttpSlowRequestInterceptor implements HandlerInterceptor {
    private static final String HANDLER_ATTRIBUTE = EasyHttpSlowRequestInterceptor.class.getName() + ".handler";

    private final boolean enabled;
    private final long slowThresholdMs;
    private final int maxDepth;
    private final long minNodeCostMs;

    public EasyHttpSlowRequestInterceptor(boolean enabled, long slowThresholdMs, int maxDepth, long minNodeCostMs) {
        this.enabled = enabled;
        this.slowThresholdMs = slowThresholdMs;
        this.maxDepth = maxDepth;
        this.minNodeCostMs = minNodeCostMs;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String handlerName = resolveHandlerName(handler);
        request.setAttribute(HANDLER_ATTRIBUTE, handlerName);
        if (enabled && slowThresholdMs > 0) {
            TraceContext.startRoot(handlerName, SpanType.Http, request.getMethod() + " " + requestUri(request),
                    maxDepth, minNodeCostMs);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!enabled) {
            TraceContext.clear();
            return;
        }
        TraceContext.stopRoot(slowThresholdMs, "HTTP trace tree, status=%s".formatted(response.getStatus()), ex);
    }

    private static String resolveHandlerName(Object handler) {
        if (handler instanceof HandlerMethod handlerMethod) {
            return handlerMethod.getBeanType().getSimpleName() + "#" + handlerMethod.getMethod().getName();
        }
        return handler == null ? "-" : handler.getClass().getSimpleName();
    }

    private static String requestUri(HttpServletRequest request) {
        String queryString = request.getQueryString();
        if (queryString == null || queryString.isBlank()) {
            return request.getRequestURI();
        }
        return request.getRequestURI() + "?" + queryString;
    }
}
