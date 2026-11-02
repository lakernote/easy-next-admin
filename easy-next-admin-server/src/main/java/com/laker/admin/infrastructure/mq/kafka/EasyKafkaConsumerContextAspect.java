package com.laker.admin.infrastructure.mq.kafka;

import com.laker.admin.common.constant.EasyNextAdminConstants;
import io.github.lakernote.observability.trace.MdcContext;
import io.github.lakernote.observability.trace.TraceIdContext;
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

/** Propagates application identity alongside the Trace ID restored by the observability starter. */
@Aspect
@Component
@ConditionalOnClass(KafkaListener.class)
@ConditionalOnProperty(prefix = "easy.features", name = "kafka", havingValue = "true")
public class EasyKafkaConsumerContextAspect {

    @Around(value = "@annotation(kafkaListener)", argNames = "joinPoint,kafkaListener")
    public Object propagateMessageContext(ProceedingJoinPoint joinPoint, KafkaListener kafkaListener) throws Throwable {
        Object[] args = joinPoint.getArgs();
        try (MdcContext.Scope ignored = MdcContext.scope()) {
            TraceIdContext.putOrCreateTraceId(header(args, EasyNextAdminConstants.TRACE_ID_HEADER));
            MdcContext.putOrRemove(EasyNextAdminConstants.USER_ID, header(args, EasyNextAdminConstants.USER_ID));
            return joinPoint.proceed();
        }
    }

    private static String header(Object[] args, String name) {
        for (Object arg : args) {
            if (arg instanceof ConsumerRecord<?, ?> record) {
                Header header = record.headers().lastHeader(name);
                if (header != null && header.value() != null) {
                    String value = new String(header.value(), StandardCharsets.UTF_8);
                    if (StringUtils.hasText(value)) return value;
                }
            }
        }
        return null;
    }
}
