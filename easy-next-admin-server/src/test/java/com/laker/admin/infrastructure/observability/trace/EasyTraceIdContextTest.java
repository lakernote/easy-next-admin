package com.laker.admin.infrastructure.observability.trace;

import com.laker.admin.common.constant.EasyNextAdminConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class EasyTraceIdContextTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void getOrCreateTraceIdShouldReuseExistingTraceId() {
        MDC.put(EasyNextAdminConstants.TRACE_ID, "existing-trace-id");

        String traceId = EasyTraceIdContext.getOrCreateTraceId();

        assertThat(traceId).isEqualTo("existing-trace-id");
        assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID)).isEqualTo("existing-trace-id");
    }

    @Test
    void getOrCreateTraceIdShouldCreateMissingTraceId() {
        String traceId = EasyTraceIdContext.getOrCreateTraceId();

        assertThat(traceId).hasSize(32);
        assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID)).isEqualTo(traceId);
    }

    @Test
    void putOrCreateTraceIdShouldUseProvidedTraceId() {
        String traceId = EasyTraceIdContext.putOrCreateTraceId("incoming-trace");

        assertThat(traceId).isEqualTo("incoming-trace");
        assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID)).isEqualTo("incoming-trace");
    }

    @Test
    void putOrCreateTraceIdShouldCreateMissingTraceId() {
        String traceId = EasyTraceIdContext.putOrCreateTraceId(null);

        assertThat(traceId).hasSize(32);
        assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID)).isEqualTo(traceId);
    }

    @Test
    void resolveFromRequestShouldUseTraceIdHeaderOnly() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(EasyNextAdminConstants.TRACE_ID_HEADER, "client-trace-id");
        request.addHeader(legacyRequestIdHeader(), "legacy-request-id");

        String traceId = EasyTraceIdContext.resolveFromRequest(request);

        assertThat(traceId).isEqualTo("client-trace-id");
    }

    @Test
    void resolveFromRequestShouldIgnoreLegacyRequestIdHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(legacyRequestIdHeader(), "legacy-request-id");

        String traceId = EasyTraceIdContext.resolveFromRequest(request);

        assertThat(traceId).isNotEqualTo("legacy-request-id");
        assertThat(traceId).hasSize(32);
    }

    @Test
    void resolveFromRequestShouldIgnoreMdcKeyHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(EasyNextAdminConstants.TRACE_ID, "mdc-key-trace-id");

        String traceId = EasyTraceIdContext.resolveFromRequest(request);

        assertThat(traceId).isNotEqualTo("mdc-key-trace-id");
        assertThat(traceId).hasSize(32);
    }

    private String legacyRequestIdHeader() {
        return "X-" + "Request" + "-Id";
    }
}
