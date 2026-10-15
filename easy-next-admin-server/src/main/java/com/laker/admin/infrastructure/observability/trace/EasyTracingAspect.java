package com.laker.admin.infrastructure.observability.trace;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Aspect
@Component
@ConditionalOnProperty(prefix = "easy.features", name = "monitor", havingValue = "true", matchIfMissing = true)
public class EasyTracingAspect {
    private final EasyNextAdminConfig easyNextAdminConfig;
    /** Annotation metadata and default span names are immutable, so resolve them only on the first method call. */
    private final ClassValue<ConcurrentMap<Method, TraceDescriptor>> traceDescriptors = new ClassValue<>() {
        @Override
        protected ConcurrentMap<Method, TraceDescriptor> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

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
        TraceDescriptor descriptor;
        try {
            Method signatureMethod = ((MethodSignature) joinPoint.getSignature()).getMethod();
            Class<?> targetClass = joinPoint.getTarget() == null
                    ? signatureMethod.getDeclaringClass()
                    : ClassUtils.getUserClass(joinPoint.getTarget());
            descriptor = descriptor(signatureMethod, targetClass);
        } catch (Throwable monitoringFailure) {
            TraceContext.clear();
            return joinPoint.proceed();
        }
        boolean spanStarted = false;
        try {
            TraceContext.addSpan(descriptor.spanName(), descriptor.spanType(), descriptor.tag());
            spanStarted = true;
        } catch (Throwable monitoringFailure) {
            TraceContext.clear();
        }
        try {
            return joinPoint.proceed();
        } finally {
            if (spanStarted) {
                try {
                    TraceContext.stopSpan();
                } catch (Throwable monitoringFailure) {
                    TraceContext.clear();
                }
            }
        }
    }

    private TraceDescriptor descriptor(Method signatureMethod, Class<?> targetClass) {
        ConcurrentMap<Method, TraceDescriptor> descriptors = traceDescriptors.get(targetClass);
        TraceDescriptor cached = descriptors.get(signatureMethod);
        if (cached != null) return cached;
        TraceDescriptor created = describe(signatureMethod, targetClass);
        TraceDescriptor raced = descriptors.putIfAbsent(signatureMethod, created);
        return raced == null ? created : raced;
    }

    private static TraceDescriptor describe(Method signatureMethod, Class<?> targetClass) {
        Method specificMethod = ClassUtils.getMostSpecificMethod(signatureMethod, targetClass);
        EasyTrace easyTrace = resolveTraceAnnotation(specificMethod, signatureMethod, targetClass);
        return new TraceDescriptor(
                resolveSpanName(easyTrace, specificMethod, targetClass),
                resolveSpanType(easyTrace),
                resolveSpanTag(easyTrace));
    }

    private static EasyTrace resolveTraceAnnotation(Method specificMethod,
                                                    Method signatureMethod,
                                                    Class<?> targetClass) {
        EasyTrace methodTrace = AnnotationUtils.findAnnotation(specificMethod, EasyTrace.class);
        if (methodTrace != null) {
            return methodTrace;
        }
        methodTrace = AnnotationUtils.findAnnotation(signatureMethod, EasyTrace.class);
        if (methodTrace != null) {
            return methodTrace;
        }
        EasyTrace targetTrace = AnnotationUtils.findAnnotation(targetClass, EasyTrace.class);
        return targetTrace != null
                ? targetTrace
                : AnnotationUtils.findAnnotation(signatureMethod.getDeclaringClass(), EasyTrace.class);
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

    private record TraceDescriptor(String spanName, SpanType spanType, String tag) { }
}
