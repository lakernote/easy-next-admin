package com.laker.observability.integration.transaction;

import com.laker.observability.config.EasyObservabilityProperties;
import com.laker.observability.metrics.OperationRecorder;
import com.laker.observability.trace.OperationRecord;
import com.laker.observability.trace.TraceHistory;
import com.laker.observability.trace.TraceIdContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionExecution;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionTraceListenerTest {
    @AfterEach
    void clearContext() {
        TraceIdContext.clear();
    }

    @Test
    void recordsLongTransactionAndActiveGaugeAtSpringTransactionBoundary() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        EasyObservabilityProperties properties = new EasyObservabilityProperties();
        properties.getTransaction().setSlowThresholdMs(1_000);
        AtomicLong clock = new AtomicLong();
        TraceHistory history = new TraceHistory(10, 10);
        OperationRecorder recorder = new OperationRecorder(meters, history, properties);
        TransactionTraceListener listener = new TransactionTraceListener(recorder, meters, properties, clock::get);
        TransactionExecution execution = execution("OrderService#create", false);

        listener.beforeBegin(execution);
        clock.set(10_000_000_000L);
        listener.afterBegin(execution, null);
        assertThat(meters.get("easy.transactions.active").gauge().value()).isEqualTo(1);
        clock.addAndGet(1_200_000_000L);
        listener.afterCommit(execution, null);

        assertThat(history.recentOperations(10, "slow")).singleElement()
                .satisfies(operation -> {
                    assertThat(operation.type()).isEqualTo("TRANSACTION");
                    assertThat(operation.durationMs()).isEqualTo(1_200);
                    assertThat(operation.slow()).isTrue();
                    assertThat(operation.reasons()).contains("LONG_TRANSACTION");
                });
        assertThat(meters.get("easy.transactions").tag("result", "success").tag("read_only", "false")
                .timer().count()).isEqualTo(1);
        assertThat(meters.get("easy.transactions.active").gauge().value()).isZero();
        assertThat(history.recent(10)).isEmpty();
    }

    @Test
    void attachesTransactionAsSingleChildOfExistingHttpTrace() {
        OperationRecorder recorder = mock(OperationRecorder.class);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        EasyObservabilityProperties properties = new EasyObservabilityProperties();
        AtomicLong clock = new AtomicLong();
        TransactionTraceListener listener = new TransactionTraceListener(recorder, meters, properties, clock::get);
        TransactionExecution execution = execution("OrderService#create", true);
        com.laker.observability.trace.TraceContext.startRoot("POST /orders",
                com.laker.observability.trace.SpanKind.HTTP, "", 8, 0);

        listener.beforeBegin(execution);
        listener.afterBegin(execution, null);
        clock.addAndGet(20_000_000L);
        listener.afterCommit(execution, null);
        java.util.concurrent.atomic.AtomicReference<com.laker.observability.trace.TraceContext.Completion> completion =
                new java.util.concurrent.atomic.AtomicReference<>();
        com.laker.observability.trace.TraceContext.markSlow("TEST_CAPTURE");
        com.laker.observability.trace.TraceContext.stopRoot(0, "test", null, completion::set);

        assertThat(completion.get().tree()).contains("OrderService#create");
        assertThat(completion.get().tree().split("OrderService#create", -1)).hasSize(2);
        verify(recorder).record(any(OperationRecord.class));
    }

    private static TransactionExecution execution(String name, boolean readOnly) {
        TransactionExecution execution = mock(TransactionExecution.class);
        when(execution.getTransactionName()).thenReturn(name);
        when(execution.isReadOnly()).thenReturn(readOnly);
        return execution;
    }
}
