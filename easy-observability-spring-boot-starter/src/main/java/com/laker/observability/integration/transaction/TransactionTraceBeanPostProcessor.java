package com.laker.observability.integration.transaction;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.transaction.TransactionExecutionListener;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;

import java.util.ArrayList;
import java.util.List;

/** Adds the trace listener through Spring's transaction-manager extension point. */
public final class TransactionTraceBeanPostProcessor implements BeanPostProcessor, PriorityOrdered {
    private final ObjectProvider<TransactionTraceListener> listenerProvider;

    public TransactionTraceBeanPostProcessor(ObjectProvider<TransactionTraceListener> listenerProvider) {
        this.listenerProvider = listenerProvider;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof AbstractPlatformTransactionManager manager) {
            TransactionTraceListener listener = listenerProvider.getIfAvailable();
            if (listener == null) return bean;
            List<TransactionExecutionListener> listeners = new ArrayList<>(manager.getTransactionExecutionListeners());
            if (listeners.stream().noneMatch(TransactionTraceListener.class::isInstance)) {
                listeners.add(listener);
                manager.setTransactionExecutionListeners(listeners);
            }
        }
        return bean;
    }

    @Override public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }
}
