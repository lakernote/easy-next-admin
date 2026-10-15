package com.laker.admin.infrastructure.observability.trace;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.infrastructure.observability.jfr.ActiveOperationRegistry;
import com.laker.admin.infrastructure.observability.jfr.LocalJfrRecorder;
import com.laker.admin.infrastructure.observability.jfr.OperationObservation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.TransactionExecution;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;

class TransactionObservationListenerTest {

    @AfterEach
    void clearContexts() {
        EasyTraceIdContext.clear();
        TraceContext.clear();
    }

    @Test
    void beginFailureWithoutEntryRootShouldPersistCanonicalIncidentAndOperation() {
        Fixture fixture = fixture();
        TransactionExecution execution = execution("OrderService#create", false);
        IllegalStateException failure = new IllegalStateException("cannot acquire connection");

        fixture.listener.beforeBegin(execution);
        String generatedTraceId = EasyTraceIdContext.currentTraceId();
        assertThat(fixture.registry.countsByType().get("TRANSACTION")).isZero();
        fixture.listener.afterBegin(execution, failure);

        assertThat(generatedTraceId).isNotBlank();
        assertThat(EasyTraceIdContext.currentTraceId()).isNull();
        assertThat(fixture.registry.countsByType().get("TRANSACTION")).isZero();
        verify(fixture.store).recordTransaction(eq(generatedTraceId), eq("OrderService#create"), eq(false),
                anyLong(), any());

        ArgumentCaptor<RecentTraceStore.OperationSample> operation =
                ArgumentCaptor.forClass(RecentTraceStore.OperationSample.class);
        verify(fixture.store).recordOperation(operation.capture());
        assertThat(operation.getValue()).satisfies(sample -> {
            assertThat(sample.type()).isEqualTo("TRANSACTION");
            assertThat(sample.traceId()).isEqualTo(generatedTraceId);
            assertThat(sample.name()).isEqualTo("OrderService#create");
            assertThat(sample.outcome()).isEqualTo("failure");
            assertThat(sample.stackTrace()).contains("cannot acquire connection");
        });

        verify(fixture.store).recordIncident(eq("TRANSACTION"), eq(generatedTraceId),
                eq("OrderService#create"), eq("write"), eq(500), anyString(), anyLong(),
                anyBoolean(), eq(true), anyList(), eq(IllegalStateException.class.getName()),
                eq("cannot acquire connection"), anyString(), any(), any(), any());
    }

    @Test
    void beginFailureUnderHttpRootShouldAttachToRootWithoutStandaloneIncident() {
        Fixture fixture = fixture();
        EasyTraceIdContext.putTraceId("existing-http-trace");
        OperationObservation.Scope root = fixture.observation.start(
                "HTTP", "OrderController#create", "POST /orders", true);
        TransactionExecution execution = execution("OrderService#create", false);

        fixture.listener.beforeBegin(execution);
        assertThat(fixture.registry.countsByType().get("TRANSACTION")).isZero();
        fixture.listener.afterBegin(execution, new IllegalStateException("database unavailable"));
        assertThat(fixture.registry.countsByType().get("TRANSACTION")).isZero();
        root.finish("success");

        // The HTTP interceptor owns the canonical root incident. The child transaction only enriches its
        // completion, so it cannot create a second incident for the same failure.
        verify(fixture.store, never()).recordIncident(anyString(), anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyInt(), anyString(), anyLong(), anyBoolean(), anyBoolean(),
                anyList(), anyString(), anyString(), anyString(), any(), any(), any());
        assertThat(root.completion().error()).isTrue();
        assertThat(root.completion().errorCause().type()).isEqualTo("TRANSACTION");
        assertThat(EasyTraceIdContext.currentTraceId()).isEqualTo("existing-http-trace");
    }

    @Test
    void monitoringFailureShouldNotReplaceTransactionFailureAndShouldClearOwnedTrace() {
        Fixture fixture = fixture();
        TransactionExecution execution = execution("OrderService#broken", false);
        doThrow(new AssertionError("diagnosis storage unavailable"))
                .when(fixture.store).recordTransaction(anyString(), anyString(), anyBoolean(), anyLong(), any());

        fixture.listener.beforeBegin(execution);

        assertThatCode(() -> fixture.listener.afterBegin(execution,
                new IllegalArgumentException("business begin failure"))).doesNotThrowAnyException();
        assertThat(EasyTraceIdContext.currentTraceId()).isNull();
    }

    @Test
    void monitoringSetupFailureMustDiscardScopeWithoutReportingTransactionFailure() {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        OperationObservation observation = mock(OperationObservation.class);
        OperationObservation.Scope scope = mock(OperationObservation.Scope.class);
        when(observation.start("TRANSACTION", "OrderService#create", "write", false)).thenReturn(scope);
        doThrow(new AssertionError("boundary setup failed")).when(scope).beginBusinessBoundary();
        TransactionObservationListener listener =
                new TransactionObservationListener(store, meters, observation, config);
        TransactionExecution execution = execution("OrderService#create", false);

        listener.beforeBegin(execution);
        assertThatCode(() -> listener.afterBegin(execution, null)).doesNotThrowAnyException();

        verify(scope).discard();
        verify(scope, never()).finish(anyString(), any());
        verify(store).transactionStarted();
        verify(store).transactionFinished();
        verify(store, never()).recordTransaction(anyString(), anyString(), anyBoolean(), anyLong(), any());
    }

    @Test
    void transactionTimersShouldAvoidUnusedClientSidePercentiles() {
        Fixture fixture = fixture();

        assertThat(fixture.meters.find("easy.transactions").timers()).hasSize(4)
                .allSatisfy(timer -> assertThat(timer.takeSnapshot().percentileValues()).isEmpty());
    }

    @Test
    void successfulBeginShouldStartAndFinishTransactionScope() {
        Fixture fixture = fixture();
        TransactionExecution execution = execution("OrderService#create", false);
        TraceContext.startRoot("POST /orders", SpanType.Http, "", 8, 0);

        fixture.listener.beforeBegin(execution);
        assertThat(fixture.registry.countsByType().get("TRANSACTION")).isZero();

        fixture.listener.afterBegin(execution, null);
        assertThat(fixture.registry.countsByType().get("TRANSACTION")).isEqualTo(1);

        fixture.listener.afterCommit(execution, null);
        assertThat(fixture.registry.countsByType().get("TRANSACTION")).isZero();
        AtomicReference<TraceContext.Completion> trace = new AtomicReference<>();
        TraceContext.markSlow("TEST_CAPTURE");
        TraceContext.stopRoot(0, "test", null, trace::set);
        assertThat(trace.get().tree()).contains("OrderService#create");
    }

    @Test
    void failedBeginShouldNotCreateTransactionSpan() {
        Fixture fixture = fixture();
        TransactionExecution execution = execution("OrderService#create", false);
        TraceContext.startRoot("POST /orders", SpanType.Http, "", 8, 0);

        fixture.listener.beforeBegin(execution);
        fixture.listener.afterBegin(execution, new IllegalStateException("cannot acquire connection"));

        AtomicReference<TraceContext.Completion> trace = new AtomicReference<>();
        TraceContext.stopRoot(0, "test", null, trace::set);
        assertThat(trace.get().tree()).doesNotContain("OrderService#create");
        assertThat(fixture.registry.countsByType().get("TRANSACTION")).isZero();
    }

    @Test
    void slowThresholdAndTimerShouldMeasureOnlyTheStartedTransaction() {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getTrace().setTransactionSlowThresholdMs(1_000);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        ActiveOperationRegistry registry = new ActiveOperationRegistry(meters, config);
        @SuppressWarnings("unchecked")
        ObjectProvider<LocalJfrRecorder> jfrRecorderProvider = mock(ObjectProvider.class);
        OperationObservation observation =
                new OperationObservation(store, config, registry, meters, jfrRecorderProvider);
        AtomicLong nanoTime = new AtomicLong();
        TransactionObservationListener listener =
                new TransactionObservationListener(store, meters, observation, config, nanoTime::get);
        TransactionExecution execution = execution("OrderService#create", false);

        listener.beforeBegin(execution);
        nanoTime.set(TimeUnit.SECONDS.toNanos(10));
        listener.afterBegin(execution, null);
        nanoTime.addAndGet(TimeUnit.MILLISECONDS.toNanos(500));
        listener.afterCommit(execution, null);

        verify(store, never()).recordTransaction(anyString(), anyString(), anyBoolean(), anyLong(), any());
        assertThat(meters.get("easy.transactions")
                .tag("result", "success")
                .tag("read_only", "false")
                .timer()
                .totalTime(TimeUnit.MILLISECONDS)).isEqualTo(500);
        assertThat(registry.countsByType().get("TRANSACTION")).isZero();
    }

    @Test
    void rollbackShouldFreezeResourcesBeforeFormattingFailureOrFinishingTrace() {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getTrace().setTransactionSlowThresholdMs(60_000);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        OperationObservation observation = mock(OperationObservation.class);
        OperationObservation.Scope scope = mock(OperationObservation.Scope.class);
        when(observation.start("TRANSACTION", "OrderService#create", "write", false)).thenReturn(scope);
        AtomicBoolean resourceFrozen = new AtomicBoolean();
        doAnswer(invocation -> {
            resourceFrozen.set(true);
            return null;
        }).when(scope).finish(eq("failure"), any(Throwable.class));
        TransactionObservationListener listener =
                new TransactionObservationListener(store, meters, observation, config);
        TransactionExecution execution = execution("OrderService#create", false);
        TraceContext.startRoot("POST /orders", SpanType.Http, "", 8, 0);

        listener.beforeBegin(execution);
        listener.afterBegin(execution, null);
        IllegalStateException failure = new BoundaryCheckedException("rollback boom", resourceFrozen);
        listener.afterRollback(execution, failure);

        verify(scope).finish("failure", failure);
        assertThat(resourceFrozen).isTrue();
    }

    private static final class BoundaryCheckedException extends IllegalStateException {
        private final AtomicBoolean resourceFrozen;

        private BoundaryCheckedException(String message, AtomicBoolean resourceFrozen) {
            super(message);
            this.resourceFrozen = resourceFrozen;
        }

        @Override
        public String getMessage() {
            assertThat(resourceFrozen)
                    .as("transaction resources must be frozen before failure evidence is formatted")
                    .isTrue();
            return super.getMessage();
        }
    }

    private static Fixture fixture() {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getTrace().setTransactionSlowThresholdMs(60_000);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        ActiveOperationRegistry registry = new ActiveOperationRegistry(meters, config);
        @SuppressWarnings("unchecked")
        ObjectProvider<LocalJfrRecorder> jfrRecorderProvider = mock(ObjectProvider.class);
        OperationObservation observation =
                new OperationObservation(store, config, registry, meters, jfrRecorderProvider);
        TransactionObservationListener listener =
                new TransactionObservationListener(store, meters, observation, config);
        return new Fixture(store, meters, registry, observation, listener);
    }

    private static TransactionExecution execution(String name, boolean readOnly) {
        TransactionExecution execution = mock(TransactionExecution.class);
        when(execution.getTransactionName()).thenReturn(name);
        when(execution.isReadOnly()).thenReturn(readOnly);
        return execution;
    }

    private record Fixture(RecentTraceStore store, SimpleMeterRegistry meters, ActiveOperationRegistry registry,
                           OperationObservation observation, TransactionObservationListener listener) {
    }
}
