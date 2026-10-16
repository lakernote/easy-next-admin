package com.laker.observability.integration.feign;

import com.laker.observability.config.EasyObservabilityProperties;
import com.laker.observability.trace.TraceIdContext;
import feign.RequestInterceptor;

/** Adds the current trace ID to outgoing Feign requests. */
public final class TraceIdRequestInterceptor implements RequestInterceptor {
    private final String headerName;

    public TraceIdRequestInterceptor(EasyObservabilityProperties properties) {
        this.headerName = properties.getTrace().getHeaderName();
    }

    @Override
    public void apply(feign.RequestTemplate template) {
        template.header(headerName, TraceIdContext.getOrCreateTraceId());
    }
}
