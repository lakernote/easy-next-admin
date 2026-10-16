package com.laker.observability.autoconfigure;

import com.laker.observability.config.EasyObservabilityProperties;
import com.laker.observability.integration.transaction.TransactionTraceBeanPostProcessor;
import com.laker.observability.integration.transaction.TransactionTraceListener;
import com.laker.observability.metrics.OperationRecorder;
import com.laker.observability.trace.TraceHistory;
import com.laker.observability.trace.TraceArchive;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.TransactionExecutionListener;

/** Installs transaction tracing through Spring's transaction execution listener extension point. */
@AutoConfiguration(after = ObservabilityAutoConfiguration.class)
@ConditionalOnClass(TransactionExecutionListener.class)
@ConditionalOnProperty(prefix = "easy.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "easy.observability.transaction", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TransactionTraceAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    TransactionTraceListener transactionTraceListener(OperationRecorder recorder, TraceHistory history,
                                                       TraceArchive archive, MeterRegistry meterRegistry,
                                                       EasyObservabilityProperties properties) {
        return new TransactionTraceListener(recorder, history, archive, meterRegistry, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    static TransactionTraceBeanPostProcessor transactionTraceBeanPostProcessor(
            ObjectProvider<TransactionTraceListener> listenerProvider) {
        return new TransactionTraceBeanPostProcessor(listenerProvider);
    }
}
