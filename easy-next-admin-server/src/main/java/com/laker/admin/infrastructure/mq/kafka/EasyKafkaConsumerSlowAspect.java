package com.laker.admin.infrastructure.mq.kafka;

import com.laker.admin.common.constant.EasyNextAdminConstants;
import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.infrastructure.observability.trace.EasyMdcContext;
import com.laker.admin.infrastructure.observability.trace.EasyTraceIdContext;
import com.laker.admin.infrastructure.observability.trace.SpanType;
import com.laker.admin.infrastructure.observability.trace.TraceContext;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

@Aspect
@Component
@RequiredArgsConstructor
@ConditionalOnClass(KafkaListener.class)
@ConditionalOnProperty(prefix = "easy.features", name = "kafka", havingValue = "true")
public class EasyKafkaConsumerSlowAspect {
    private final EasyNextAdminConfig easyNextAdminConfig;

    @Around(value = "@annotation(kafkaListener)", argNames = "joinPoint,kafkaListener")
    public Object aroundKafkaListener(ProceedingJoinPoint joinPoint, KafkaListener kafkaListener) throws Throwable {
        Object[] args = joinPoint.getArgs();
        try (EasyMdcContext.Scope ignored = EasyMdcContext.scope()) {
            EasyTraceIdContext.putOrCreateTraceId(resolveTraceId(args));
            EasyMdcContext.putOrRemove(EasyNextAdminConstants.USER_ID, resolveUserId(args));
            boolean traceEnabled = easyNextAdminConfig.getTrace().isEnabled();
            long thresholdMs = easyNextAdminConfig.getTrace().getKafkaConsumerSlowThresholdMs();
            if (traceEnabled && thresholdMs > 0) {
                TraceContext.startRoot(joinPoint.getSignature().toShortString(), SpanType.Kafka,
                        listenerTopics(kafkaListener),
                        easyNextAdminConfig.getTrace().getMaxDepth(),
                        easyNextAdminConfig.getTrace().getMinNodeCostMs());
            }
            Throwable failure = null;
            try {
                return joinPoint.proceed();
            } catch (Throwable ex) {
                failure = ex;
                throw ex;
            } finally {
                if (traceEnabled) {
                    TraceContext.stopRoot(thresholdMs, "Kafka consumer trace tree", failure);
                } else {
                    TraceContext.clear();
                }
            }
        }
    }

    private static String resolveTraceId(Object[] args) {
        return resolveHeader(args, EasyNextAdminConstants.TRACE_ID_HEADER);
    }

    private static String resolveUserId(Object[] args) {
        return resolveHeader(args, EasyNextAdminConstants.USER_ID);
    }

    private static String resolveHeader(Object[] args, String headerName) {
        for (Object arg : args) {
            if (arg instanceof ConsumerRecord<?, ?> record) {
                Header header = record.headers().lastHeader(headerName);
                if (header != null && header.value() != null) {
                    String value = new String(header.value(), StandardCharsets.UTF_8);
                    if (StringUtils.hasText(value)) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    private static String listenerTopics(KafkaListener kafkaListener) {
        if (kafkaListener.topics().length > 0) {
            return String.join(",", kafkaListener.topics());
        }
        if (StringUtils.hasText(kafkaListener.topicPattern())) {
            return kafkaListener.topicPattern();
        }
        return "-";
    }
}
