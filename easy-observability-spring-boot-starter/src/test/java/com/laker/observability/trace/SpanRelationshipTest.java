package com.laker.observability.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpanRelationshipTest {
    @AfterEach
    void clearTrace() {
        TraceContext.clear();
    }

    @Test
    void detachedOperationsAttachToTheCurrentSpanAndExposeStableIds() {
        String rootId = TraceContext.startRootSpan("GET /orders", SpanKind.HTTP, "", 8, 0);
        TraceContext.addSpan("OrderService#load", SpanKind.SERVICE, "order");
        String serviceId = TraceContext.currentSpanId();

        TraceContext.DetachedSpan sql = TraceContext.startDetachedSpan("OrderMapper#select", SpanKind.DATABASE, "SELECT");

        assertThat(rootId).isEqualTo("1");
        assertThat(serviceId).isNotBlank().isNotEqualTo(rootId);
        assertThat(sql.spanId()).isNotBlank().isNotEqualTo(serviceId);
        assertThat(sql.parentSpanId()).isEqualTo(serviceId);
    }
}
