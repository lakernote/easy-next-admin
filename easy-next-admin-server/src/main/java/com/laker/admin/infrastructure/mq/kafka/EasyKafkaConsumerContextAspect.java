package com.laker.admin.infrastructure.mq.kafka;

import com.laker.admin.common.constant.EasyNextAdminConstants;
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
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;

/** Propagates application identity alongside the Trace ID restored by Meerkat. */
@Aspect
@Component
@ConditionalOnClass(KafkaListener.class)
@ConditionalOnProperty(prefix = "easy.features", name = "kafka", havingValue = "true")
public class EasyKafkaConsumerContextAspect {

    @Around(value = "@annotation(kafkaListener)", argNames = "joinPoint,kafkaListener")
    public Object propagateMessageContext(ProceedingJoinPoint joinPoint, KafkaListener kafkaListener) throws Throwable {
        Object[] args = joinPoint.getArgs();
        String previousUserId = MDC.get(EasyNextAdminConstants.USER_ID);
        try {
            String userId = header(args, EasyNextAdminConstants.USER_ID);
            if (StringUtils.hasText(userId)) MDC.put(EasyNextAdminConstants.USER_ID, userId);
            else MDC.remove(EasyNextAdminConstants.USER_ID);
            return joinPoint.proceed();
        } finally {
            if (StringUtils.hasText(previousUserId)) MDC.put(EasyNextAdminConstants.USER_ID, previousUserId);
            else MDC.remove(EasyNextAdminConstants.USER_ID);
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
