package com.laker.admin.infrastructure.observability.trace;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.infrastructure.observability.jfr.OperationObservation;
import com.laker.observability.trace.TraceContext;
import com.laker.observability.trace.TraceIdContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionExecution;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionDiagnosticListenerTest {
    @AfterEach
    void clearContexts() {
        TraceIdContext.clear();
        TraceContext.clear();
    }

    @Test
    void capturesLocalJfrScopeAndPersistsSlowTransactionSample() {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getTrace().setTransactionSlowThresholdMs(1_000);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        OperationObservation operation = mock(OperationObservation.class);
        OperationObservation.Scope scope = mock(OperationObservation.Scope.class);
        when(operation.startDiagnosticOnly("TRANSACTION", "OrderService#create", "write")).thenReturn(scope);
        AtomicLong clock = new AtomicLong();
        TransactionDiagnosticListener listener =
                new TransactionDiagnosticListener(store, meters, operation, config, clock::get);
        TransactionExecution execution = execution("OrderService#create", false);

        listener.beforeBegin(execution);
        clock.set(TimeUnit.SECONDS.toNanos(10)); // Connection acquisition is excluded from transaction duration.
        listener.afterBegin(execution, null);
        clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(1_200));
        listener.afterCommit(execution, null);

        verify(scope).beginBusinessBoundary();
        verify(scope).finish("success", null);
        verify(store).transactionStarted();
        verify(store).transactionFinished();
        verify(store).recordTransaction(anyString(), anyString(), anyBoolean(), anyLong(), any());
        assertThat(meters.find("easy.transactions").timers()).isEmpty();
    }

    @Test
    void failedBeginIsLeftForStarterTraceAndDoesNotStartLocalJfrScope() {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        OperationObservation operation = mock(OperationObservation.class);
        TransactionDiagnosticListener listener =
                new TransactionDiagnosticListener(store, meters, operation, config);
        TransactionExecution execution = execution("OrderService#create", false);
        listener.beforeBegin(execution);
        String traceId = TraceIdContext.currentTraceId();

        listener.afterBegin(execution, new IllegalStateException("connection unavailable"));

        assertThat(traceId).isNotBlank();
        assertThat(TraceIdContext.currentTraceId()).isNull();
        verify(operation, never()).startDiagnosticOnly(anyString(), anyString(), anyString());
        verify(store).recordTransaction(eq(traceId), eq("OrderService#create"), eq(false), anyLong(), any());
        verify(store, never()).transactionStarted();
    }

    @Test
    void jfrSetupFailureDoesNotChangeTransactionResultOrPersistFalseFailure() {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        OperationObservation operation = mock(OperationObservation.class);
        doThrow(new AssertionError("JFR unavailable"))
                .when(operation).startDiagnosticOnly("TRANSACTION", "OrderService#create", "write");
        TransactionDiagnosticListener listener =
                new TransactionDiagnosticListener(store, meters, operation, config);
        TransactionExecution execution = execution("OrderService#create", false);
        listener.beforeBegin(execution);

        assertThatCode(() -> listener.afterBegin(execution, null)).doesNotThrowAnyException();
        assertThatCode(() -> listener.afterCommit(execution, null)).doesNotThrowAnyException();
        verify(store).transactionStarted();
        verify(store).transactionFinished();
        verify(store, never()).recordTransaction(anyString(), anyString(), anyBoolean(), anyLong(), any());
    }

    private static TransactionExecution execution(String name, boolean readOnly) {
        TransactionExecution execution = mock(TransactionExecution.class);
        when(execution.getTransactionName()).thenReturn(name);
        when(execution.isReadOnly()).thenReturn(readOnly);
        return execution;
    }
}
