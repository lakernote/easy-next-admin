package com.laker.admin.infrastructure.web.interceptor;

import com.laker.observability.trace.BoundedThrowableFormatter;
import com.laker.observability.trace.SpanKind;
import com.laker.observability.trace.TraceContext;
import com.laker.observability.trace.TraceIdContext;
import com.laker.observability.trace.MdcContext;
import com.laker.admin.infrastructure.observability.trace.RecentTraceStore;
import com.laker.admin.infrastructure.observability.jfr.OperationObservation;
import com.laker.observability.trace.ObservationSuppression;
import com.laker.observability.trace.TraceHistory;
import com.laker.admin.infrastructure.web.context.EasyResolvedExceptionContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.async.CallableProcessingInterceptor;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class EasyHttpDiagnosticInterceptor implements AsyncHandlerInterceptor {
    private static final int MAX_DEFERRED_TRACE_SEGMENTS = 8;
    private static final String STATE_ATTRIBUTE = EasyHttpDiagnosticInterceptor.class.getName() + ".state";
    private static final String SUPPRESSION_ATTRIBUTE = EasyHttpDiagnosticInterceptor.class.getName() + ".suppression";

    private final boolean enabled;
    private final long slowThresholdMs;
    private final int maxDepth;
    private final long minNodeCostMs;
    private final RecentTraceStore recentTraceStore;
    private final OperationObservation operationObservation;
    private final TraceHistory traceHistory;
    /** Callable workers are serialized per thread; keep one reusable slot instead of one ThreadLocal per request. */
    private final ThreadLocal<AsyncWorkerBinding> workerBinding = new ThreadLocal<>();

    public EasyHttpDiagnosticInterceptor(boolean enabled, long slowThresholdMs, int maxDepth, long minNodeCostMs,
                                    RecentTraceStore recentTraceStore, OperationObservation operationObservation) {
        this(enabled, slowThresholdMs, maxDepth, minNodeCostMs, recentTraceStore, operationObservation,
                new TraceHistory(500));
    }

    public EasyHttpDiagnosticInterceptor(boolean enabled, long slowThresholdMs, int maxDepth, long minNodeCostMs,
                                    RecentTraceStore recentTraceStore, OperationObservation operationObservation,
                                    TraceHistory traceHistory) {
        this.enabled = enabled;
        this.slowThresholdMs = slowThresholdMs;
        this.maxDepth = maxDepth;
        this.minNodeCostMs = minNodeCostMs;
        this.recentTraceStore = recentTraceStore;
        this.operationObservation = operationObservation;
        this.traceHistory = traceHistory;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getRequestURI().startsWith("/api/monitor/diagnosis")
                || request.getRequestURI().startsWith("/api/easy-observability")) {
            request.setAttribute(SUPPRESSION_ATTRIBUTE, ObservationSuppression.enter());
            return true;
        }
        Object existing = request.getAttribute(STATE_ATTRIBUTE);
        if (existing instanceof RequestState state) {
            try { state.observation.rebindCurrentThread(); }
            catch (Throwable monitoringFailure) { state.observation.discard(); }
            return true;
        }
        String handlerName = resolveHandlerName(handler);
        boolean ownsTrace = enabled && !TraceContext.active();
        OperationObservation.Scope observation = ownsTrace
                ? operationObservation.startHttpRequest(handlerName,
                        request.getMethod() + " " + request.getRequestURI())
                : operationObservation.startDiagnosticOnly("HTTP", handlerName,
                        request.getMethod() + " " + request.getRequestURI());
        RequestState state = new RequestState(handlerName, TraceIdContext.currentTraceId(), MdcContext.copy(),
                request.getMethod() + " " + request.getRequestURI(), observation, ownsTrace);
        request.setAttribute(STATE_ATTRIBUTE, state);
        if (ownsTrace) {
            try {
                TraceContext.startRoot(handlerName, SpanKind.HTTP,
                        request.getMethod() + " " + request.getRequestURI(), maxDepth, minNodeCostMs);
            } catch (Throwable monitoringFailure) {
                TraceContext.clear();
            }
        }
        observation.beginBusinessBoundary();
        if (ownsTrace && TraceContext.active()) TraceContext.beginCurrentSpanBoundary();
        state.beginBusinessBoundary();
        return true;
    }

    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response,
                                               Object handler) {
        Object suppression = request.getAttribute(SUPPRESSION_ATTRIBUTE);
        if (suppression instanceof ObservationSuppression.Scope scope) {
            scope.close();
            request.removeAttribute(SUPPRESSION_ATTRIBUTE);
            return;
        }
        Object value = request.getAttribute(STATE_ATTRIBUTE);
        if (!(value instanceof RequestState state)) return;
        state.async = true;
        // Freeze the servlet-thread resource segment at the business handoff boundary. Trace finalization may render
        // or format incident evidence and must not be charged to the request's CPU/allocation counters.
        try {
            if (state.ownsTrace && TraceContext.active()) TraceContext.freezeRootBoundary();
            state.observation.markAsyncWaiting();
            if (state.ownsTrace && TraceContext.active()) {
                TraceContext.stopRootDeferred(null, state::mergeDeferred);
            } else if (state.ownsTrace) {
                TraceContext.clear();
            }
        } catch (Throwable monitoringFailure) {
            state.observation.discard();
            if (state.ownsTrace) TraceContext.clear();
        }
        try {
            request.getAsyncContext().addListener(new AsyncCompletionListener(request, response, state));
        } catch (Throwable ignoredMonitoringFailure) { }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Object suppression = request.getAttribute(SUPPRESSION_ATTRIBUTE);
        if (suppression instanceof ObservationSuppression.Scope scope) {
            scope.close();
            request.removeAttribute(SUPPRESSION_ATTRIBUTE);
            return;
        }
        Object value = request.getAttribute(STATE_ATTRIBUTE);
        if (!(value instanceof RequestState state)) {
            EasyResolvedExceptionContext.clear(request);
            return;
        }
        Throwable resolvedFailure = diagnosticFailure(ex != null ? ex : EasyResolvedExceptionContext.get(request));
        if (state.async) {
            complete(request, response, state, resolvedFailure, true);
            return;
        }
        complete(request, response, state, resolvedFailure, false);
    }

    /**
     * Accounts for the thread that actually executes a Spring MVC {@link Callable}. DeferredResult remains on its
     * existing lifecycle because the application controls which thread completes it.
     */
    public CallableProcessingInterceptor callableTraceInterceptor() {
        return new CallableProcessingInterceptor() {
            @Override
            public <T> void preProcess(NativeWebRequest webRequest, Callable<T> task) {
                rebindAsyncWorker(webRequest.getNativeRequest(HttpServletRequest.class));
            }

            @Override
            public <T> void postProcess(NativeWebRequest webRequest, Callable<T> task, Object concurrentResult) {
                releaseAsyncWorker(webRequest.getNativeRequest(HttpServletRequest.class),
                        concurrentResult instanceof Throwable throwable ? throwable : null);
            }

            @Override
            public <T> void afterCompletion(NativeWebRequest webRequest, Callable<T> task) {
                releaseAsyncWorker(webRequest.getNativeRequest(HttpServletRequest.class), null);
            }
        };
    }

    void rebindAsyncWorker(HttpServletRequest request) {
        RequestState state = requestState(request);
        if (state == null || workerBinding.get() != null) return;
        MdcContext.Scope mdcScope = null;
        boolean resourceBound = false;
        boolean traceStarted = false;
        try {
            mdcScope = MdcContext.withContext(state.mdcContext);
            resourceBound = state.observation.rebindAfterInitialAsyncHandoff();
            if (state.ownsTrace) {
                try {
                    TraceContext.startRoot(state.handlerName, SpanKind.HTTP,
                            state.methodPath, maxDepth, minNodeCostMs);
                    traceStarted = true;
                } catch (Throwable monitoringFailure) {
                    if (state.ownsTrace) TraceContext.clear();
                }
            }
            workerBinding.set(new AsyncWorkerBinding(state, mdcScope, traceStarted, resourceBound));
            if (traceStarted) TraceContext.beginCurrentSpanBoundary();
        } catch (Throwable monitoringFailure) {
            if (resourceBound) {
                try { state.observation.releaseAsyncBinding(true); } catch (Throwable ignored) { }
            }
            if (state.ownsTrace) TraceContext.clear();
            if (mdcScope != null) {
                try { mdcScope.close(); } catch (Throwable ignored) { }
            }
            workerBinding.remove();
        }
    }

    void releaseAsyncWorker(HttpServletRequest request) {
        releaseAsyncWorker(request, null);
    }

    void releaseAsyncWorker(HttpServletRequest request, Throwable failure) {
        AsyncWorkerBinding binding = workerBinding.get();
        if (binding == null) return;
        RequestState requestState = requestState(request);
        if (requestState != null && binding.state != requestState) return;
        RequestState state = binding.state;
        try {
            try {
                // End the worker's resource segment before TraceContext formats an exception or materializes trace data.
                // releaseAsyncBinding keeps the aggregate observation alive for the servlet completion callback.
                if (binding.traceStarted && TraceContext.active()) TraceContext.freezeRootBoundary();
                state.observation.releaseAsyncBinding(binding.resourceBound);
                if (binding.traceStarted && TraceContext.active()) {
                    if (failure != null) TraceContext.markError("HTTP_ERROR", failure);
                    TraceContext.stopRootDeferred(failure, state::mergeDeferred);
                } else if (binding.traceStarted) {
                    TraceContext.clear();
                }
            } catch (Throwable monitoringFailure) {
                state.observation.discard();
                if (binding.traceStarted) TraceContext.clear();
            }
        } finally {
            try { binding.mdcScope.close(); } catch (Throwable ignored) { }
            workerBinding.remove();
        }
    }

    private static RequestState requestState(HttpServletRequest request) {
        if (request == null) return null;
        Object value = request.getAttribute(STATE_ATTRIBUTE);
        return value instanceof RequestState state ? state : null;
    }

    private void complete(HttpServletRequest request, HttpServletResponse response, RequestState state,
                          Throwable failure, boolean async) {
        try {
            completeObserved(request, response, state, failure, async);
        } catch (Throwable monitoringFailure) {
            try { state.observation.discard(); } catch (Throwable ignored) { }
            if (state.ownsTrace) TraceContext.clear();
            try { request.removeAttribute(STATE_ATTRIBUTE); } catch (Throwable ignored) { }
            try { EasyResolvedExceptionContext.clear(request); } catch (Throwable ignored) { }
        }
    }

    private void completeObserved(HttpServletRequest request, HttpServletResponse response, RequestState state,
                                  Throwable failure, boolean async) {
        Throwable resolvedFailure = diagnosticFailure(
                failure != null ? failure : EasyResolvedExceptionContext.get(request));
        if (!state.tryComplete()) {
            EasyResolvedExceptionContext.clear(request);
            return;
        }
        long durationMs = Math.max(0, (System.nanoTime() - state.startedNanos) / 1_000_000);
        int status = response.getStatus();
        String outcome = resolvedFailure != null || status >= 400 ? "failure" : "success";
        boolean rootSlow = slowThresholdMs > 0 && durationMs >= slowThresholdMs;
        state.observation.endBusinessBoundary();
        if (!async && state.ownsTrace && TraceContext.active()) {
            try { TraceContext.freezeRootBoundary(); }
            catch (Throwable monitoringFailure) { TraceContext.clear(); }
        }
        // Freeze wall/resource counters before any trace rendering, exception formatting, JSONL enqueue, or log.
        if (async) {
            BoundedThrowableFormatter.FailureEvidence existingEvidence = state.failureEvidence();
            if (existingEvidence.present()) state.observation.finishAsync(outcome, resolvedFailure, existingEvidence);
            else state.observation.finishAsync(outcome, resolvedFailure);
        } else {
            state.observation.finish(outcome, resolvedFailure);
        }
        OperationObservation.Completion operationCompletion = state.observation.completion();
        BoundedThrowableFormatter.FailureEvidence failureEvidence = state.observation.failureEvidence();
        state.merge(operationCompletion);
        if (!async) {
            if (state.ownsTrace && TraceContext.active()) {
                if (rootSlow) TraceContext.markSlow("SLOW_REQUEST");
                if (resolvedFailure != null || status >= 400) {
                    if (failureEvidence != null && failureEvidence.present()) {
                        TraceContext.markError("HTTP_ERROR", failureEvidence);
                    } else {
                        TraceContext.markError("HTTP_ERROR", resolvedFailure);
                    }
                }
                TraceContext.stopRoot(slowThresholdMs,
                        "HTTP trace tree, status=%s".formatted(status), resolvedFailure,
                        state::merge);
            } else if (state.ownsTrace) {
                TraceContext.clear();
            }
        }
        boolean rootError = resolvedFailure != null || status >= 400;
        if (rootSlow) state.addReason("SLOW_REQUEST");
        if (rootError) state.addReason("HTTP_ERROR");
        boolean hasIncident = state.hasIncident(rootSlow, rootError);
        IncidentEvidence evidence = hasIncident
                ? state.evidence(rootSlow, rootError, resolvedFailure, status) : null;
        if (state.ownsTrace) {
            traceHistory.add(state.traceId, state.handlerName, state.methodPath, status, durationMs,
                    state.incidentSlow || rootSlow, state.incidentError || rootError,
                    evidence == null ? "" : evidence.tree());
        }
        if (evidence != null) {
            recentTraceStore.recordIncident("HTTP", state.traceId, state.handlerName, state.methodPath, status,
                    evidence.tree(), durationMs, evidence.slow(), evidence.error(), evidence.reasons(),
                    evidence.errorType(), evidence.errorMessage(), evidence.stackTrace(), evidence.primaryCause(),
                    evidence.slowCause(), evidence.errorCause(), state.observation.finishedAt());
        }
        request.removeAttribute(STATE_ATTRIBUTE);
        EasyResolvedExceptionContext.clear(request);
    }

    private static String resolveHandlerName(Object handler) {
        if (handler instanceof HandlerMethod handlerMethod) {
            return handlerMethod.getBeanType().getSimpleName() + "#" + handlerMethod.getMethod().getName();
        }
        return handler == null ? "-" : handler.getClass().getSimpleName();
    }

    private static final class RequestState {
        private final String handlerName;
        private final String traceId;
        private final Map<String, String> mdcContext;
        private final String methodPath;
        private final OperationObservation.Scope observation;
        private final boolean ownsTrace;
        private volatile long startedNanos = System.nanoTime();
        private boolean completed;
        private volatile String tree = "";
        private volatile boolean async;
        private List<TraceContext.DeferredCompletion> deferredTraceSegments;

        private RequestState(String handlerName, String traceId, Map<String, String> mdcContext, String methodPath,
                             OperationObservation.Scope observation, boolean ownsTrace) {
            this.handlerName = handlerName;
            this.traceId = traceId;
            this.mdcContext = mdcContext;
            this.methodPath = methodPath;
            this.observation = observation;
            this.ownsTrace = ownsTrace;
        }

        private void beginBusinessBoundary() {
            startedNanos = System.nanoTime();
        }

        private boolean incidentSlow;
        private boolean incidentError;
        private LinkedHashSet<String> incidentReasons;
        private String errorType = "";
        private String errorMessage = "";
        private String stackTrace = "";
        private RecentTraceStore.IncidentCause primaryCause;
        private RecentTraceStore.IncidentCause slowCause;
        private RecentTraceStore.IncidentCause errorCause;

        private synchronized void appendTree(String additionalTree) {
            if (additionalTree == null || additionalTree.isBlank()) return;
            tree = tree == null || tree.isBlank() ? additionalTree
                    : tree + System.lineSeparator() + "--- async callable ---" + System.lineSeparator() + additionalTree;
        }

        private synchronized void merge(TraceContext.Completion completion) {
            if (completion == null) return;
            appendTree(completion.tree());
            incidentSlow |= completion.slow();
            incidentError |= completion.error();
            addReasons(completion.reasons());
            captureError(completion.errorType(), completion.errorMessage(), completion.stackTrace());
        }

        private synchronized void mergeDeferred(TraceContext.DeferredCompletion completion) {
            if (completion == null) return;
            incidentSlow |= completion.slow();
            incidentError |= completion.error();
            addReasons(completion.reasons());
            captureError(completion.errorType(), completion.errorMessage(), completion.stackTrace());
            if (deferredTraceSegments == null) deferredTraceSegments = new ArrayList<>(2);
            if (deferredTraceSegments.size() < MAX_DEFERRED_TRACE_SEGMENTS) {
                deferredTraceSegments.add(completion);
            }
        }

        private synchronized void merge(OperationObservation.Completion completion) {
            if (completion == null) return;
            incidentSlow |= completion.slow();
            incidentError |= completion.error();
            addReasons(completion.reasons());
            captureError(completion.errorType(), completion.errorMessage(), completion.stackTrace());
            primaryCause = completion.primaryCause();
            slowCause = completion.slowCause();
            errorCause = completion.errorCause();
        }

        private synchronized void addReason(String reason) {
            if (reason == null || reason.isBlank()) return;
            if (incidentReasons == null) incidentReasons = new LinkedHashSet<>();
            incidentReasons.add(reason);
        }

        private void addReasons(List<String> reasons) {
            if (reasons == null || reasons.isEmpty()) return;
            if (incidentReasons == null) incidentReasons = new LinkedHashSet<>();
            incidentReasons.addAll(reasons);
        }

        private synchronized boolean tryComplete() {
            if (completed) return false;
            completed = true;
            return true;
        }

        private synchronized boolean hasIncident(boolean rootSlow, boolean rootError) {
            return rootSlow || rootError || incidentSlow || incidentError;
        }

        private synchronized IncidentEvidence evidence(boolean rootSlow, boolean rootError,
                                                        Throwable failure, int status) {
            incidentSlow |= rootSlow;
            incidentError |= rootError;
            if (failure != null) {
                if (errorType.isBlank()) {
                    captureError(failure.getClass().getName(), BoundedThrowableFormatter.message(failure),
                            BoundedThrowableFormatter.format(failure));
                }
            } else if (rootError && errorType.isBlank()) {
                captureError("HTTP_ERROR", "HTTP status " + status, "");
            }
            if (incidentSlow || incidentError) materializeDeferredTrees();
            return new IncidentEvidence(tree, incidentSlow, incidentError,
                    incidentReasons == null ? List.of() : List.copyOf(incidentReasons),
                    errorType, errorMessage, stackTrace, primaryCause, slowCause, errorCause);
        }

        private synchronized BoundedThrowableFormatter.FailureEvidence failureEvidence() {
            return errorType.isBlank()
                    ? BoundedThrowableFormatter.FailureEvidence.EMPTY
                    : new BoundedThrowableFormatter.FailureEvidence(errorType, errorMessage, stackTrace);
        }

        private void materializeDeferredTrees() {
            if (deferredTraceSegments == null) return;
            deferredTraceSegments.forEach(segment -> appendTree(segment.tree()));
            deferredTraceSegments = null;
        }

        private void captureError(String type, String message, String stack) {
            if (!errorType.isBlank() || type == null || type.isBlank()) return;
            errorType = type;
            errorMessage = message == null ? "" : message;
            stackTrace = stack == null ? "" : stack;
        }
    }

    /**
     * Spring MVC wraps a {@link Error} thrown by a synchronous handler in a servlet exception before invoking
     * {@code afterCompletion}. Preserve the fatal cause in diagnosis data so OOM and similar failures do not appear
     * only as a generic dispatch exception. Normal exception chains retain their original top-level exception.
     */
    private static Throwable diagnosticFailure(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (current instanceof Error) return current;
            Throwable cause = current.getCause();
            if (cause == current) break;
            current = cause;
        }
        return failure;
    }

    private record IncidentEvidence(String tree, boolean slow, boolean error, List<String> reasons,
                                    String errorType, String errorMessage, String stackTrace,
                                    RecentTraceStore.IncidentCause primaryCause,
                                    RecentTraceStore.IncidentCause slowCause,
                                    RecentTraceStore.IncidentCause errorCause) { }

    private record AsyncWorkerBinding(RequestState state, MdcContext.Scope mdcScope,
                                      boolean traceStarted, boolean resourceBound) { }

    private final class AsyncCompletionListener implements AsyncListener {
        private final HttpServletRequest request;
        private final HttpServletResponse response;
        private final RequestState state;

        private AsyncCompletionListener(HttpServletRequest request, HttpServletResponse response, RequestState state) {
            this.request = request;
            this.response = response;
            this.state = state;
        }

        @Override
        public void onComplete(AsyncEvent event) { complete(request, response, state, null, true); }

        @Override
        public void onTimeout(AsyncEvent event) {
            complete(request, response, state, new TimeoutException("HTTP async request timed out"), true);
        }

        @Override
        public void onError(AsyncEvent event) { complete(request, response, state, event.getThrowable(), true); }

        @Override
        public void onStartAsync(AsyncEvent event) throws IOException {
            try { state.observation.markAsyncWaiting(); }
            catch (Throwable monitoringFailure) { state.observation.discard(); }
            try { event.getAsyncContext().addListener(this); }
            catch (Throwable ignoredMonitoringFailure) { }
        }
    }

}
