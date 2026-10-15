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
    void getOrCreateTraceIdShouldReplaceInvalidMdcTraceId() {
        MDC.put(EasyNextAdminConstants.TRACE_ID, "bad trace\nforged-log-line");

        String traceId = EasyTraceIdContext.getOrCreateTraceId();

        assertThat(traceId).hasSize(32).doesNotContain("bad trace");
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
    void putOrCreateTraceIdShouldReplaceInvalidInputInsteadOfReusingMdc() {
        MDC.put(EasyNextAdminConstants.TRACE_ID, "previous-trace");

        String traceId = EasyTraceIdContext.putOrCreateTraceId("../../forged");

        assertThat(traceId).hasSize(32).isNotEqualTo("previous-trace");
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

    @Test
    void resolveFromRequestShouldReplaceOversizedTraceId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(EasyNextAdminConstants.TRACE_ID_HEADER,
                "a".repeat(EasyTraceIdContext.MAX_TRACE_ID_LENGTH + 1));

        String traceId = EasyTraceIdContext.resolveFromRequest(request);

        assertThat(traceId).hasSize(32);
    }

    @Test
    void traceIdValidationShouldSupportCommonFormatsAndRejectUnsafeCharacters() {
        assertThat(EasyTraceIdContext.isValidTraceId("4bf92f3577b34da6a3ce929d0e0e4736")).isTrue();
        assertThat(EasyTraceIdContext.isValidTraceId("550e8400-e29b-41d4-a716-446655440000")).isTrue();
        assertThat(EasyTraceIdContext.isValidTraceId("gateway_1:order.create-42")).isTrue();
        assertThat(EasyTraceIdContext.isValidTraceId("a".repeat(EasyTraceIdContext.MAX_TRACE_ID_LENGTH))).isTrue();

        assertThat(EasyTraceIdContext.isValidTraceId("a".repeat(EasyTraceIdContext.MAX_TRACE_ID_LENGTH + 1))).isFalse();
        assertThat(EasyTraceIdContext.isValidTraceId(" leading-space")).isFalse();
        assertThat(EasyTraceIdContext.isValidTraceId("trace/id")).isFalse();
        assertThat(EasyTraceIdContext.isValidTraceId("trace\"id")).isFalse();
        assertThat(EasyTraceIdContext.isValidTraceId("链路-1")).isFalse();
    }

    private String legacyRequestIdHeader() {
        return "X-" + "Request" + "-Id";
    }
}
