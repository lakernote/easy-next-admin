package com.laker.observability.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import org.springframework.beans.factory.SmartInitializingSingleton;

/** Binds common JVM and CPU metrics when the host has not already registered them. */
public final class RuntimeMetricsBinder implements SmartInitializingSingleton, AutoCloseable {
    private final MeterRegistry registry;
    private JvmGcMetrics gcMetrics;

    public RuntimeMetricsBinder(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (registry.find("jvm.memory.used").meter() == null) new JvmMemoryMetrics().bindTo(registry);
        if (registry.find("jvm.gc.pause").meter() == null) {
            gcMetrics = new JvmGcMetrics();
            gcMetrics.bindTo(registry);
        }
        if (registry.find("jvm.threads.live").meter() == null) new JvmThreadMetrics().bindTo(registry);
        if (registry.find("jvm.classes.loaded").meter() == null) new ClassLoaderMetrics().bindTo(registry);
        if (registry.find("system.cpu.usage").meter() == null
                || registry.find("process.cpu.usage").meter() == null) {
            new ProcessorMetrics().bindTo(registry);
        }
    }

    @Override
    public void close() {
        if (gcMetrics != null) gcMetrics.close();
    }
}
