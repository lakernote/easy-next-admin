package com.laker.observability.integration.redis;

import com.laker.observability.config.EasyObservabilityProperties;
import com.laker.observability.metrics.OperationRecorder;
import com.laker.observability.trace.OperationRecord;
import com.laker.observability.trace.SpanKind;
import com.laker.observability.trace.ThreadResourceSnapshot;
import com.laker.observability.trace.TraceContext;
import com.laker.observability.trace.TraceHistory;
import com.laker.observability.trace.TraceArchive;
import com.laker.observability.trace.TraceIdContext;
import org.springframework.beans.BeansException;
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
    private final OperationRecorder recorder;
    private final TraceHistory history;
    private final TraceArchive archive;
    private final EasyObservabilityProperties properties;

    public RedissonTraceConnectionFactoryPostProcessor(OperationRecorder recorder, TraceHistory history,
                                                        EasyObservabilityProperties properties) {
        this(recorder, history, null, properties);
    }

    public RedissonTraceConnectionFactoryPostProcessor(OperationRecorder recorder, TraceHistory history,
                                                        TraceArchive archive, EasyObservabilityProperties properties) {
        this.recorder = recorder;
        this.history = history;
        this.archive = archive;
        this.properties = properties;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!(bean instanceof RedisConnectionFactory factory)
                || !ClassUtils.getUserClass(bean).getName().startsWith("org.redisson.spring.data.connection.RedissonConnectionFactory")) {
            return bean;
        }
        Set<Class<?>> interfaces = publicInterfaces(bean.getClass());
        interfaces.add(RedisConnectionFactory.class);
        return Proxy.newProxyInstance(bean.getClass().getClassLoader(), interfaces.toArray(Class<?>[]::new),
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) return invokeTarget(method, factory, args);
                    Object result = invokeTarget(method, factory, args);
                    return result instanceof RedisConnection connection ? wrap(connection) : result;
                });
    }

    private RedisConnection wrap(RedisConnection connection) {
        Set<Class<?>> interfaces = publicInterfaces(connection.getClass());
        interfaces.add(RedisConnection.class);
        return (RedisConnection) Proxy.newProxyInstance(connection.getClass().getClassLoader(),
                interfaces.toArray(Class<?>[]::new), new ConnectionHandler(connection));
    }

    private final class ConnectionHandler implements InvocationHandler {
        private final RedisConnection delegate;
        private ConnectionHandler(RedisConnection delegate) { this.delegate = delegate; }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class || !isCommand(method)) return invokeTarget(method, delegate, args);
            boolean root = !TraceContext.active();
            boolean generatedId = !TraceIdContext.isValidTraceId(TraceIdContext.currentTraceId());
            String traceId = TraceIdContext.getOrCreateTraceId();
            String name = method.getName();
            long threadId = Thread.currentThread().getId();
            long startedNanos = System.nanoTime();
            ThreadResourceSnapshot before = ThreadResourceSnapshot.capture(threadId,
                    properties.getTrace().isResourceSamplingEnabled());
            if (root) TraceContext.startRoot("Redis " + name, SpanKind.REDIS, name, 8, 0);
            else TraceContext.addSpan("Redis " + name, SpanKind.REDIS, name);
            Throwable failure = null;
            try {
                return invokeTarget(method, delegate, args);
            } catch (Throwable error) {
                failure = error;
                throw error;
            } finally {
                finish(name, traceId, root, generatedId, threadId, startedNanos, before, failure);
            }
        }
    }

    private void finish(String name, String traceId, boolean root, boolean generatedId, long threadId,
                        long startedNanos, ThreadResourceSnapshot before, Throwable failure) {
        long durationMs = Math.max(0, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
        ThreadResourceSnapshot after = ThreadResourceSnapshot.capture(threadId,
                properties.getTrace().isResourceSamplingEnabled());
        long threshold = properties.getTrace().getRedisSlowThresholdMs();
        boolean slow = threshold > 0 && durationMs >= threshold;
        try {
            TraceContext.stopSpan();
            if (slow) TraceContext.markSlow("SLOW_REDIS");
            if (failure != null) TraceContext.markError("REDIS_ERROR", failure);
            recorder.record(new OperationRecord("REDIS", traceId, name, Thread.currentThread().getName(), threadId,
                    failure == null ? "success" : "failure", durationMs, after.cpuTimeMsSince(before),
                    after.allocatedBytesSince(before), after.blockedCountSince(before),
                    after.blockedTimeMsSince(before), after.waitedCountSince(before),
                    after.waitedTimeMsSince(before), "Redis command", "", "", slow, failure != null,
                    slow ? List.of("SLOW_REDIS") : List.of(), failure == null ? "" : failure.getClass().getName(),
                    "", Map.of(), Instant.now()));
            if (root) TraceContext.stopRoot(threshold, "Redis command", failure, completion -> {
                boolean traceSlow = slow || completion.slow();
                boolean traceError = failure != null || completion.error();
                history.add(traceId, "Redis " + name, name, "REDIS", failure == null ? 200 : 500,
                        durationMs, traceSlow, traceError, completion.tree());
                if (archive != null && (traceSlow || traceError)) {
                    try { archive.record(history.find(traceId)); }
                    catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) {
            if (root) TraceContext.clear();
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
