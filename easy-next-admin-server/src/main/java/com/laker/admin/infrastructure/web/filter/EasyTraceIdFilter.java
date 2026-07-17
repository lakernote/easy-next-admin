package com.laker.admin.infrastructure.web.filter;

import com.laker.admin.common.constant.EasyNextAdminConstants;
import com.laker.admin.infrastructure.observability.trace.EasyMdcContext;
import com.laker.admin.infrastructure.observability.trace.EasyTraceIdContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class EasyTraceIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 网关、前端、服务端都用 X-Trace-Id 承载链路号；没有传入时由服务端生成。
        String traceId = EasyTraceIdContext.resolveFromRequest(request);
        response.setHeader(EasyNextAdminConstants.TRACE_ID_HEADER, traceId);
        try (EasyMdcContext.Scope ignored = EasyMdcContext.scope()) {
            EasyTraceIdContext.putOrCreateTraceId(traceId);
            filterChain.doFilter(request, response);
        }
    }
}
