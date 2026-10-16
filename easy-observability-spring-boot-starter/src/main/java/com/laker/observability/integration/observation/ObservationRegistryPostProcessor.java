package com.laker.observability.integration.observation;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

/** Registers the trace bridge with every Micrometer registry created by the host application. */
public final class ObservationRegistryPostProcessor implements BeanPostProcessor {
    private final ObjectProvider<ObservationTraceHandler> handlerProvider;

    public ObservationRegistryPostProcessor(ObjectProvider<ObservationTraceHandler> handlerProvider) {
        this.handlerProvider = handlerProvider;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof ObservationRegistry registry) {
            ObservationTraceHandler handler = handlerProvider.getIfAvailable();
            if (handler != null) registry.observationConfig().observationHandler(handler);
        }
        return bean;
    }
}
