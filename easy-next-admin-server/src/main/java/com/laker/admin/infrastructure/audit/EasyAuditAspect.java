package com.laker.admin.infrastructure.audit;

import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.security.exception.EasyAuthException;
import com.laker.admin.infrastructure.security.exception.EasyForbiddenException;
import com.laker.admin.infrastructure.security.masking.EasySensitiveDataMasker;
import com.laker.admin.module.system.dto.auth.AuthLoginRequest;
import com.laker.admin.module.audit.entity.AuditDataChangeLog;
import com.laker.admin.module.audit.entity.AuditOperationLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@Aspect
@Component
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class EasyAuditAspect {
    private static final Set<String> MUTATION_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final AuditLogCollector auditLogCollector;
    private final EasySensitiveDataMasker masker;
    private final AuditRequestPayloadFormatter requestPayloadFormatter;
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    public EasyAuditAspect(AuditLogCollector auditLogCollector,
                           EasySensitiveDataMasker masker,
                           AuditRequestPayloadFormatter requestPayloadFormatter) {
        this.auditLogCollector = auditLogCollector;
        this.masker = masker;
        this.requestPayloadFormatter = requestPayloadFormatter;
    }

    @Pointcut("execution(public * com.laker.admin..controller..*(..))")
    public void controllerMethod() {
    }

    @Pointcut("@annotation(com.laker.admin.infrastructure.audit.EasyAudit) " +
            "|| @within(com.laker.admin.infrastructure.audit.EasyAudit)")
    public void auditAnnotated() {
    }

    @Around("controllerMethod() || auditAnnotated()")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodContext methodContext = methodContext(joinPoint);
        EasyAudit audit = auditAnnotation(methodContext);
        boolean controller = isRestController(methodContext.targetClass());
        boolean shouldRecordOperation = shouldRecordOperation(audit);
        Instant start = Instant.now();
        try {
            Object result = joinPoint.proceed();
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            if (shouldRecordOperation) {
                auditLogCollector.recordOperation(operationLog(methodContext, audit, result, null, durationMs));
            }
            if (audit != null && audit.dataChange()) {
                auditLogCollector.recordDataChange(dataChangeLog(methodContext, audit, result, null));
            }
            return result;
        } catch (Throwable throwable) {
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            if (shouldRecordOperation) {
                auditLogCollector.recordOperation(operationLog(methodContext, audit, null, throwable, durationMs));
            }
            if ((controller || audit != null) && shouldRecordError(throwable)) {
                auditLogCollector.recordError(throwable);
            }
            throw throwable;
        }
    }

    private AuditOperationLog operationLog(MethodContext methodContext,
                                           EasyAudit audit,
                                           Object result,
                                           Throwable throwable,
                                           long durationMs) {
        AuditOperationLog operationLog = new AuditOperationLog();
        operationLog.setModule(resolveModule(methodContext, audit));
        operationLog.setAction(resolveAction(methodContext, audit));
        operationLog.setRequestParams(requestPayloadFormatter.format(methodContext.method(), methodContext.args()));
        operationLog.setResponseStatus(responseStatus(result, throwable));
        operationLog.setErrorMessage(throwable == null ? null : throwable.getMessage());
        operationLog.setDurationMs(safeDuration(durationMs));
        operationLog.setOperatorName(resolveOperatorName(methodContext));
        return operationLog;
    }

    private AuditDataChangeLog dataChangeLog(MethodContext methodContext,
                                             EasyAudit audit,
                                             Object result,
                                             Throwable throwable) {
        AuditDataChangeLog dataChangeLog = new AuditDataChangeLog();
        dataChangeLog.setBizType(resolveExpressionAsString(audit.bizType(), methodContext, result, throwable));
        dataChangeLog.setBizId(resolveExpressionAsString(audit.bizId(), methodContext, result, throwable));
        dataChangeLog.setTableName(resolveExpressionAsString(audit.tableName(), methodContext, result, throwable));
        dataChangeLog.setChangeType(resolveExpressionAsString(audit.changeType(), methodContext, result, throwable));
        dataChangeLog.setBeforeJson(resolveExpressionAsJson(audit.before(), methodContext, result, throwable));
        dataChangeLog.setAfterJson(resolveExpressionAsJson(audit.after(), methodContext, result, throwable));
        dataChangeLog.setChangedFields(resolveExpressionAsString(audit.changedFields(), methodContext, result, throwable));
        return dataChangeLog;
    }

    private MethodContext methodContext(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?> targetClass = joinPoint.getTarget() == null
                ? signature.getDeclaringType()
                : AopUtils.getTargetClass(joinPoint.getTarget());
        Method specificMethod = AopUtils.getMostSpecificMethod(signature.getMethod(), targetClass);
        return new MethodContext(targetClass, specificMethod, joinPoint.getTarget(), joinPoint.getArgs());
    }

    private EasyAudit auditAnnotation(MethodContext methodContext) {
        EasyAudit methodAudit = AnnotatedElementUtils.findMergedAnnotation(methodContext.method(), EasyAudit.class);
        if (methodAudit != null) {
            return methodAudit;
        }
        return AnnotatedElementUtils.findMergedAnnotation(methodContext.targetClass(), EasyAudit.class);
    }

    private boolean shouldRecordOperation(EasyAudit audit) {
        if (audit != null) {
            return audit.operation();
        }
        String method = com.laker.admin.infrastructure.web.context.EasyRequestContext.currentRequestMethod();
        return MUTATION_METHODS.contains(method);
    }

    private boolean isRestController(Class<?> targetClass) {
        return AnnotatedElementUtils.hasAnnotation(targetClass, RestController.class);
    }

    private String resolveModule(MethodContext methodContext, EasyAudit audit) {
        if (audit != null && StringUtils.hasText(audit.module())) {
            return audit.module();
        }
        Tag tag = AnnotatedElementUtils.findMergedAnnotation(methodContext.targetClass(), Tag.class);
        if (tag != null && StringUtils.hasText(tag.name())) {
            return tag.name();
        }
        return methodContext.targetClass().getSimpleName().replace("Controller", "");
    }

    private String resolveAction(MethodContext methodContext, EasyAudit audit) {
        if (audit != null && StringUtils.hasText(audit.action())) {
            return audit.action();
        }
        Operation operation = AnnotatedElementUtils.findMergedAnnotation(methodContext.method(), Operation.class);
        if (operation != null && StringUtils.hasText(operation.summary())) {
            return operation.summary();
        }
        return methodContext.method().getName();
    }

    private String responseStatus(Object result, Throwable throwable) {
        if (throwable != null) {
            return isExpectedFailure(throwable) ? "FAIL" : "ERROR";
        }
        if (result instanceof Response<?> response) {
            return response.getCode() == 0 ? "SUCCESS" : "FAIL";
        }
        return "SUCCESS";
    }

    private boolean shouldRecordError(Throwable throwable) {
        return !isExpectedFailure(throwable);
    }

    private boolean isExpectedFailure(Throwable throwable) {
        return throwable instanceof BusinessException
                || throwable instanceof EasyAuthException
                || throwable instanceof EasyForbiddenException;
    }

    private String resolveOperatorName(MethodContext methodContext) {
        for (Object arg : methodContext.args()) {
            if (arg instanceof AuthLoginRequest loginRequest && StringUtils.hasText(loginRequest.getUsername())) {
                return loginRequest.getUsername();
            }
        }
        return null;
    }

    private String resolveExpressionAsString(String expression,
                                             MethodContext methodContext,
                                             Object result,
                                             Throwable throwable) {
        if (!StringUtils.hasText(expression)) {
            return null;
        }
        Object value = isExpression(expression)
                ? evaluate(expression, methodContext, result, throwable)
                : expression;
        return value == null ? null : String.valueOf(value);
    }

    private String resolveExpressionAsJson(String expression,
                                           MethodContext methodContext,
                                           Object result,
                                           Throwable throwable) {
        if (!StringUtils.hasText(expression)) {
            return null;
        }
        Object value = isExpression(expression)
                ? evaluate(expression, methodContext, result, throwable)
                : expression;
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return masker.sanitizeJsonText(text);
        }
        return masker.toSanitizedJson(value);
    }

    private Object evaluate(String expression,
                            MethodContext methodContext,
                            Object result,
                            Throwable throwable) {
        try {
            EvaluationContext context = new MethodBasedEvaluationContext(
                    methodContext.target(),
                    methodContext.method(),
                    methodContext.args(),
                    parameterNameDiscoverer);
            context.setVariable("result", result);
            context.setVariable("exception", throwable);
            return expressionParser.parseExpression(expression).getValue(context);
        } catch (Exception e) {
            log.warn("evaluate audit expression failed, expression: {}, error: {}", expression, e.getMessage());
            return null;
        }
    }

    private boolean isExpression(String value) {
        return value.contains("#") || value.startsWith("'") || value.startsWith("T(");
    }

    private int safeDuration(long durationMs) {
        return durationMs > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) durationMs;
    }

    private record MethodContext(Class<?> targetClass, Method method, Object target, Object[] args) {
    }
}
