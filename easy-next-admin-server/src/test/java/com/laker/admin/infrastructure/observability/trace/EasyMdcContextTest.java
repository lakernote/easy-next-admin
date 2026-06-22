package com.laker.admin.infrastructure.observability.trace;

import com.laker.admin.common.constant.EasyNextAdminConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class EasyMdcContextTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void withContextShouldRestorePreviousMdc() {
        MDC.put(EasyNextAdminConstants.TRACE_ID, "previous-trace");
        MDC.put(EasyNextAdminConstants.USER_ID, "previous-user");
        Map<String, String> capturedContext = Map.of(EasyNextAdminConstants.USER_ID, "7");

        try (EasyMdcContext.Scope ignored = EasyMdcContext.withContext(capturedContext)) {
            assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID)).isNull();
            assertThat(MDC.get(EasyNextAdminConstants.USER_ID)).isEqualTo("7");
        }

        assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID)).isEqualTo("previous-trace");
        assertThat(MDC.get(EasyNextAdminConstants.USER_ID)).isEqualTo("previous-user");
    }

    @Test
    void scopeShouldRestorePreviousMdcAfterMutations() {
        MDC.put(EasyNextAdminConstants.TRACE_ID, "previous-trace");
        MDC.put(EasyNextAdminConstants.USER_ID, "previous-user");

        try (EasyMdcContext.Scope ignored = EasyMdcContext.scope()) {
            MDC.put(EasyNextAdminConstants.TRACE_ID, "mutated-trace");
            MDC.remove(EasyNextAdminConstants.USER_ID);
            assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID)).isEqualTo("mutated-trace");
            assertThat(MDC.get(EasyNextAdminConstants.USER_ID)).isNull();
        }

        assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID)).isEqualTo("previous-trace");
        assertThat(MDC.get(EasyNextAdminConstants.USER_ID)).isEqualTo("previous-user");
    }

    @Test
    void wrapShouldRestoreWorkerMdcAfterRunnable() {
        MDC.put(EasyNextAdminConstants.TRACE_ID, "worker-trace");
        AtomicReference<String> traceId = new AtomicReference<>();
        AtomicReference<String> userId = new AtomicReference<>();

        Runnable wrapped = EasyMdcContext.wrap(() -> {
            traceId.set(MDC.get(EasyNextAdminConstants.TRACE_ID));
            userId.set(MDC.get(EasyNextAdminConstants.USER_ID));
        }, null);

        wrapped.run();

        assertThat(traceId.get()).isNull();
        assertThat(userId.get()).isNull();
        assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID)).isEqualTo("worker-trace");
    }

    @Test
    void putOrRemoveShouldUseValueWhenPresentAndRemoveWhenBlank() {
        EasyMdcContext.putOrRemove(EasyNextAdminConstants.USER_ID, "7");
        assertThat(EasyMdcContext.get(EasyNextAdminConstants.USER_ID)).isEqualTo("7");

        EasyMdcContext.putOrRemove(EasyNextAdminConstants.USER_ID, " ");
        assertThat(EasyMdcContext.get(EasyNextAdminConstants.USER_ID)).isNull();
    }
}
