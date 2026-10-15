package com.laker.admin.infrastructure.observability.metrics;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.infrastructure.observability.jfr.OperationObservation;
import com.laker.admin.infrastructure.observability.remote.service.RemoteCallLogService;
import com.laker.admin.infrastructure.observability.remote.entity.RemoteCallLog;
import com.laker.admin.infrastructure.observability.trace.EasyTraceIdContext;
import com.laker.admin.infrastructure.observability.trace.SpanType;
import com.laker.admin.infrastructure.observability.trace.TraceContext;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RemoteCallMetricsAspectTest {

    @AfterEach
    void clearTrace() {
        TraceContext.clear();
        EasyTraceIdContext.clear();
    }

    @Test
    void shouldRecordSuccessfulRemoteCallAsMetricAndTraceSpan() throws Throwable {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RemoteCallMetricsAspect aspect = aspect(new EasyBusinessMetrics(meterRegistry));
        ProceedingJoinPoint joinPoint = joinPoint("fetch", "ok", null);
        TraceContext.startRoot("GET /api/example", SpanType.Http, "", 8, 0);

        assertThat(aspect.measure(joinPoint)).isEqualTo("ok");

        Timer timer = remoteTimer(meterRegistry, "success");
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isGreaterThan(0);
        assertThat(finishTrace()).contains("[Remote] SampleRemoteClient#fetch");
    }

    @Test
    void shouldRecordFailureAndRethrowTheOriginalException() throws Throwable {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RemoteCallMetricsAspect aspect = aspect(new EasyBusinessMetrics(meterRegistry));
        IllegalStateException failure = new IllegalStateException("remote unavailable");
        ProceedingJoinPoint joinPoint = joinPoint("fetch", null, failure);
        TraceContext.startRoot("GET /api/example", SpanType.Http, "", 8, 0);

        assertThatThrownBy(() -> aspect.measure(joinPoint)).isSameAs(failure);

        Timer timer = remoteTimer(meterRegistry, "failure");
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(finishTrace()).contains("[Remote] SampleRemoteClient#fetch");
    }

    @Test
    void shouldFreezeRemoteResourcesBeforeFailureFormattingAndMetricRecording() throws Throwable {
        EasyBusinessMetrics metrics = mock(EasyBusinessMetrics.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RemoteCallLogService> logProvider = mock(ObjectProvider.class);
        RemoteCallLogService logService = mock(RemoteCallLogService.class);
        when(logProvider.getIfAvailable()).thenReturn(logService);
        @SuppressWarnings("unchecked")
        ObjectProvider<OperationObservation> observationProvider = mock(ObjectProvider.class);
        OperationObservation observation = mock(OperationObservation.class);
        OperationObservation.Scope scope = mock(OperationObservation.Scope.class);
        when(observationProvider.getIfAvailable()).thenReturn(observation);
        when(observation.start("REMOTE", "SampleRemoteClient#fetch", "outbound", false)).thenReturn(scope);
        AtomicBoolean resourceFrozen = new AtomicBoolean();
        doAnswer(invocation -> {
            resourceFrozen.set(true);
            return null;
        }).when(scope).finish(eq("failure"), org.mockito.ArgumentMatchers.any(Throwable.class));
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getMonitor().getDiagnosis().setRemoteLogPersistenceEnabled(true);
        RemoteCallMetricsAspect aspect = new RemoteCallMetricsAspect(metrics, logProvider, observationProvider,
                config, Runnable::run);
        IllegalStateException failure = new BoundaryCheckedException("remote boom", resourceFrozen);

        assertThatThrownBy(() -> aspect.measure(joinPoint("fetch", null, failure, 0))).isSameAs(failure);

        verify(scope).finish("failure", failure);
        verify(metrics).recordRemoteCall(eq("SampleRemoteClient"), eq("fetch"), eq(false), anyLong());
        InOrder boundaryOrder = inOrder(scope, metrics);
        boundaryOrder.verify(scope).finish("failure", failure);
        boundaryOrder.verify(metrics).recordRemoteCall(eq("SampleRemoteClient"), eq("fetch"), eq(false), anyLong());
    }

    @Test
    void remoteDurationShouldStartAfterObservationAndTraceSetup() throws Throwable {
        EasyBusinessMetrics metrics = mock(EasyBusinessMetrics.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RemoteCallLogService> logProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<OperationObservation> observationProvider = mock(ObjectProvider.class);
        OperationObservation observation = mock(OperationObservation.class);
        OperationObservation.Scope scope = mock(OperationObservation.Scope.class);
        AtomicBoolean observationStarted = new AtomicBoolean();
        when(observationProvider.getIfAvailable()).thenReturn(observation);
        when(observation.start("REMOTE", "SampleRemoteClient#fetch", "outbound", false))
                .thenAnswer(invocation -> {
                    observationStarted.set(true);
                    return scope;
                });
        AtomicInteger clockReads = new AtomicInteger();
        RemoteCallMetricsAspect aspect = new RemoteCallMetricsAspect(
                metrics, logProvider, observationProvider, new EasyNextAdminConfig(), Runnable::run, () -> {
                    assertThat(observationStarted)
                            .as("remote wall timer must start after monitoring setup")
                            .isTrue();
                    return clockReads.getAndIncrement() == 0 ? 1_000L : 1_075L;
                });

        assertThat(aspect.measure(joinPoint("fetch", "ok", null, 0))).isEqualTo("ok");

        verify(metrics).recordRemoteCall("SampleRemoteClient", "fetch", true, 75L);
        verify(scope).finish("success", null);
    }

    @Test
    void monitoringFailureShouldNotChangeTheBusinessResult() throws Throwable {
        EasyBusinessMetrics metrics = mock(EasyBusinessMetrics.class);
        doThrow(new AssertionError("registry unavailable"))
                .when(metrics).recordRemoteCall(eq("SampleRemoteClient"), eq("fetch"), eq(true), anyLong());
        RemoteCallMetricsAspect aspect = aspect(metrics);
        ProceedingJoinPoint joinPoint = joinPoint("fetch", "ok", null);

        assertThat(aspect.measure(joinPoint)).isEqualTo("ok");
    }

    @Test
    void monitoringErrorMustNotReplaceRemoteBusinessFailure() throws Throwable {
        EasyBusinessMetrics metrics = mock(EasyBusinessMetrics.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RemoteCallLogService> logProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<OperationObservation> observationProvider = mock(ObjectProvider.class);
        OperationObservation observation = mock(OperationObservation.class);
        OperationObservation.Scope scope = mock(OperationObservation.Scope.class);
        when(observationProvider.getIfAvailable()).thenReturn(observation);
        when(observation.start("REMOTE", "SampleRemoteClient#fetch", "outbound", false)).thenReturn(scope);
        IllegalStateException businessFailure = new IllegalStateException("remote unavailable");
        doThrow(new AssertionError("monitoring finalizer failed"))
                .when(scope).finish("failure", businessFailure);
        RemoteCallMetricsAspect aspect = new RemoteCallMetricsAspect(metrics, logProvider, observationProvider,
                new EasyNextAdminConfig(), Runnable::run);

        assertThatThrownBy(() -> aspect.measure(joinPoint("fetch", null, businessFailure, 0)))
                .isSameAs(businessFailure);

        verify(scope).discard();
        verify(metrics).recordRemoteCall(eq("SampleRemoteClient"), eq("fetch"), eq(false), anyLong());
    }

    @Test
    void fastSuccessfulCallShouldRemainAggregateMetricOnly() throws Throwable {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RemoteCallLogService logService = mock(RemoteCallLogService.class);
        RemoteCallMetricsAspect aspect = aspect(new EasyBusinessMetrics(meterRegistry), logService, 60_000);

        assertThat(aspect.measure(joinPoint("fetch", "ok", null, 0))).isEqualTo("ok");

        assertThat(remoteTimer(meterRegistry, "success").count()).isEqualTo(1);
        verify(logService, never()).save(org.mockito.ArgumentMatchers.any(RemoteCallLog.class));
    }

    @Test
    void compatibilityPersistenceIsDisabledWithoutExplicitOptIn() throws Throwable {
        RemoteCallLogService logService = mock(RemoteCallLogService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RemoteCallLogService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(logService);
        RemoteCallMetricsAspect aspect = new RemoteCallMetricsAspect(
                new EasyBusinessMetrics(new SimpleMeterRegistry()), provider, 1, Runnable::run, false);

        assertThat(aspect.measure(joinPoint("fetch", "ok", null, 5))).isEqualTo("ok");

        verify(provider, never()).getIfAvailable();
        verify(logService, never()).save(org.mockito.ArgumentMatchers.any(RemoteCallLog.class));
    }

    @Test
    void slowSuccessfulCallShouldPersistBoundedDetail() throws Throwable {
        RemoteCallLogService logService = mock(RemoteCallLogService.class);
        RemoteCallMetricsAspect aspect = aspect(
                new EasyBusinessMetrics(new SimpleMeterRegistry()), logService, 1);
        EasyTraceIdContext.putTraceId("slow-remote-trace");

        assertThat(aspect.measure(joinPoint("fetch", "ok", null, 5))).isEqualTo("ok");

        ArgumentCaptor<RemoteCallLog> persisted = ArgumentCaptor.forClass(RemoteCallLog.class);
        verify(logService).save(persisted.capture());
        assertThat(persisted.getValue()).satisfies(log -> {
            assertThat(log.getTraceId()).isEqualTo("slow-remote-trace");
            assertThat(log.getTarget()).isEqualTo("SampleRemoteClient");
            assertThat(log.getMethod()).isEqualTo("fetch");
            assertThat(log.getSuccess()).isTrue();
            assertThat(log.getDurationMs()).isGreaterThanOrEqualTo(1);
            assertThat(log.getErrorMessage()).isNull();
        });
    }

    @Test
    void failedCallShouldPersistDetailEvenBelowSlowThreshold() throws Throwable {
        RemoteCallLogService logService = mock(RemoteCallLogService.class);
        RemoteCallMetricsAspect aspect = aspect(
                new EasyBusinessMetrics(new SimpleMeterRegistry()), logService, 60_000);
        IllegalStateException failure = new IllegalStateException("remote unavailable");

        assertThatThrownBy(() -> aspect.measure(joinPoint("fetch", null, failure, 0))).isSameAs(failure);

        ArgumentCaptor<RemoteCallLog> persisted = ArgumentCaptor.forClass(RemoteCallLog.class);
        verify(logService).save(persisted.capture());
        assertThat(persisted.getValue().getSuccess()).isFalse();
        assertThat(persisted.getValue().getErrorMessage()).isEqualTo("remote unavailable");
    }

    @Test
    void slowDetailPersistenceShouldRunOutsideTheBusinessCall() throws Throwable {
        RemoteCallLogService logService = mock(RemoteCallLogService.class);
        AtomicReference<Runnable> queued = new AtomicReference<>();
        RemoteCallMetricsAspect aspect = aspect(
                new EasyBusinessMetrics(new SimpleMeterRegistry()), logService, 1, queued::set);

        assertThat(aspect.measure(joinPoint("fetch", "ok", null, 5))).isEqualTo("ok");
        verify(logService, never()).save(org.mockito.ArgumentMatchers.any(RemoteCallLog.class));

        assertThat(queued).doesNotHaveNullValue();
        queued.get().run();
        verify(logService).save(org.mockito.ArgumentMatchers.any(RemoteCallLog.class));
    }

    @Test
    void fullPersistenceQueueShouldDropDetailWithoutChangingBusinessResult() throws Throwable {
        RemoteCallLogService logService = mock(RemoteCallLogService.class);
        RemoteCallMetricsAspect aspect = aspect(
                new EasyBusinessMetrics(new SimpleMeterRegistry()), logService, 1,
                task -> { throw new RejectedExecutionException("full"); });

        assertThat(aspect.measure(joinPoint("fetch", "ok", null, 5))).isEqualTo("ok");
        verify(logService, never()).save(org.mockito.ArgumentMatchers.any(RemoteCallLog.class));
    }

    private static RemoteCallMetricsAspect aspect(EasyBusinessMetrics metrics) {
        @SuppressWarnings("unchecked")
        ObjectProvider<RemoteCallLogService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return new RemoteCallMetricsAspect(metrics, provider);
    }

    private static RemoteCallMetricsAspect aspect(EasyBusinessMetrics metrics, RemoteCallLogService logService,
                                                   long slowThresholdMs) {
        return aspect(metrics, logService, slowThresholdMs, Runnable::run);
    }

    private static RemoteCallMetricsAspect aspect(EasyBusinessMetrics metrics, RemoteCallLogService logService,
                                                   long slowThresholdMs,
                                                   java.util.concurrent.Executor persistenceExecutor) {
        @SuppressWarnings("unchecked")
        ObjectProvider<RemoteCallLogService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(logService);
        return new RemoteCallMetricsAspect(metrics, provider, slowThresholdMs, persistenceExecutor);
    }

    private static ProceedingJoinPoint joinPoint(String methodName, Object result, Throwable failure) throws Throwable {
        return joinPoint(methodName, result, failure, 1);
    }

    private static ProceedingJoinPoint joinPoint(String methodName, Object result, Throwable failure,
                                                  long delayMs) throws Throwable {
        Method method = SampleRemoteClient.class.getMethod(methodName);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getDeclaringType()).thenReturn(SampleRemoteClient.class);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        if (failure == null) {
            when(joinPoint.proceed()).thenAnswer(invocation -> {
                if (delayMs > 0) Thread.sleep(delayMs);
                return result;
            });
        } else {
            when(joinPoint.proceed()).thenAnswer(invocation -> { throw failure; });
        }
        return joinPoint;
    }

    private static Timer remoteTimer(SimpleMeterRegistry registry, String result) {
        return registry.find(EasyBusinessMetrics.REMOTE_CALL_TIMER)
                .tags("target", "SampleRemoteClient", "method", "fetch", "result", result)
                .timer();
    }

    private static String finishTrace() {
        AtomicReference<TraceContext.DeferredCompletion> completion = new AtomicReference<>();
        TraceContext.stopRootDeferred(null, completion::set);
        return completion.get().tree();
    }

    interface SampleRemoteClient {
        String fetch();
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
                    .as("remote resources must be frozen before failure detail is formatted")
                    .isTrue();
            return super.getMessage();
        }
    }
}
