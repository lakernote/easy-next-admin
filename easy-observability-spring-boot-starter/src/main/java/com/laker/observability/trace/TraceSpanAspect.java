package com.laker.observability.trace;

import com.laker.observability.config.EasyObservabilityProperties;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Aspect
public class TraceSpanAspect {
    private final EasyObservabilityProperties properties;
    /** Annotation metadata and default span names are immutable, so resolve them only on the first method call. */
    private final ClassValue<ConcurrentMap<Method, TraceDescriptor>> traceDescriptors = new ClassValue<>() {
        @Override
        protected ConcurrentMap<Method, TraceDescriptor> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    public TraceSpanAspect(EasyObservabilityProperties properties) {
        this.properties = properties;
    }

    @Around("(@annotation(com.laker.observability.trace.TraceSpan) || " +
            "@within(com.laker.observability.trace.TraceSpan)) && " +
            "!@annotation(com.laker.observability.trace.NoTrace) && " +
            "!@within(com.laker.observability.trace.NoTrace)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!properties.getTrace().isEnabled() || !TraceContext.active()) {
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
            TraceContext.addSpan(descriptor.spanName(), descriptor.kind(), descriptor.tag());
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
        TraceSpan traceSpan = resolveTraceAnnotation(specificMethod, signatureMethod, targetClass);
        return new TraceDescriptor(
                resolveSpanName(traceSpan, specificMethod, targetClass),
                resolveSpanKind(traceSpan),
                resolveSpanTag(traceSpan));
    }

    private static TraceSpan resolveTraceAnnotation(Method specificMethod,
                                                    Method signatureMethod,
                                                    Class<?> targetClass) {
        TraceSpan methodTrace = AnnotationUtils.findAnnotation(specificMethod, TraceSpan.class);
        if (methodTrace != null) {
            return methodTrace;
        }
        methodTrace = AnnotationUtils.findAnnotation(signatureMethod, TraceSpan.class);
        if (methodTrace != null) {
            return methodTrace;
        }
        TraceSpan targetTrace = AnnotationUtils.findAnnotation(targetClass, TraceSpan.class);
        return targetTrace != null
                ? targetTrace
                : AnnotationUtils.findAnnotation(signatureMethod.getDeclaringClass(), TraceSpan.class);
    }

    private static String resolveSpanName(TraceSpan traceSpan, Method method, Class<?> targetClass) {
        if (traceSpan != null && StringUtils.hasText(traceSpan.name())) {
            return traceSpan.name();
        }
        return targetClass.getSimpleName() + "#" + method.getName();
    }

    private static SpanKind resolveSpanKind(TraceSpan traceSpan) {
        return traceSpan == null ? SpanKind.OTHER : traceSpan.kind();
    }

    private static String resolveSpanTag(TraceSpan traceSpan) {
        return traceSpan == null ? "" : traceSpan.tag();
    }

    private record TraceDescriptor(String spanName, SpanKind kind, String tag) { }
}
