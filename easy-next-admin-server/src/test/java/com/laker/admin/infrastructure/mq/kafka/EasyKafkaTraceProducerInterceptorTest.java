package com.laker.admin.infrastructure.mq.kafka;

import com.laker.admin.common.constant.EasyNextAdminConstants;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class EasyKafkaTraceProducerInterceptorTest {

    private final EasyKafkaTraceProducerInterceptor interceptor = new EasyKafkaTraceProducerInterceptor();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void onSendShouldCreateTraceIdWhenMissing() {
        ProducerRecord<String, String> record = new ProducerRecord<>("topic", "key", "value");

        ProducerRecord<String, String> result = interceptor.onSend(record);

        Header traceHeader = result.headers().lastHeader(EasyNextAdminConstants.TRACE_ID_HEADER);
        assertThat(headerValue(traceHeader)).hasSize(32);
        assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID)).isEqualTo(headerValue(traceHeader));
    }

    @Test
    void onSendShouldPropagateTraceIdAndUserIdHeaders() {
        MDC.put(EasyNextAdminConstants.TRACE_ID, "trace-1");
        MDC.put(EasyNextAdminConstants.USER_ID, "7");
        ProducerRecord<String, String> record = new ProducerRecord<>("topic", "key", "value");
        record.headers().add(EasyNextAdminConstants.TRACE_ID_HEADER, "stale-trace".getBytes(StandardCharsets.UTF_8));
        record.headers().add(EasyNextAdminConstants.USER_ID, "stale-user".getBytes(StandardCharsets.UTF_8));

        ProducerRecord<String, String> result = interceptor.onSend(record);

        assertThat(result.headers().headers(EasyNextAdminConstants.TRACE_ID_HEADER)).hasSize(1);
        assertThat(result.headers().headers(EasyNextAdminConstants.USER_ID)).hasSize(1);
        assertThat(headerValue(result.headers().lastHeader(EasyNextAdminConstants.TRACE_ID_HEADER))).isEqualTo("trace-1");
        assertThat(headerValue(result.headers().lastHeader(EasyNextAdminConstants.USER_ID))).isEqualTo("7");
    }

    @Test
    void onSendShouldRemoveStaleUserIdHeaderWhenMdcHasNoUserId() {
        MDC.put(EasyNextAdminConstants.TRACE_ID, "trace-1");
        ProducerRecord<String, String> record = new ProducerRecord<>("topic", "key", "value");
        record.headers().add(EasyNextAdminConstants.USER_ID, "stale-user".getBytes(StandardCharsets.UTF_8));

        ProducerRecord<String, String> result = interceptor.onSend(record);

        assertThat(result.headers().lastHeader(EasyNextAdminConstants.USER_ID)).isNull();
    }

    private String headerValue(Header header) {
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
