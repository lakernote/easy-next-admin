package com.laker.admin.infrastructure.observability.trace;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

@Aspect
@Component
public class EasyTracingAspect {
    private final EasyNextAdminConfig easyNextAdminConfig;

    public EasyTracingAspect(EasyNextAdminConfig easyNextAdminConfig) {
        this.easyNextAdminConfig = easyNextAdminConfig;
    }

    @Around("(@annotation(com.laker.admin.infrastructure.observability.trace.EasyTrace) || " +
            "@within(com.laker.admin.infrastructure.observability.trace.EasyTrace)) && " +
            "!@annotation(com.laker.admin.infrastructure.observability.trace.EasyIgnoreTrace) && " +
            "!@within(com.laker.admin.infrastructure.observability.trace.EasyIgnoreTrace)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!easyNextAdminConfig.getTrace().isEnabled() || !TraceContext.active()) {
            return joinPoint.proceed();
        }
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Class<?> targetClass = joinPoint.getTarget() == null ? method.getDeclaringClass() : joinPoint.getTarget().getClass();
        Method specificMethod = ClassUtils.getMostSpecificMethod(method, targetClass);
        EasyTrace easyTrace = resolveTraceAnnotation(specificMethod, targetClass);
        TraceContext.addSpan(
                resolveSpanName(easyTrace, specificMethod, targetClass),
                resolveSpanType(easyTrace),
                resolveSpanTag(easyTrace));
        try {
            return joinPoint.proceed();
        } finally {
            TraceContext.stopSpan();
        }
    }

    private static EasyTrace resolveTraceAnnotation(Method method, Class<?> targetClass) {
        EasyTrace methodTrace = AnnotationUtils.findAnnotation(method, EasyTrace.class);
        if (methodTrace != null) {
            return methodTrace;
        }
        return AnnotationUtils.findAnnotation(targetClass, EasyTrace.class);
    }

    private static String resolveSpanName(EasyTrace easyTrace, Method method, Class<?> targetClass) {
        if (easyTrace != null && StringUtils.hasText(easyTrace.value())) {
            return easyTrace.value();
        }
        return targetClass.getSimpleName() + "#" + method.getName();
    }

    private static SpanType resolveSpanType(EasyTrace easyTrace) {
        return easyTrace == null ? SpanType.Others : easyTrace.spanType();
    }

    private static String resolveSpanTag(EasyTrace easyTrace) {
        return easyTrace == null ? "" : easyTrace.tag();
    }
}
