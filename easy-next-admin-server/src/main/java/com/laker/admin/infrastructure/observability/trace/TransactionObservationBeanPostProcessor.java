package com.laker.admin.infrastructure.observability.trace;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;

import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "easy.features", name = "monitor", havingValue = "true", matchIfMissing = true)
public class TransactionObservationBeanPostProcessor implements BeanPostProcessor, BeanFactoryAware, PriorityOrdered {
    private BeanFactory beanFactory;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof AbstractPlatformTransactionManager manager) {
            List<org.springframework.transaction.TransactionExecutionListener> listeners =
                    new ArrayList<>(manager.getTransactionExecutionListeners());
            if (listeners.stream().noneMatch(TransactionObservationListener.class::isInstance)) {
                listeners.add(beanFactory.getBean(TransactionObservationListener.class));
                manager.setTransactionExecutionListeners(listeners);
            }
        }
        return bean;
    }

    @Override
    public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }
}
