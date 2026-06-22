package com.laker.admin.infrastructure.mq.kafka;

import com.laker.admin.common.constant.EasyNextAdminConstants;
import com.laker.admin.infrastructure.observability.trace.EasyMdcContext;
import com.laker.admin.infrastructure.observability.trace.EasyTraceIdContext;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Kafka 生产端链路号透传。
 *
 * <p>所有通过 KafkaTemplate 发送的消息都会带上 X-Trace-Id header，
 * 下游消费者可用该值恢复 MDC，串起 HTTP 请求、异步消息和任务日志。</p>
 */
public class EasyKafkaTraceProducerInterceptor implements ProducerInterceptor<String, String> {
    @Override
    public ProducerRecord<String, String> onSend(ProducerRecord<String, String> record) {
        String traceId = EasyTraceIdContext.getOrCreateTraceId();
        record.headers().remove(EasyNextAdminConstants.TRACE_ID_HEADER);
        record.headers().add(EasyNextAdminConstants.TRACE_ID_HEADER, traceId.getBytes(StandardCharsets.UTF_8));
        record.headers().remove(EasyNextAdminConstants.USER_ID);
        String userId = EasyMdcContext.get(EasyNextAdminConstants.USER_ID);
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
        // no-op
    }
}
