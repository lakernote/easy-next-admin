package com.laker.admin.infrastructure.web.interceptor;

import com.laker.admin.common.constant.EasyNextAdminConstants;
import com.laker.admin.infrastructure.observability.jfr.OperationObservation;
import com.laker.admin.infrastructure.observability.trace.RecentTraceStore;
import io.github.lakernote.observability.trace.ObservationSuppression;
import com.laker.admin.infrastructure.web.handler.GlobalExceptionHandler;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.async.CallableProcessingInterceptor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EasyHttpDiagnosticInterceptorTest {

    @AfterEach
    void clearTraceId() {
        MDC.remove(EasyNextAdminConstants.TRACE_ID);
    }

    @Test
    void shouldReleaseDiagnosisSuppressionWhenRequestSwitchesToAsync() {
        EasyHttpDiagnosticInterceptor interceptor = new EasyHttpDiagnosticInterceptor(
                true, 3_000, 64, 1, mock(RecentTraceStore.class), mock(OperationObservation.class));
        Map<String, Object> attributes = new HashMap<>();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/monitor/diagnosis/summary");
        when(request.getAttribute(any())).thenAnswer(invocation -> attributes.get(invocation.getArgument(0)));
        doAnswer(invocation -> attributes.put(invocation.getArgument(0), invocation.getArgument(1)))
                .when(request).setAttribute(any(), any());
        doAnswer(invocation -> attributes.remove(invocation.getArgument(0)))
                .when(request).removeAttribute(any());

        HttpServletResponse response = mock(HttpServletResponse.class);
        interceptor.preHandle(request, response, new Object());
        try {
            assertThat(ObservationSuppression.active()).isTrue();
            interceptor.afterConcurrentHandlingStarted(request, response, new Object());
            assertThat(ObservationSuppression.active()).isFalse();
        } finally {
            interceptor.afterCompletion(request, response, new Object(), null);
        }
    }

    @Test
    void shouldCompleteAsyncRequestExactlyOnce() throws Exception {
        RecentTraceStore store = mock(RecentTraceStore.class);
        OperationObservation observation = mock(OperationObservation.class);
        OperationObservation.Scope scope = mock(OperationObservation.Scope.class);
        when(observation.startHttpRequest(eq("Object"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(scope);
        when(observation.startDiagnosticOnly(eq("HTTP"), eq("Object"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(scope);
        EasyHttpDiagnosticInterceptor interceptor = new EasyHttpDiagnosticInterceptor(
                false, 3_000, 64, 1, store, observation);

        Map<String, Object> attributes = new HashMap<>();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        AsyncContext asyncContext = mock(AsyncContext.class);
        when(request.getRequestURI()).thenReturn("/api/demo/42");
        when(request.getMethod()).thenReturn("GET");
        when(request.getAttribute(any())).thenAnswer(invocation -> attributes.get(invocation.getArgument(0)));
        doAnswer(invocation -> attributes.put(invocation.getArgument(0), invocation.getArgument(1)))
                .when(request).setAttribute(any(), any());
        doAnswer(invocation -> attributes.remove(invocation.getArgument(0)))
                .when(request).removeAttribute(any());
        when(request.getAsyncContext()).thenReturn(asyncContext);
        when(response.getStatus()).thenReturn(200);
        MDC.put(EasyNextAdminConstants.TRACE_ID, "trace-async");

        interceptor.preHandle(request, response, new Object());
        MDC.clear();
        interceptor.afterConcurrentHandlingStarted(request, response, new Object());

        ArgumentCaptor<AsyncListener> listener = ArgumentCaptor.forClass(AsyncListener.class);
        verify(asyncContext).addListener(listener.capture());
        verify(scope).markAsyncWaiting();
        listener.getValue().onComplete(new AsyncEvent(asyncContext, request, response));
        interceptor.afterCompletion(request, response, new Object(), null);

        verifyNoInteractions(store);
        verify(scope, times(1)).finishAsync("success", null);
        verify(scope, never()).finish(eq("success"), any());
    }

    @Test
    void shouldBindCallableWorkerWhileItExecutes() throws Exception {
        RecentTraceStore store = mock(RecentTraceStore.class);
        OperationObservation observation = mock(OperationObservation.class);
        OperationObservation.Scope scope = mock(OperationObservation.Scope.class);
        when(observation.startHttpRequest(eq("Object"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(scope);
        when(scope.rebindAfterInitialAsyncHandoff()).thenReturn(true);
        AtomicBoolean resourceReleased = new AtomicBoolean();
        doAnswer(invocation -> {
            resourceReleased.set(true);
            return null;
        }).when(scope).releaseAsyncBinding(true);
        EasyHttpDiagnosticInterceptor interceptor = new EasyHttpDiagnosticInterceptor(
                true, 3_000, 64, 1, store, observation);

        Map<String, Object> attributes = new HashMap<>();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getRequestURI()).thenReturn("/api/async-callable");
        when(request.getMethod()).thenReturn("GET");
        when(request.getAttribute(any())).thenAnswer(invocation -> attributes.get(invocation.getArgument(0)));
        doAnswer(invocation -> attributes.put(invocation.getArgument(0), invocation.getArgument(1)))
                .when(request).setAttribute(any(), any());
        MDC.put(EasyNextAdminConstants.TRACE_ID, "trace-callable");
        interceptor.preHandle(request, response, new Object());
        MDC.clear();

        NativeWebRequest webRequest = mock(NativeWebRequest.class);
        when(webRequest.getNativeRequest(HttpServletRequest.class)).thenReturn(request);
        Callable<Object> task = () -> null;
        CallableProcessingInterceptor callableInterceptor = interceptor.callableTraceInterceptor();
        callableInterceptor.preProcess(webRequest, task);
        org.assertj.core.api.Assertions.assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID))
                .isEqualTo("trace-callable");
        RuntimeException failure = new BoundaryCheckedRuntimeException("worker boom", resourceReleased);
        callableInterceptor.postProcess(webRequest, task, failure);
        org.assertj.core.api.Assertions.assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID)).isNull();

        verify(scope).rebindAfterInitialAsyncHandoff();
        verify(scope).releaseAsyncBinding(true);
    }

    @Test
    void shouldNotReleaseCallableResourceSegmentWhenInitialHandoffCannotBind() throws Exception {
        RecentTraceStore store = mock(RecentTraceStore.class);
        OperationObservation observation = mock(OperationObservation.class);
        OperationObservation.Scope scope = mock(OperationObservation.Scope.class);
        when(observation.startHttpRequest(eq("Object"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(scope);
        when(observation.startDiagnosticOnly(eq("HTTP"), eq("Object"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(scope);
        when(scope.rebindAfterInitialAsyncHandoff()).thenReturn(false);
        EasyHttpDiagnosticInterceptor interceptor = new EasyHttpDiagnosticInterceptor(
                false, 3_000, 64, 1, store, observation);

        Map<String, Object> attributes = new HashMap<>();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getRequestURI()).thenReturn("/api/async-callable");
        when(request.getMethod()).thenReturn("GET");
        when(request.getAttribute(any())).thenAnswer(invocation -> attributes.get(invocation.getArgument(0)));
        doAnswer(invocation -> attributes.put(invocation.getArgument(0), invocation.getArgument(1)))
                .when(request).setAttribute(any(), any());
        MDC.put(EasyNextAdminConstants.TRACE_ID, "trace-callable");
        interceptor.preHandle(request, response, new Object());
        MDC.clear();

        NativeWebRequest webRequest = mock(NativeWebRequest.class);
        when(webRequest.getNativeRequest(HttpServletRequest.class)).thenReturn(request);
        Callable<Object> task = () -> null;
        CallableProcessingInterceptor callableInterceptor = interceptor.callableTraceInterceptor();
        callableInterceptor.preProcess(webRequest, task);
        callableInterceptor.postProcess(webRequest, task, null);

        verify(scope).rebindAfterInitialAsyncHandoff();
        verify(scope).releaseAsyncBinding(false);
        org.assertj.core.api.Assertions.assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID)).isNull();
    }

    @Test
    void shouldReleaseWorkerContextAfterAsyncListenerAlreadyRemovedRequestState() throws Exception {
        RecentTraceStore store = mock(RecentTraceStore.class);
        OperationObservation observation = mock(OperationObservation.class);
        OperationObservation.Scope scope = mock(OperationObservation.Scope.class);
        when(observation.startHttpRequest(eq("Object"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(scope);
        when(observation.startDiagnosticOnly(eq("HTTP"), eq("Object"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(scope);
        when(scope.rebindAfterInitialAsyncHandoff()).thenReturn(true);
        EasyHttpDiagnosticInterceptor interceptor = new EasyHttpDiagnosticInterceptor(
                false, 3_000, 64, 1, store, observation);

        Map<String, Object> attributes = new HashMap<>();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getRequestURI()).thenReturn("/api/async-timeout");
        when(request.getMethod()).thenReturn("GET");
        when(request.getAttribute(any())).thenAnswer(invocation -> attributes.get(invocation.getArgument(0)));
        doAnswer(invocation -> attributes.put(invocation.getArgument(0), invocation.getArgument(1)))
                .when(request).setAttribute(any(), any());
        doAnswer(invocation -> attributes.remove(invocation.getArgument(0)))
                .when(request).removeAttribute(any());
        MDC.put(EasyNextAdminConstants.TRACE_ID, "trace-async-timeout");
        interceptor.preHandle(request, response, new Object());
        MDC.clear();

        NativeWebRequest webRequest = mock(NativeWebRequest.class);
        when(webRequest.getNativeRequest(HttpServletRequest.class)).thenReturn(request);
        Callable<Object> task = () -> null;
        CallableProcessingInterceptor callableInterceptor = interceptor.callableTraceInterceptor();
        callableInterceptor.preProcess(webRequest, task);
        request.removeAttribute(EasyHttpDiagnosticInterceptor.class.getName() + ".state");

        callableInterceptor.postProcess(webRequest, task, null);

        verify(scope).releaseAsyncBinding(true);
        org.assertj.core.api.Assertions.assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID)).isNull();
    }

    @Test
    void shouldPersistResolvedMvcExceptionTypeMessageAndStack() {
        RecentTraceStore store = mock(RecentTraceStore.class);
        OperationObservation observation = mock(OperationObservation.class);
        OperationObservation.Scope scope = mock(OperationObservation.Scope.class);
        when(observation.startHttpRequest(eq("Object"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(scope);
        when(observation.startDiagnosticOnly(eq("HTTP"), eq("Object"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(scope);
        EasyHttpDiagnosticInterceptor interceptor = new EasyHttpDiagnosticInterceptor(
                false, 3_000, 64, 1, store, observation);

        Map<String, Object> attributes = new HashMap<>();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getRequestURI()).thenReturn("/api/boom");
        when(request.getMethod()).thenReturn("GET");
        when(request.getAttribute(any())).thenAnswer(invocation -> attributes.get(invocation.getArgument(0)));
        doAnswer(invocation -> attributes.put(invocation.getArgument(0), invocation.getArgument(1)))
                .when(request).setAttribute(any(), any());
        doAnswer(invocation -> attributes.remove(invocation.getArgument(0)))
                .when(request).removeAttribute(any());
        when(response.getStatus()).thenReturn(500);
        MDC.put(EasyNextAdminConstants.TRACE_ID, "trace-resolved-exception");

        interceptor.preHandle(request, response, new Object());
        NullPointerException failure = new NullPointerException("resolved boom");
        new GlobalExceptionHandler().handleException(failure, request);
        interceptor.afterCompletion(request, response, new Object(), null);

        verify(scope).finish("failure", failure);
        verify(store).recordIncident(eq("HTTP"), eq("trace-resolved-exception"), eq("Object"),
                eq("GET /api/boom"), eq(500), any(), any(Long.class), eq(false), eq(true), any(),
                eq(NullPointerException.class.getName()), eq("resolved boom"), contains("NullPointerException"),
                isNull(), isNull(), isNull(), isNull());
    }

    @Test
    void monitoringErrorDuringCompletionMustNotEscapeTheHttpLifecycle() {
        RecentTraceStore store = mock(RecentTraceStore.class);
        OperationObservation observation = mock(OperationObservation.class);
        OperationObservation.Scope scope = mock(OperationObservation.Scope.class);
        when(observation.startHttpRequest(eq("Object"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(scope);
        when(observation.startDiagnosticOnly(eq("HTTP"), eq("Object"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(scope);
        IllegalStateException businessFailure = new IllegalStateException("handler failed");
        doThrow(new AssertionError("monitoring finalizer failed"))
                .when(scope).finish("failure", businessFailure);
        EasyHttpDiagnosticInterceptor interceptor = new EasyHttpDiagnosticInterceptor(
                false, 3_000, 64, 1, store, observation);

        Map<String, Object> attributes = new HashMap<>();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getRequestURI()).thenReturn("/api/failure");
        when(request.getMethod()).thenReturn("GET");
        when(request.getAttribute(any())).thenAnswer(invocation -> attributes.get(invocation.getArgument(0)));
        doAnswer(invocation -> attributes.put(invocation.getArgument(0), invocation.getArgument(1)))
                .when(request).setAttribute(any(), any());
        doAnswer(invocation -> attributes.remove(invocation.getArgument(0)))
                .when(request).removeAttribute(any());
        when(response.getStatus()).thenReturn(500);

        interceptor.preHandle(request, response, new Object());

        assertThatCode(() -> interceptor.afterCompletion(request, response, new Object(), businessFailure))
                .doesNotThrowAnyException();
        verify(scope).discard();
        verifyNoInteractions(store);
    }

    @Test
    void shouldPersistFatalErrorWrappedBySpringMvc() {
        RecentTraceStore store = mock(RecentTraceStore.class);
        OperationObservation observation = mock(OperationObservation.class);
        OperationObservation.Scope scope = mock(OperationObservation.Scope.class);
        when(observation.startHttpRequest(eq("Object"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(scope);
        AtomicBoolean resourceFrozen = new AtomicBoolean();
        doAnswer(invocation -> {
            resourceFrozen.set(true);
            return null;
        }).when(scope).finish(eq("failure"), any(Throwable.class));
        EasyHttpDiagnosticInterceptor interceptor = new EasyHttpDiagnosticInterceptor(
                true, 3_000, 64, 1, store, observation);

        Map<String, Object> attributes = new HashMap<>();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getRequestURI()).thenReturn("/api/oom");
        when(request.getMethod()).thenReturn("GET");
        when(request.getAttribute(any())).thenAnswer(invocation -> attributes.get(invocation.getArgument(0)));
        doAnswer(invocation -> attributes.put(invocation.getArgument(0), invocation.getArgument(1)))
                .when(request).setAttribute(any(), any());
        doAnswer(invocation -> attributes.remove(invocation.getArgument(0)))
                .when(request).removeAttribute(any());
        when(response.getStatus()).thenReturn(500);
        MDC.put(EasyNextAdminConstants.TRACE_ID, "trace-fatal-http");

        interceptor.preHandle(request, response, new Object());
        OutOfMemoryError failure = new BoundaryCheckedOutOfMemoryError(
                "simulated http allocation failure", resourceFrozen);
        ServletException dispatchFailure = new ServletException("Handler dispatch failed", failure);
        interceptor.afterCompletion(request, response, new Object(), dispatchFailure);

        verify(scope).finish("failure", failure);
        verify(store).recordIncident(eq("HTTP"), eq("trace-fatal-http"), eq("Object"),
                eq("GET /api/oom"), eq(500), contains("Object"), any(Long.class), eq(false), eq(true), any(),
                eq(failure.getClass().getName()), eq("simulated http allocation failure"),
                contains("OutOfMemoryError"), isNull(), isNull(), isNull(), isNull());
    }

    private static final class BoundaryCheckedRuntimeException extends RuntimeException {
        private final AtomicBoolean resourceFrozen;

        private BoundaryCheckedRuntimeException(String message, AtomicBoolean resourceFrozen) {
            super(message);
            this.resourceFrozen = resourceFrozen;
        }

        @Override
        public String getMessage() {
            org.assertj.core.api.Assertions.assertThat(resourceFrozen)
                    .as("resource segment must be frozen before trace formats the failure")
                    .isTrue();
            return super.getMessage();
        }
    }

    private static final class BoundaryCheckedOutOfMemoryError extends OutOfMemoryError {
        private final AtomicBoolean resourceFrozen;

        private BoundaryCheckedOutOfMemoryError(String message, AtomicBoolean resourceFrozen) {
            super(message);
            this.resourceFrozen = resourceFrozen;
        }

        @Override
        public String getMessage() {
            org.assertj.core.api.Assertions.assertThat(resourceFrozen)
                    .as("resource observation must finish before trace/error evidence is formatted")
                    .isTrue();
            return super.getMessage();
        }
    }
}
