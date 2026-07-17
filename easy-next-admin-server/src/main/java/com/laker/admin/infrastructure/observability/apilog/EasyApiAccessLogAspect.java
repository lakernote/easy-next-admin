package com.laker.admin.infrastructure.observability.apilog;

import com.fasterxml.jackson.databind.JsonNode;
import com.laker.admin.infrastructure.audit.AuditRequestPayloadFormatter;
import com.laker.admin.infrastructure.json.EasyJsonCodec;
import com.laker.admin.infrastructure.json.EasyJsonException;
import com.laker.admin.infrastructure.observability.metrics.EasyBusinessMetrics;
import com.laker.admin.infrastructure.observability.trace.EasyTraceIdContext;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.masking.EasySensitiveDataMasker;
import com.laker.admin.infrastructure.web.context.EasyRequestContext;
import com.laker.admin.module.audit.entity.AuditApiLog;
import com.laker.admin.module.audit.service.IAuditApiLogService;
import lombok.RequiredArgsConstructor;
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
 * API 访问日志切面。
 *
 * <p>职责边界：落库 audit_api_log 便于排查单次请求；耗时、成功率等聚合指标交给 EasyBusinessMetrics。</p>
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class EasyApiAccessLogAspect {
    private final EasyJsonCodec jsonCodec;
    private final AuditRequestPayloadFormatter requestPayloadFormatter;
    private final EasySensitiveDataMasker masker;
    private final IAuditApiLogService auditApiLogService;
    private final EasyBusinessMetrics businessMetrics;

    @Pointcut("@annotation(com.laker.admin.infrastructure.observability.apilog.EasyApiAccessLog) " +
            "|| @within(com.laker.admin.infrastructure.observability.apilog.EasyApiAccessLog)")
    public void withApiAccessLogAnnotation() {
        // pointcut only
    }

    @Around("withApiAccessLogAnnotation()")
    public Object recordApiAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = specificMethod(joinPoint, signature);
        String controller = controllerName(joinPoint, signature);
        String action = method.getName();
        String name = signature.toShortString();
        String requestPayload = requestPayloadFormatter.format(method, joinPoint.getArgs());
        Instant start = Instant.now();
        AuditApiLog accessLog = newAccessLog(requestPayload);

        try {
            Object returnValue = joinPoint.proceed();
            fillSuccess(accessLog, returnValue);
            long costMs = finish(accessLog, start, controller, action);
            log.debug("method:{},success,cost:{}ms,uri:{},param:{},return:{}",
                    name, costMs, EasyRequestContext.currentRequestUri(), requestPayload, accessLog.getResponse());
            saveAccessLog(accessLog);
            return returnValue;
        } catch (Throwable ex) {
            long costMs = finishFailure(accessLog, start, controller, action);
            log.info("method:{},fail,cost:{}ms,uri:{},param:{}",
                    name, costMs, EasyRequestContext.currentRequestUri(), requestPayload);
            saveAccessLog(accessLog);
            log.error(name, ex);
            throw ex;
        }
    }

    private AuditApiLog newAccessLog(String requestPayload) {
        AuditApiLog accessLog = new AuditApiLog();
        accessLog.setTraceId(EasyTraceIdContext.getOrCreateTraceId());
        accessLog.setIp(EasyRequestContext.currentRemoteIp());
        accessLog.setUri(EasyRequestContext.currentRequestUri());
        accessLog.setUserId(EasySecurityContext.getUserId());
        accessLog.setClient(EasyRequestContext.currentUserAgentSummary());
        accessLog.setRequest(requestPayload);
        accessLog.setMethod(EasyRequestContext.currentRequestMethod());
        accessLog.setStatus(true);
        return accessLog;
    }

    private void fillSuccess(AuditApiLog accessLog, Object returnValue) {
        String response = serializeResponse(returnValue);
        String sanitizedResponse = masker.sanitizeJsonText(response, 500);
        if (StringUtils.hasText(sanitizedResponse) && sanitizedResponse.length() <= 500) {
            accessLog.setResponse(sanitizedResponse);
        }
        if (StringUtils.hasText(response) && response.trim().startsWith("{")) {
            fillStatusFromResponseCode(accessLog, response);
        }
    }

    private String serializeResponse(Object returnValue) {
        try {
            return jsonCodec.toJson(returnValue);
        } catch (EasyJsonException ex) {
            log.debug("响应体序列化失败，跳过 API 访问日志响应体记录", ex);
            return "";
        }
    }

    private void fillStatusFromResponseCode(AuditApiLog accessLog, String response) {
        try {
            JsonNode jsonNode = jsonCodec.readTree(response);
            if (jsonNode.isObject() && jsonNode.has("code")) {
                accessLog.setStatus(jsonNode.path("code").asInt(-1) == 0);
            }
        } catch (EasyJsonException ex) {
            log.debug("响应体不是可解析 JSON 对象，跳过 code 状态提取");
        }
    }

    private long finish(AuditApiLog accessLog, Instant start, String controller, String action) {
        long costMs = Duration.between(start, Instant.now()).toMillis();
        accessLog.setCost((int) costMs);
        accessLog.setCreateTime(LocalDateTime.now());
        businessMetrics.recordApiAccess(controller, action, Boolean.TRUE.equals(accessLog.getStatus()), costMs);
        return costMs;
    }

    private long finishFailure(AuditApiLog accessLog, Instant start, String controller, String action) {
        accessLog.setStatus(false);
        return finish(accessLog, start, controller, action);
    }

    private void saveAccessLog(AuditApiLog accessLog) {
        try {
            auditApiLogService.save(accessLog);
        } catch (RuntimeException ex) {
            log.error("保存 API 访问日志失败", ex);
        }
    }

    private Method specificMethod(ProceedingJoinPoint joinPoint, MethodSignature signature) {
        Class<?> targetClass = joinPoint.getTarget() == null
                ? signature.getDeclaringType()
                : AopUtils.getTargetClass(joinPoint.getTarget());
        return AopUtils.getMostSpecificMethod(signature.getMethod(), targetClass);
    }

    private String controllerName(ProceedingJoinPoint joinPoint, MethodSignature signature) {
        Class<?> targetClass = joinPoint.getTarget() == null
                ? signature.getDeclaringType()
                : AopUtils.getTargetClass(joinPoint.getTarget());
        return targetClass.getSimpleName();
    }
}
