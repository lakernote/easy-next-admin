package com.laker.observability.integration.kafka;

import com.laker.observability.trace.TraceIdContext;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Copies the current trace ID to outgoing Kafka records without retaining the message payload. */
public class TraceIdProducerInterceptor<K, V> implements ProducerInterceptor<K, V> {
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public ProducerRecord<K, V> onSend(ProducerRecord<K, V> record) {
        String traceId = TraceIdContext.getOrCreateTraceId();
        record.headers().remove(TRACE_ID_HEADER);
        record.headers().add(TRACE_ID_HEADER, traceId.getBytes(StandardCharsets.UTF_8));
        return record;
    }

    @Override public void onAcknowledgement(RecordMetadata metadata, Exception exception) { }
    @Override public void close() { }
    @Override public void configure(Map<String, ?> configs) { }
}
