package com.laker.admin.infrastructure.web.filter;

import com.laker.admin.common.constant.EasyNextAdminConstants;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class EasyTraceIdFilterTest {

    private final EasyTraceIdFilter filter = new EasyTraceIdFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldExposeTraceIdHeaderAndRestorePreviousMdcAfterRequest() throws Exception {
        MDC.put(EasyNextAdminConstants.TRACE_ID, "previous-trace");
        MDC.put("customKey", "custom-value");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/system/users");
        request.addHeader(EasyNextAdminConstants.TRACE_ID_HEADER, "client-trace-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) ->
                assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID)).isEqualTo("client-trace-id");

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(EasyNextAdminConstants.TRACE_ID_HEADER)).isEqualTo("client-trace-id");
        assertThat(response.getHeaderNames()).containsExactly(EasyNextAdminConstants.TRACE_ID_HEADER);
        assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID)).isEqualTo("previous-trace");
        assertThat(MDC.get("customKey")).isEqualTo("custom-value");
    }

    @Test
    void shouldCreateTraceIdWhenRequestHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/system/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) ->
                assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID)).hasSize(32);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(EasyNextAdminConstants.TRACE_ID_HEADER)).hasSize(32);
        assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID)).isNull();
    }
}
