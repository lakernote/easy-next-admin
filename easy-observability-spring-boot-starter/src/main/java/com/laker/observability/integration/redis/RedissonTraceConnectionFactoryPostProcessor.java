package com.laker.observability.integration.redis;

import com.laker.observability.config.EasyObservabilityProperties;
import com.laker.observability.metrics.OperationRecorder;
import com.laker.observability.trace.OperationRecord;
import com.laker.observability.trace.SpanKind;
import com.laker.observability.trace.ThreadResourceSnapshot;
import com.laker.observability.trace.TraceContext;
import com.laker.observability.trace.TraceIdContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.util.ClassUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Uses Spring Data's connection extension point for the Redisson community client. */
public final class RedissonTraceConnectionFactoryPostProcessor implements BeanPostProcessor {
    private final ObjectProvider<OperationRecorder> recorderProvider;
    private final ObjectProvider<EasyObservabilityProperties> propertiesProvider;

    public RedissonTraceConnectionFactoryPostProcessor(ObjectProvider<OperationRecorder> recorderProvider,
                                                        ObjectProvider<EasyObservabilityProperties> propertiesProvider) {
        this.recorderProvider = recorderProvider;
        this.propertiesProvider = propertiesProvider;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!(bean instanceof RedisConnectionFactory factory)
                || !ClassUtils.getUserClass(bean).getName().startsWith("org.redisson.spring.data.connection.RedissonConnectionFactory")) {
            return bean;
        }
        OperationRecorder recorder = recorderProvider.getIfAvailable();
        EasyObservabilityProperties properties = propertiesProvider.getIfAvailable();
        if (recorder == null || properties == null) return bean;
        Set<Class<?>> interfaces = publicInterfaces(bean.getClass());
        interfaces.add(RedisConnectionFactory.class);
        return Proxy.newProxyInstance(bean.getClass().getClassLoader(), interfaces.toArray(Class<?>[]::new),
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) return invokeTarget(method, factory, args);
                    Object result = invokeTarget(method, factory, args);
                    return result instanceof RedisConnection connection ? wrap(connection, recorder, properties) : result;
                });
    }

    private RedisConnection wrap(RedisConnection connection, OperationRecorder recorder,
                                 EasyObservabilityProperties properties) {
        Set<Class<?>> interfaces = publicInterfaces(connection.getClass());
        interfaces.add(RedisConnection.class);
        return (RedisConnection) Proxy.newProxyInstance(connection.getClass().getClassLoader(),
                interfaces.toArray(Class<?>[]::new), new ConnectionHandler(connection, recorder, properties));
    }

    private final class ConnectionHandler implements InvocationHandler {
        private final RedisConnection delegate;
        private final OperationRecorder recorder;
        private final EasyObservabilityProperties properties;

        private ConnectionHandler(RedisConnection delegate, OperationRecorder recorder,
                                  EasyObservabilityProperties properties) {
            this.delegate = delegate;
            this.recorder = recorder;
            this.properties = properties;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class || !isCommand(method)) return invokeTarget(method, delegate, args);
            // Redis is always a dependency operation. Only an inbound HTTP, job, or Kafka
            // consumer observation may create a trace root.
            boolean generatedId = !TraceIdContext.isValidTraceId(TraceIdContext.currentTraceId());
            String traceId = TraceIdContext.getOrCreateTraceId();
            String name = method.getName();
            long threadId = Thread.currentThread().getId();
            long startedNanos = System.nanoTime();
            ThreadResourceSnapshot before = ThreadResourceSnapshot.capture(threadId,
                    properties.getTrace().isResourceSamplingEnabled());
            TraceContext.DetachedSpan span = TraceContext.startDetachedSpan("Redis " + name, SpanKind.REDIS, name);
            Throwable failure = null;
            try {
                return invokeTarget(method, delegate, args);
            } catch (Throwable error) {
                failure = error;
                throw error;
            } finally {
                finish(name, traceId, generatedId, threadId, startedNanos, before, span, failure,
                        recorder, properties);
            }
        }
    }

    private void finish(String name, String traceId, boolean generatedId, long threadId,
                        long startedNanos, ThreadResourceSnapshot before, TraceContext.DetachedSpan span,
                        Throwable failure, OperationRecorder recorder, EasyObservabilityProperties properties) {
        long durationMs = Math.max(0, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
        ThreadResourceSnapshot after = ThreadResourceSnapshot.capture(threadId,
                properties.getTrace().isResourceSamplingEnabled());
        long threshold = properties.getTrace().getRedisSlowThresholdMs();
        boolean slow = threshold > 0 && durationMs >= threshold;
        try {
            span.run();
            if (slow) TraceContext.markSlow("SLOW_REDIS");
            if (failure != null) TraceContext.markError("REDIS_ERROR", failure);
            recorder.record(new OperationRecord("REDIS", traceId, name, Thread.currentThread().getName(), threadId,
                    failure == null ? "success" : "failure", durationMs, after.cpuTimeMsSince(before),
                    after.allocatedBytesSince(before), after.blockedCountSince(before),
                    after.blockedTimeMsSince(before), after.waitedCountSince(before),
                    after.waitedTimeMsSince(before), "Redis command", "", "", slow, failure != null,
                    slow ? List.of("SLOW_REDIS") : List.of(), failure == null ? "" : failure.getClass().getName(),
                    "", Map.of(), Instant.now(), span.spanId(), span.parentSpanId()));
        } catch (Throwable ignored) {
            // Instrumentation must never change the application's Redis result.
        } finally {
            if (generatedId) TraceIdContext.clear();
        }
    }

    private static boolean isCommand(Method method) {
        return switch (method.getName()) {
            case "close", "isClosed", "getNativeConnection", "getSubscription", "openPipeline", "closePipeline",
                    "isQueueing", "open", "isPipelined", "multi", "exec", "discard", "watch", "unwatch" -> false;
            default -> true;
        };
    }

    private static Object invokeTarget(Method method, Object target, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException error) { throw error.getCause(); }
    }

    private static Set<Class<?>> publicInterfaces(Class<?> type) {
        Set<Class<?>> interfaces = new LinkedHashSet<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            ClassUtils.getAllInterfacesForClassAsSet(current).stream()
                    .filter(candidate -> java.lang.reflect.Modifier.isPublic(candidate.getModifiers()))
                    .forEach(interfaces::add);
        }
        return interfaces;
    }
}
