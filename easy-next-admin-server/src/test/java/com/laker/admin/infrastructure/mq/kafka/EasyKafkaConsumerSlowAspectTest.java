package com.laker.admin.infrastructure.mq.kafka;

import com.laker.admin.common.constant.EasyNextAdminConstants;
import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.infrastructure.observability.jfr.OperationObservation;
import com.laker.admin.infrastructure.observability.trace.RecentTraceStore;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
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

    @Test
    void aroundKafkaListenerShouldReplaceInvalidTraceIdHeader() throws Throwable {
        ConsumerRecord<String, String> record = recordWithHeaders("trace-id\nforged", null);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[]{record});
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID))
                    .hasSize(32)
                    .doesNotContain("forged");
            return "ok";
        });
        EasyKafkaConsumerSlowAspect aspect = new EasyKafkaConsumerSlowAspect(configWithoutTraceTree());

        Object result = aspect.aroundKafkaListener(joinPoint, mock(KafkaListener.class));

        assertThat(result).isEqualTo("ok");
        assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID)).isNull();
    }

    @Test
    void aroundKafkaListenerShouldPersistAndRethrowFatalError() throws Throwable {
        ConsumerRecord<String, String> record = recordWithHeaders("trace-fatal-kafka", null);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[]{record});
        AtomicBoolean resourceFrozen = new AtomicBoolean();
        OutOfMemoryError failure = new BoundaryCheckedOutOfMemoryError(
                "simulated kafka allocation failure", resourceFrozen);
        when(joinPoint.proceed()).thenAnswer(invocation -> { throw failure; });
        RecentTraceStore store = mock(RecentTraceStore.class);
        OperationObservation observation = mock(OperationObservation.class);
        OperationObservation.Scope scope = mock(OperationObservation.Scope.class);
        when(observation.start("KAFKA", "KafkaListener")).thenReturn(scope);
        doAnswer(invocation -> {
            resourceFrozen.set(true);
            return null;
        }).when(scope).finish(eq("failure"), any(Throwable.class));
        EasyNextAdminConfig config = configWithoutTraceTree();
        config.getTrace().setEnabled(true);
        EasyKafkaConsumerSlowAspect aspect = new EasyKafkaConsumerSlowAspect(
                config, store, observation);

        assertThatThrownBy(() -> aspect.aroundKafkaListener(joinPoint, mock(KafkaListener.class)))
                .isSameAs(failure);

        verify(store).recordIncident(eq("KAFKA"), eq("trace-fatal-kafka"), eq("KafkaListener"), eq("-"),
                eq(500), contains("KafkaListener"), any(Long.class), eq(false), eq(true), any(),
                eq(failure.getClass().getName()), eq("simulated kafka allocation failure"),
                contains("OutOfMemoryError"), isNull(), isNull(), isNull(), isNull());
        assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID)).isNull();
    }

    @Test
    void monitoringErrorMustNotReplaceKafkaBusinessFailure() throws Throwable {
        ConsumerRecord<String, String> record = recordWithHeaders("trace-monitor-failure", null);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[]{record});
        IllegalStateException businessFailure = new IllegalStateException("business failure");
        when(joinPoint.proceed()).thenThrow(businessFailure);
        OperationObservation observation = mock(OperationObservation.class);
        OperationObservation.Scope scope = mock(OperationObservation.Scope.class);
        when(observation.start("KAFKA", "KafkaListener")).thenReturn(scope);
        doThrow(new AssertionError("monitoring finalizer failed"))
                .when(scope).finish("failure", businessFailure);
        EasyKafkaConsumerSlowAspect aspect = new EasyKafkaConsumerSlowAspect(
                configWithoutTraceTree(), mock(RecentTraceStore.class), observation);

        assertThatThrownBy(() -> aspect.aroundKafkaListener(joinPoint, mock(KafkaListener.class)))
                .isSameAs(businessFailure);

        verify(scope).discard();
        assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID)).isNull();
    }

    private static final class BoundaryCheckedOutOfMemoryError extends OutOfMemoryError {
        private final AtomicBoolean resourceFrozen;

        private BoundaryCheckedOutOfMemoryError(String message, AtomicBoolean resourceFrozen) {
            super(message);
            this.resourceFrozen = resourceFrozen;
        }

        @Override
        public String getMessage() {
            assertThat(resourceFrozen)
                    .as("Kafka resources must be frozen before trace/error evidence is formatted")
                    .isTrue();
            return super.getMessage();
        }
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
