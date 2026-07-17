package com.laker.admin.infrastructure.mq.kafka;

import com.laker.admin.common.constant.EasyNextAdminConstants;
import com.laker.admin.config.properties.EasyNextAdminConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EasyKafkaConsumerSlowAspectTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void aroundKafkaListenerShouldRestoreTraceIdAndUserIdFromHeaders() throws Throwable {
        MDC.put(EasyNextAdminConstants.TRACE_ID, "previous-trace");
        MDC.put(EasyNextAdminConstants.USER_ID, "previous-user");
        ConsumerRecord<String, String> record = recordWithHeaders("incoming-trace", "7");
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[]{record});
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID)).isEqualTo("incoming-trace");
            assertThat(MDC.get(EasyNextAdminConstants.USER_ID)).isEqualTo("7");
            return "ok";
        });
        EasyKafkaConsumerSlowAspect aspect = new EasyKafkaConsumerSlowAspect(configWithoutTraceTree());

        Object result = aspect.aroundKafkaListener(joinPoint, mock(KafkaListener.class));

        assertThat(result).isEqualTo("ok");
        assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID)).isEqualTo("previous-trace");
        assertThat(MDC.get(EasyNextAdminConstants.USER_ID)).isEqualTo("previous-user");
    }

    @Test
    void aroundKafkaListenerShouldCreateTraceIdWhenHeaderIsMissing() throws Throwable {
        ConsumerRecord<String, String> record = recordWithHeaders(null, null);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[]{record});
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID)).hasSize(32);
            assertThat(MDC.get(EasyNextAdminConstants.USER_ID)).isNull();
            return "ok";
        });
        EasyKafkaConsumerSlowAspect aspect = new EasyKafkaConsumerSlowAspect(configWithoutTraceTree());

        Object result = aspect.aroundKafkaListener(joinPoint, mock(KafkaListener.class));

        assertThat(result).isEqualTo("ok");
        assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID)).isNull();
        assertThat(MDC.get(EasyNextAdminConstants.USER_ID)).isNull();
    }

    private ConsumerRecord<String, String> recordWithHeaders(String traceId, String userId) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0L, "key", "value");
        if (traceId != null) {
            record.headers().add(EasyNextAdminConstants.TRACE_ID_HEADER, traceId.getBytes(StandardCharsets.UTF_8));
        }
        if (userId != null) {
            record.headers().add(EasyNextAdminConstants.USER_ID, userId.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    private EasyNextAdminConfig configWithoutTraceTree() {
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getTrace().setEnabled(false);
        return config;
    }
}
