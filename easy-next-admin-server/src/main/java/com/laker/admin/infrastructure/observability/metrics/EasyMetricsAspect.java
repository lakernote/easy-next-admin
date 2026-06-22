package com.laker.admin.infrastructure.observability.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.laker.admin.infrastructure.audit.AuditRequestPayloadFormatter;
import com.laker.admin.infrastructure.json.EasyJsonCodec;
import com.laker.admin.infrastructure.json.EasyJsonException;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.masking.EasySensitiveDataMasker;
import com.laker.admin.infrastructure.web.context.EasyRequestContext;
import com.laker.admin.infrastructure.observability.trace.EasyTraceIdContext;
import com.laker.admin.module.audit.entity.AuditApiLog;
import com.laker.admin.module.audit.service.IAuditApiLogService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * <pre>
 * Bean的优先级设置为最高
 *  1.用于监控方法执行时间
 *  2.记录日志
 *  3.记录请求参数
 *  4.记录返回值
 *  5.记录请求ip
 *  6.记录请求uri
 *  7.记录请求客户端
 *  8.记录请求用户
 *  9.记录请求时间
 *  10.记录请求状态
 *  11.记录请求耗时
 *  12.记录请求方法
 * </pre>
 */
@Aspect
@Component
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class EasyMetricsAspect {
    private final EasyJsonCodec jsonCodec;
    private final AuditRequestPayloadFormatter requestPayloadFormatter;
    private final EasySensitiveDataMasker masker;
    private final IAuditApiLogService auditApiLogService;

    public EasyMetricsAspect(EasyJsonCodec jsonCodec,
                             AuditRequestPayloadFormatter requestPayloadFormatter,
                             EasySensitiveDataMasker masker,
                             IAuditApiLogService auditApiLogService) {
        this.jsonCodec = jsonCodec;
        this.requestPayloadFormatter = requestPayloadFormatter;
        this.masker = masker;
        this.auditApiLogService = auditApiLogService;
    }

    /**
     * <pre>
     * 定义切点
     *  "@annotation"用于匹配那些带有指定注解的方法。也就是说，当 某个方法被指定的注解标记时，该方法就会成为切入点的一部分。
     *  "@within"用于匹配那些所在类带有指定注解的所有方法。只要 类被指定的注解标记，该类中的所有方法都会成为切入点的一部分。
     *  </pre>
     */
    @Pointcut("@annotation(com.laker.admin.infrastructure.observability.metrics.EasyMetrics) " +
            "|| @within(com.laker.admin.infrastructure.observability.metrics.EasyMetrics)")
    public void withAnnotationMetrics() {
        // do nothing
    }

    @Around("withAnnotationMetrics()")
    public Object metrics(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        String name = signature.toShortString();
        Method method = specificMethod(pjp, signature);
        String requestPayload = requestPayloadFormatter.format(method, pjp.getArgs());
        Object returnValue;
        Instant start = Instant.now();
        AuditApiLog logBean = new AuditApiLog();
        logBean.setTraceId(EasyTraceIdContext.getOrCreateTraceId());
        logBean.setIp(EasyRequestContext.currentRemoteIp());
        logBean.setUri(EasyRequestContext.currentRequestUri());
        logBean.setUserId(EasySecurityContext.getUserId());
        logBean.setClient(EasyRequestContext.currentUserAgentSummary());
        logBean.setRequest(requestPayload);
        logBean.setMethod(EasyRequestContext.currentRequestMethod());
        logBean.setStatus(true);
        try {
            returnValue = pjp.proceed();
        } catch (Exception ex) {
            long costMs = Duration.between(start, Instant.now()).toMillis();
            log.info("method:{},fail,cost:{}ms,uri:{},param:{}", name, costMs, EasyRequestContext.currentRequestUri(), requestPayload);
            logBean.setCost((int) costMs);
            logBean.setCreateTime(LocalDateTime.now());
            logBean.setStatus(false);
            auditApiLogService.save(logBean);
            log.error(name, ex);
            throw ex;
        }
        String response = jsonCodec.toJson(returnValue);
        String sanitizedResponse = masker.sanitizeJsonText(response, 500);
        long costMs = Duration.between(start, Instant.now()).toMillis();
        log.debug("method:{},success,cost:{}ms,uri:{},param:{},return:{}", name, costMs, EasyRequestContext.currentRequestUri(), requestPayload, sanitizedResponse);
        logBean.setCost((int) costMs);
        logBean.setCreateTime(LocalDateTime.now());
        if (StringUtils.hasText(sanitizedResponse) && sanitizedResponse.length() <= 500) {
            logBean.setResponse(sanitizedResponse);
        }
        if (StringUtils.hasText(response) && response.trim().startsWith("{")) {
            try {
                JsonNode jsonNode = jsonCodec.readTree(response);
                if (jsonNode.isObject() && jsonNode.has("code")) {
                    logBean.setStatus(jsonNode.path("code").asInt(-1) == 0);
                }
            } catch (EasyJsonException e) {
                log.debug("响应体不是可解析 JSON 对象，跳过 code 状态提取");
            }
        }
        try {
            auditApiLogService.save(logBean);
        } catch (Exception e) {
            log.error("保存日志失败", e);
        }
        return returnValue;
    }

    private Method specificMethod(ProceedingJoinPoint pjp, MethodSignature signature) {
        Class<?> targetClass = pjp.getTarget() == null ? signature.getDeclaringType() : AopUtils.getTargetClass(pjp.getTarget());
        return AopUtils.getMostSpecificMethod(signature.getMethod(), targetClass);
    }
}
