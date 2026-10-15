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

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Automatically adds module service calls to an existing trace tree.
 *
 * <p>The HTTP, job, or message entry point owns the root trace. This aspect only enriches an
 * already active trace, so background maintenance and calls outside a monitored entry do not
 * create unrelated trace fragments.</p>
 */
@Aspect
@Component
@ConditionalOnProperty(prefix = "easy.features", name = "monitor", havingValue = "true", matchIfMissing = true)
public class AutomaticServiceTracingAspect {
    private final EasyNextAdminConfig easyNextAdminConfig;
    /** Service classes and methods are finite; cache reflection and annotation lookup outside the call hot path. */
    private final ClassValue<ConcurrentMap<Method, TraceDescriptor>> traceDescriptors = new ClassValue<>() {
        @Override
        protected ConcurrentMap<Method, TraceDescriptor> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    public AutomaticServiceTracingAspect(EasyNextAdminConfig easyNextAdminConfig) {
        this.easyNextAdminConfig = easyNextAdminConfig;
    }

    @Around("execution(public * com.laker.admin.module..*.*(..)) && " +
            "@within(org.springframework.stereotype.Service)")
    public Object traceServiceCall(ProceedingJoinPoint joinPoint) throws Throwable {
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
        if (!descriptor.automatic()) {
            return joinPoint.proceed();
        }

        boolean spanStarted = false;
        try {
            TraceContext.addSpan(descriptor.spanName(), SpanType.Service);
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
        boolean automatic = !hasAnnotation(specificMethod, signatureMethod, targetClass, EasyTrace.class)
                && !hasAnnotation(specificMethod, signatureMethod, targetClass, EasyIgnoreTrace.class);
        return new TraceDescriptor(automatic,
                automatic ? targetClass.getSimpleName() + "#" + specificMethod.getName() : "");
    }

    private static boolean hasAnnotation(Method specificMethod,
                                         Method signatureMethod,
                                         Class<?> targetClass,
                                         Class<? extends java.lang.annotation.Annotation> annotationType) {
        return AnnotationUtils.findAnnotation(specificMethod, annotationType) != null
                || AnnotationUtils.findAnnotation(signatureMethod, annotationType) != null
                || AnnotationUtils.findAnnotation(signatureMethod.getDeclaringClass(), annotationType) != null
                || AnnotationUtils.findAnnotation(targetClass, annotationType) != null;
    }

    private record TraceDescriptor(boolean automatic, String spanName) { }
}
