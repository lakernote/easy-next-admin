package com.laker.admin.infrastructure.mq.kafka;

import com.laker.admin.common.constant.EasyNextAdminConstants;
import io.github.lakernote.meerkat.trace.TraceIdContext;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Kafka 生产端链路号透传。
 *
 * <p>当前线程处于 Meerkat Trace 边界时透传 X-Trace-Id；没有当前 Trace 时不伪造链路号。
 * 应用自己的 userId 上下文仍随消息传播。</p>
 */
public class EasyKafkaTraceProducerInterceptor implements ProducerInterceptor<String, String> {
    @Override
    public ProducerRecord<String, String> onSend(ProducerRecord<String, String> record) {
        String traceId = TraceIdContext.currentTraceId();
        if (StringUtils.hasText(traceId)) {
            record.headers().remove(EasyNextAdminConstants.TRACE_ID_HEADER);
            record.headers().add(EasyNextAdminConstants.TRACE_ID_HEADER, traceId.getBytes(StandardCharsets.UTF_8));
        }
        record.headers().remove(EasyNextAdminConstants.USER_ID);
        String userId = MDC.get(EasyNextAdminConstants.USER_ID);
        if (StringUtils.hasText(userId)) {
            record.headers().add(EasyNextAdminConstants.USER_ID, userId.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        // no-op
    }

    @Override
    public void close() {
        // no-op
    }

    @Override
    public void configure(Map<String, ?> configs) {
        // no-op; EasyNextAdmin intentionally uses the shared X-Trace-Id contract.
    }
}
