package com.laker.observability.integration.executor;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/** Binds Micrometer's standard pool gauges to Spring executor beans after initialization. */
public final class ExecutorMetricsBeanPostProcessor implements BeanPostProcessor {
    private final ObjectProvider<MeterRegistry> registryProvider;
    private final Set<ExecutorService> bound = Collections.synchronizedSet(
            Collections.newSetFromMap(new IdentityHashMap<>()));

    public ExecutorMetricsBeanPostProcessor(ObjectProvider<MeterRegistry> registryProvider) {
        this.registryProvider = registryProvider;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        try {
            ExecutorService executor = null;
            if (bean instanceof ThreadPoolTaskExecutor taskExecutor) executor = taskExecutor.getThreadPoolExecutor();
            else if (bean instanceof ThreadPoolTaskScheduler taskScheduler) executor = taskScheduler.getScheduledThreadPoolExecutor();
            else if (bean instanceof ExecutorService service) executor = service;
            if (executor != null && bound.add(executor)) {
                new ExecutorServiceMetrics(executor, beanName, java.util.List.of()).bindTo(registryProvider.getObject());
            }
        } catch (Throwable ignored) {
            // Metrics binding must remain fail-open for custom wrappers and pre-bound Boot executors.
        }
        return bean;
    }
}
