package com.laker.observability.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdContextTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void getOrCreateTraceIdShouldReuseExistingTraceId() {
        MDC.put(TraceIdContext.MDC_KEY, "existing-trace-id");

        String traceId = TraceIdContext.getOrCreateTraceId();

        assertThat(traceId).isEqualTo("existing-trace-id");
        assertThat(MDC.get(TraceIdContext.MDC_KEY)).isEqualTo("existing-trace-id");
    }

    @Test
    void getOrCreateTraceIdShouldCreateMissingTraceId() {
        String traceId = TraceIdContext.getOrCreateTraceId();

        assertThat(traceId).hasSize(32);
        assertThat(MDC.get(TraceIdContext.MDC_KEY)).isEqualTo(traceId);
    }

    @Test
    void getOrCreateTraceIdShouldReplaceInvalidMdcTraceId() {
        MDC.put(TraceIdContext.MDC_KEY, "bad trace\nforged-log-line");

        String traceId = TraceIdContext.getOrCreateTraceId();

        assertThat(traceId).hasSize(32).doesNotContain("bad trace");
        assertThat(MDC.get(TraceIdContext.MDC_KEY)).isEqualTo(traceId);
    }

    @Test
    void putOrCreateTraceIdShouldUseProvidedTraceId() {
        String traceId = TraceIdContext.putOrCreateTraceId("incoming-trace");

        assertThat(traceId).isEqualTo("incoming-trace");
        assertThat(MDC.get(TraceIdContext.MDC_KEY)).isEqualTo("incoming-trace");
    }

    @Test
    void putOrCreateTraceIdShouldCreateMissingTraceId() {
        String traceId = TraceIdContext.putOrCreateTraceId(null);

        assertThat(traceId).hasSize(32);
        assertThat(MDC.get(TraceIdContext.MDC_KEY)).isEqualTo(traceId);
    }

    @Test
    void putOrCreateTraceIdShouldReplaceInvalidInputInsteadOfReusingMdc() {
        MDC.put(TraceIdContext.MDC_KEY, "previous-trace");

        String traceId = TraceIdContext.putOrCreateTraceId("../../forged");

        assertThat(traceId).hasSize(32).isNotEqualTo("previous-trace");
        assertThat(MDC.get(TraceIdContext.MDC_KEY)).isEqualTo(traceId);
    }

    @Test
    void resolveShouldKeepValidIncomingTraceId() {
        assertThat(TraceIdContext.resolve("client-trace-id")).isEqualTo("client-trace-id");
    }

    @Test
    void resolveShouldCreateTraceIdForInvalidIncomingValue() {
        String traceId = TraceIdContext.resolve("bad trace\nforged-log-line");

        assertThat(traceId).hasSize(32).doesNotContain("bad trace");
    }

    @Test
    void traceIdValidationShouldSupportCommonFormatsAndRejectUnsafeCharacters() {
        assertThat(TraceIdContext.isValidTraceId("4bf92f3577b34da6a3ce929d0e0e4736")).isTrue();
        assertThat(TraceIdContext.isValidTraceId("550e8400-e29b-41d4-a716-446655440000")).isTrue();
        assertThat(TraceIdContext.isValidTraceId("gateway_1:order.create-42")).isTrue();
        assertThat(TraceIdContext.isValidTraceId("a".repeat(TraceIdContext.MAX_TRACE_ID_LENGTH))).isTrue();

        assertThat(TraceIdContext.isValidTraceId("a".repeat(TraceIdContext.MAX_TRACE_ID_LENGTH + 1))).isFalse();
        assertThat(TraceIdContext.isValidTraceId(" leading-space")).isFalse();
        assertThat(TraceIdContext.isValidTraceId("trace/id")).isFalse();
        assertThat(TraceIdContext.isValidTraceId("trace\"id")).isFalse();
        assertThat(TraceIdContext.isValidTraceId("链路-1")).isFalse();
    }

    private String legacyRequestIdHeader() {
        return "X-" + "Request" + "-Id";
    }
}
