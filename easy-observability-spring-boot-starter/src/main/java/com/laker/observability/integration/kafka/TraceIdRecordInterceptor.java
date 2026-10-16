package com.laker.observability.integration.kafka;

import com.laker.observability.config.EasyObservabilityProperties;
import com.laker.observability.trace.MdcContext;
import com.laker.observability.trace.TraceIdContext;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.listener.RecordInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

/** Restores the message trace ID in MDC for the listener invocation, then restores the prior context. */
public final class TraceIdRecordInterceptor implements RecordInterceptor<String, String> {
    private final String headerName;
    private final ThreadLocal<Deque<MdcContext.Scope>> scopes = ThreadLocal.withInitial(ArrayDeque::new);

    public TraceIdRecordInterceptor(EasyObservabilityProperties properties) {
        this.headerName = properties.getTrace().getHeaderName();
    }

    @Override
    public ConsumerRecord<String, String> intercept(ConsumerRecord<String, String> record,
                                                     Consumer<String, String> consumer) {
        MdcContext.Scope scope = MdcContext.scope();
        scopes.get().push(scope);
        String traceId = headerValue(record, headerName);
        if (TraceIdContext.isValidTraceId(traceId)) TraceIdContext.putTraceId(traceId);
        else TraceIdContext.getOrCreateTraceId();
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<String, String> record, Consumer<String, String> consumer) {
        Deque<MdcContext.Scope> stack = scopes.get();
        if (stack.isEmpty()) return;
        try {
            stack.pop().close();
        } finally {
            if (stack.isEmpty()) scopes.remove();
        }
    }

    private static String headerValue(ConsumerRecord<?, ?> record, String name) {
        if (record == null || name == null || name.isBlank()) return null;
        Header header = record.headers().lastHeader(name);
        if (header == null || header.value() == null) return null;
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
