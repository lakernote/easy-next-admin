package com.laker.observability.web;

import com.laker.observability.config.EasyObservabilityProperties;
import com.laker.observability.trace.MdcContext;
import com.laker.observability.trace.TraceIdContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Creates or accepts a trace ID for each servlet request and exposes it through the response and MDC. */
public final class TraceIdFilter extends OncePerRequestFilter {
    private final EasyObservabilityProperties properties;

    public TraceIdFilter(EasyObservabilityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String headerName = properties.getTrace().getHeaderName();
        String traceId = TraceIdContext.resolve(request.getHeader(headerName));
        response.setHeader(headerName, traceId);
        try (MdcContext.Scope ignored = MdcContext.scope()) {
            TraceIdContext.putTraceId(traceId);
            filterChain.doFilter(request, response);
        }
    }
}
