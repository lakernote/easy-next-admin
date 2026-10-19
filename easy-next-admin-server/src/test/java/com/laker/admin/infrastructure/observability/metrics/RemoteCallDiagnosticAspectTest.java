package com.laker.admin.infrastructure.observability.metrics;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.infrastructure.observability.jfr.OperationObservation;
import com.laker.admin.infrastructure.observability.remote.service.RemoteCallLogService;
import com.laker.admin.infrastructure.observability.remote.entity.RemoteCallLog;
import io.github.lakernote.observability.metrics.BusinessMetrics;
import io.github.lakernote.observability.trace.TraceIdContext;
import io.github.lakernote.observability.trace.SpanKind;
import io.github.lakernote.observability.trace.TraceContext;
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

class RemoteCallDiagnosticAspectTest {

    @AfterEach
    void clearTrace() {
        TraceContext.clear();
        TraceIdContext.clear();
    }

    @Test
    void frameworkAspectShouldNotDuplicateStarterMetricsOrTraceSpans() throws Throwable {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RemoteCallDiagnosticAspect aspect = aspect(new BusinessMetrics(meterRegistry));
        ProceedingJoinPoint joinPoint = joinPoint("fetch", "ok", null);
        TraceContext.startRoot("GET /api/example", SpanKind.HTTP, "", 8, 0);

        assertThat(aspect.aroundRemoteCall(joinPoint)).isEqualTo("ok");

        assertThat(remoteTimer(meterRegistry, "success")).isNull();
        assertThat(finishTrace()).doesNotContain("SampleRemoteClient#fetch");
    }

    @Test
    void fastSuccessfulCallShouldNotPersistDiagnosticDetail() throws Throwable {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RemoteCallLogService logService = mock(RemoteCallLogService.class);
        RemoteCallDiagnosticAspect aspect = aspect(new BusinessMetrics(meterRegistry), logService, 60_000);

        assertThat(aspect.aroundRemoteCall(joinPoint("fetch", "ok", null, 0))).isEqualTo("ok");

        assertThat(remoteTimer(meterRegistry, "success")).isNull();
        verify(logService, never()).save(org.mockito.ArgumentMatchers.any(RemoteCallLog.class));
    }

    @Test
    void compatibilityPersistenceIsDisabledWithoutExplicitOptIn() throws Throwable {
        RemoteCallLogService logService = mock(RemoteCallLogService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RemoteCallLogService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(logService);
        RemoteCallDiagnosticAspect aspect = new RemoteCallDiagnosticAspect(
                new BusinessMetrics(new SimpleMeterRegistry()), provider, 1, Runnable::run, false);

        assertThat(aspect.aroundRemoteCall(joinPoint("fetch", "ok", null, 5))).isEqualTo("ok");

        verify(provider, never()).getIfAvailable();
        verify(logService, never()).save(org.mockito.ArgumentMatchers.any(RemoteCallLog.class));
    }

    @Test
    void slowSuccessfulCallShouldPersistBoundedDetail() throws Throwable {
        RemoteCallLogService logService = mock(RemoteCallLogService.class);
        RemoteCallDiagnosticAspect aspect = aspect(
                new BusinessMetrics(new SimpleMeterRegistry()), logService, 1);
        TraceIdContext.putTraceId("slow-remote-trace");

        assertThat(aspect.aroundRemoteCall(joinPoint("fetch", "ok", null, 5))).isEqualTo("ok");

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
        RemoteCallDiagnosticAspect aspect = aspect(
                new BusinessMetrics(new SimpleMeterRegistry()), logService, 60_000);
        IllegalStateException failure = new IllegalStateException("remote unavailable");

        assertThatThrownBy(() -> aspect.aroundRemoteCall(joinPoint("fetch", null, failure, 0))).isSameAs(failure);

        ArgumentCaptor<RemoteCallLog> persisted = ArgumentCaptor.forClass(RemoteCallLog.class);
        verify(logService).save(persisted.capture());
        assertThat(persisted.getValue().getSuccess()).isFalse();
        assertThat(persisted.getValue().getErrorMessage()).isEqualTo("remote unavailable");
    }

    @Test
    void slowDetailPersistenceShouldRunOutsideTheBusinessCall() throws Throwable {
        RemoteCallLogService logService = mock(RemoteCallLogService.class);
        AtomicReference<Runnable> queued = new AtomicReference<>();
        RemoteCallDiagnosticAspect aspect = aspect(
                new BusinessMetrics(new SimpleMeterRegistry()), logService, 1, queued::set);

        assertThat(aspect.aroundRemoteCall(joinPoint("fetch", "ok", null, 5))).isEqualTo("ok");
        verify(logService, never()).save(org.mockito.ArgumentMatchers.any(RemoteCallLog.class));

        assertThat(queued).doesNotHaveNullValue();
        queued.get().run();
        verify(logService).save(org.mockito.ArgumentMatchers.any(RemoteCallLog.class));
    }

    @Test
    void fullPersistenceQueueShouldDropDetailWithoutChangingBusinessResult() throws Throwable {
        RemoteCallLogService logService = mock(RemoteCallLogService.class);
        RemoteCallDiagnosticAspect aspect = aspect(
                new BusinessMetrics(new SimpleMeterRegistry()), logService, 1,
                task -> { throw new RejectedExecutionException("full"); });

        assertThat(aspect.aroundRemoteCall(joinPoint("fetch", "ok", null, 5))).isEqualTo("ok");
        verify(logService, never()).save(org.mockito.ArgumentMatchers.any(RemoteCallLog.class));
    }

    private static RemoteCallDiagnosticAspect aspect(BusinessMetrics metrics) {
        @SuppressWarnings("unchecked")
        ObjectProvider<RemoteCallLogService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return new RemoteCallDiagnosticAspect(metrics, provider);
    }

    private static RemoteCallDiagnosticAspect aspect(BusinessMetrics metrics, RemoteCallLogService logService,
                                                   long slowThresholdMs) {
        return aspect(metrics, logService, slowThresholdMs, Runnable::run);
    }

    private static RemoteCallDiagnosticAspect aspect(BusinessMetrics metrics, RemoteCallLogService logService,
                                                   long slowThresholdMs,
                                                   java.util.concurrent.Executor persistenceExecutor) {
        @SuppressWarnings("unchecked")
        ObjectProvider<RemoteCallLogService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(logService);
        return new RemoteCallDiagnosticAspect(metrics, provider, slowThresholdMs, persistenceExecutor);
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
        return registry.find(BusinessMetrics.REMOTE_CALL_TIMER)
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
