package com.laker.observability.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MdcContextTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void withContextShouldRestorePreviousMdc() {
        MDC.put(TraceIdContext.MDC_KEY, "previous-trace");
        MDC.put("userId", "previous-user");
        Map<String, String> capturedContext = Map.of("userId", "7");

        try (MdcContext.Scope ignored = MdcContext.withContext(capturedContext)) {
            assertThat(MDC.get(TraceIdContext.MDC_KEY)).isNull();
            assertThat(MDC.get("userId")).isEqualTo("7");
        }

        assertThat(MDC.get(TraceIdContext.MDC_KEY)).isEqualTo("previous-trace");
        assertThat(MDC.get("userId")).isEqualTo("previous-user");
    }

    @Test
    void scopeShouldRestorePreviousMdcAfterMutations() {
        MDC.put(TraceIdContext.MDC_KEY, "previous-trace");
        MDC.put("userId", "previous-user");

        try (MdcContext.Scope ignored = MdcContext.scope()) {
            MDC.put(TraceIdContext.MDC_KEY, "mutated-trace");
            MDC.remove("userId");
            assertThat(MDC.get(TraceIdContext.MDC_KEY)).isEqualTo("mutated-trace");
            assertThat(MDC.get("userId")).isNull();
        }

        assertThat(MDC.get(TraceIdContext.MDC_KEY)).isEqualTo("previous-trace");
        assertThat(MDC.get("userId")).isEqualTo("previous-user");
    }

    @Test
    void wrapShouldRestoreWorkerMdcAfterRunnable() {
        MDC.put(TraceIdContext.MDC_KEY, "worker-trace");
        AtomicReference<String> traceId = new AtomicReference<>();
        AtomicReference<String> userId = new AtomicReference<>();

        Runnable wrapped = MdcContext.wrap(() -> {
            traceId.set(MDC.get(TraceIdContext.MDC_KEY));
            userId.set(MDC.get("userId"));
        }, null);

        wrapped.run();

        assertThat(traceId.get()).isNull();
        assertThat(userId.get()).isNull();
        assertThat(MDC.get(TraceIdContext.MDC_KEY)).isEqualTo("worker-trace");
    }

    @Test
    void putOrRemoveShouldUseValueWhenPresentAndRemoveWhenBlank() {
        MdcContext.putOrRemove("userId", "7");
        assertThat(MdcContext.get("userId")).isEqualTo("7");

        MdcContext.putOrRemove("userId", " ");
        assertThat(MdcContext.get("userId")).isNull();
    }
}
