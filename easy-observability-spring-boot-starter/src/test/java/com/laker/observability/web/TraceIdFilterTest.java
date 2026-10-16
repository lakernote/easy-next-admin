package com.laker.observability.web;

import com.laker.observability.config.EasyObservabilityProperties;
import com.laker.observability.trace.TraceIdContext;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdFilterTest {
    private final TraceIdFilter filter = new TraceIdFilter(new EasyObservabilityProperties());

    @AfterEach
    void clearMdc() { MDC.clear(); }

    @Test
    void acceptsTraceIdAndRestoresMdcAfterRequest() throws Exception {
        MDC.put(TraceIdContext.MDC_KEY, "previous");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.addHeader("X-Trace-Id", "client-trace");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) ->
                assertThat(MDC.get(TraceIdContext.MDC_KEY)).isEqualTo("client-trace");

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("client-trace");
        assertThat(MDC.get(TraceIdContext.MDC_KEY)).isEqualTo("previous");
    }

    @Test
    void generatesSafeTraceIdWhenHeaderIsMissingOrInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.addHeader("X-Trace-Id", "bad\r\nheader");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertThat(MDC.get(TraceIdContext.MDC_KEY)).hasSize(32));

        assertThat(response.getHeader("X-Trace-Id")).hasSize(32);
        assertThat(MDC.get(TraceIdContext.MDC_KEY)).isNull();
    }
}
