package com.laker.admin.infrastructure.mq.kafka;

import com.laker.admin.common.constant.EasyNextAdminConstants;
import com.laker.observability.trace.MdcContext;
import com.laker.observability.integration.kafka.TraceIdProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

/**
 * Kafka 生产端链路号透传。
 *
 * <p>所有通过 KafkaTemplate 发送的消息都会带上 X-Trace-Id header，
 * 下游消费者可用该值恢复 MDC，串起 HTTP 请求、异步消息和任务日志。</p>
 */
public class EasyKafkaTraceProducerInterceptor extends TraceIdProducerInterceptor<String, String> {
    @Override
    public ProducerRecord<String, String> onSend(ProducerRecord<String, String> record) {
        super.onSend(record);
        record.headers().remove(EasyNextAdminConstants.USER_ID);
        String userId = MdcContext.get(EasyNextAdminConstants.USER_ID);
        if (StringUtils.hasText(userId)) {
            record.headers().add(EasyNextAdminConstants.USER_ID, userId.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

}
