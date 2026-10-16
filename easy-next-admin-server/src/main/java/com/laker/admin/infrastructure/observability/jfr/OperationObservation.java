package com.laker.admin.infrastructure.observability.jfr;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.observability.trace.BoundedThrowableFormatter;
import com.laker.observability.trace.TraceIdContext;
import com.laker.admin.infrastructure.observability.trace.RecentTraceStore;
import com.laker.observability.trace.TraceContext;
import com.laker.observability.metrics.OperationRecorder;
import com.laker.observability.trace.OperationRecord;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Supplier;

@Component
@Slf4j
public class OperationObservation {
    private static final Set<String> AGGREGATE_ROOT_TYPES = Set.of("HTTP", "JOB", "KAFKA");
    private static final List<String> METRIC_TYPES = List.of(
            "HTTP", "JOB", "KAFKA", "TRANSACTION", "SQL", "REMOTE", "OTHER");
    private static final List<String> METRIC_OUTCOMES = List.of("success", "failure");
    private final RecentTraceStore store;
    private final EasyNextAdminConfig config;
    private final ActiveOperationRegistry activeOperations;
    private final MeterRegistry meterRegistry;
    /** Resolved once at construction; checking JFR state must stay a volatile read on the per-operation hot path. */
    private final LocalJfrRecorder jfrRecorder;
    private final boolean configurationOnlyJfrFallback;
    private final OperationRecorder operationRecorder;
    /** One lazily registered meter set for each bounded type/outcome pair. */
    private final AtomicReferenceArray<OperationMeters> operationMeters;
    /** Root ownership follows the executing thread so a client-reused trace id cannot merge concurrent requests. */
    private final ThreadLocal<Scope> aggregateRootContext = new ThreadLocal<>();

    @Autowired
    public OperationObservation(RecentTraceStore store, EasyNextAdminConfig config,
                                ActiveOperationRegistry activeOperations, MeterRegistry meterRegistry,
                                ObjectProvider<LocalJfrRecorder> jfrRecorderProvider,
                                OperationRecorder operationRecorder) {
        this.store = store;
        this.config = config;
        this.activeOperations = activeOperations;
        this.meterRegistry = meterRegistry;
        this.jfrRecorder = jfrRecorderProvider.getIfAvailable();
        this.configurationOnlyJfrFallback = false;
        this.operationRecorder = operationRecorder;
        this.operationMeters = meterRegistry == null ? null
                : new AtomicReferenceArray<>(METRIC_TYPES.size() * METRIC_OUTCOMES.size());
    }

    public OperationObservation(RecentTraceStore store, EasyNextAdminConfig config,
                                ActiveOperationRegistry activeOperations, MeterRegistry meterRegistry,
                                ObjectProvider<LocalJfrRecorder> jfrRecorderProvider) {
        this(store, config, activeOperations, meterRegistry, jfrRecorderProvider, null);
    }

    OperationObservation(RecentTraceStore store, EasyNextAdminConfig config,
                         ActiveOperationRegistry activeOperations) {
        this.store = store;
        this.config = config;
        this.activeOperations = activeOperations;
        this.meterRegistry = null;
        this.jfrRecorder = null;
        this.configurationOnlyJfrFallback = true;
        this.operationRecorder = null;
        this.operationMeters = null;
    }

    public Scope start(String type, String name) {
        return start(type, name, "", false);
    }

    /**
     * Starts an operation observation. High-volume operations such as SQL can retain only slow/error detail while
     * still contributing active-operation state, Micrometer metrics and root child summaries. JFR events are an
     * optional extra: disabling the local JFR recorder does not disable configured MXBean resource accounting or
     * JSONL samples.
     */
    public Scope start(String type, String name, String detail, boolean persistAll) {
        return start(type, name, detail, null, persistAll);
    }

    /**
     * Starts an operation with cheap detail for the active list and a deferred incident detail. The supplier runs
     * only when the operation is retained (slow, failed or persist-all). A normal fast SQL operation keeps only its
     * cheap command/mapper identity in JFR, so enabling advanced diagnostics does not add BoundSql parsing and SQL
     * normalization to every database call.
     */
    public Scope start(String type, String name, String activeDetail, Supplier<String> incidentDetail,
                       boolean persistAll) {
        return start(type, name, activeDetail, incidentDetail, persistAll, true);
    }

    /** Starts local JFR/diagnostic capture while leaving metrics and trace recording to the reusable starter. */
    public Scope startDiagnosticOnly(String type, String name, String detail) {
        return start(type, name, detail, null, false, false);
    }

    private Scope start(String type, String name, String activeDetail, Supplier<String> incidentDetail,
                        boolean persistAll, boolean recordGeneric) {
        if (!config.getFeatures().isMonitor()
                || !config.getMonitor().getDiagnosis().isEnabled()) {
            return new Scope();
        }
        String currentTraceId = TraceIdContext.currentTraceId();
        boolean ownsTraceId = currentTraceId == null || currentTraceId.isBlank();
        String traceId = ownsTraceId ? TraceIdContext.getOrCreateTraceId() : currentTraceId;
        RuntimeOperationJfrEvent event = null;
        ActiveOperationRegistry.Scope activeScope = null;
        Scope scope = null;
        try {
            boolean jfrRecording = jfrRecorder == null
                    ? configurationOnlyJfrFallback && config.getMonitor().getDiagnosis().isJfrEnabled()
                    : jfrRecorder.isRecording();
            if (jfrRecording) {
                event = new RuntimeOperationJfrEvent();
                event.type = type;
                event.traceId = traceId;
                event.operation = name;
                event.detail = activeDetail;
            }
            activeScope = activeOperations.start(type, traceId, name, activeDetail);
            boolean aggregateRoot = AGGREGATE_ROOT_TYPES.contains(type) && traceId != null && !traceId.isBlank();
            Scope previousRoot = aggregateRoot ? aggregateRootContext.get() : null;
            Scope parentRoot = aggregateRoot ? null : aggregateRootContext.get();
            scope = new Scope(type, name, Thread.currentThread().getName(), System.nanoTime(), event,
                    activeScope, traceId, activeDetail, incidentDetail, persistAll, aggregateRoot, parentRoot,
                    previousRoot, ownsTraceId, recordGeneric);
            if (aggregateRoot) aggregateRootContext.set(scope);
            return scope;
        } catch (Throwable exception) {
            if (scope != null) {
                scope.discard();
            } else {
                try { if (activeScope != null) activeScope.discard(); } catch (Throwable ignored) { }
                try { if (event != null) event.end(); } catch (Throwable ignored) { }
            }
            if (ownsTraceId && java.util.Objects.equals(traceId, TraceIdContext.currentTraceId())) {
                TraceIdContext.clear();
            }
            safeStartFailure(type, name, exception);
            return new Scope();
        }
    }

    /**
     * Captures the current request/job/message root so application-managed worker threads can keep resource
     * accounting and child-operation summaries attached to the same operation. The snapshot deliberately carries
     * only the observation root; callers remain responsible for propagating trace and security contexts.
     */
    public ContextSnapshot captureContext() {
        Scope root = aggregateRootContext.get();
        return new ContextSnapshot(root != null && !root.finished ? root : null);
    }

    /**
     * Retains a failed operation that never crossed its real execution boundary. Transaction begin failures are the
     * primary caller: they still need an error incident and parent correlation, but starting a normal scope before
     * the transaction exists would make connection acquisition look like transaction CPU and elapsed time.
     */
    public Completion recordUnstartedFailure(String type, String name, String traceId, String detail,
                                             long durationMs, Throwable failure) {
        if (!config.getFeatures().isMonitor() || !config.getMonitor().getDiagnosis().isEnabled()) {
            return Completion.none();
        }
        String reason = errorReason(type);
        String errorType = failure == null ? reason : failure.getClass().getName();
        String errorMessage = failure == null ? type + " 启动失败" : BoundedThrowableFormatter.message(failure);
        String stackTrace = BoundedThrowableFormatter.format(failure);
        long safeDuration = Math.max(0, durationMs);
        String safeDetail = detail == null ? "" : detail;
        RecentTraceStore.IncidentCause errorCause = new RecentTraceStore.IncidentCause(
                type, name, safeDetail, safeDuration, persistedThreshold(threshold(type, config)),
                false, true, reason);
        Completion result = new Completion(false, true, List.of(reason), errorType, errorMessage, stackTrace,
                errorCause, null, errorCause);
        Scope parentRoot = aggregateRootContext.get();
        if (parentRoot != null) parentRoot.recordChild(type, safeDuration, result, failure);
        try {
            if (operationRecorder != null) {
                operationRecorder.record(new OperationRecord(type, traceId, name, Thread.currentThread().getName(),
                        Thread.currentThread().getId(), "failure", safeDuration, -1, -1, -1, -1, -1, -1,
                        safeDetail, "", stackTrace, false, true, result.reasons(), errorType, errorMessage,
                        Map.of(), java.time.Instant.now()));
            } else {
                recordResourceMetrics(type, "failure", safeDuration,
                        new ActiveOperationRegistry.ResourceUsage(-1, -1, -1, -1, -1, -1));
            }
            store.recordOperation(new RecentTraceStore.OperationSample(type, traceId, name,
                    Thread.currentThread().getName(), "failure", safeDuration, -1, -1, -1, -1, -1, -1,
                    safeDetail, "", stackTrace, Map.of(), java.time.LocalDateTime.now()));
            if (parentRoot == null) {
                store.recordIncident(type, traceId, name, safeDetail, 500, type + " " + name,
                        safeDuration, false, true, result.reasons(), errorType, errorMessage, stackTrace,
                        errorCause, null, errorCause);
            }
        } catch (RuntimeException exception) {
            log.debug("运行诊断启动失败记录异常，不影响业务操作: {} {}", type, name, exception);
        }
        return result;
    }

    public final class ContextSnapshot {
        private final Scope root;

        private ContextSnapshot(Scope root) {
            this.root = root;
        }

        /**
         * Binds the captured root to the current worker only after the servlet thread has released its segment.
         * Monitoring must never delay business work, so a worker that wins this race simply runs without per-thread
         * resource attribution for that segment.
         */
        public ContextBinding bind() {
            Scope previousRoot = aggregateRootContext.get();
            if (root == null) return new ContextBinding(null, previousRoot, false);
            boolean resourceBound = root.rebindAfterInitialAsyncHandoff();
            Scope boundRoot = aggregateRootContext.get() == root ? root : null;
            return new ContextBinding(boundRoot, previousRoot, resourceBound);
        }
    }

    public final class ContextBinding implements AutoCloseable {
        private final Scope root;
        private final Scope previousRoot;
        private final boolean resourceBound;
        private final AtomicBoolean closed = new AtomicBoolean();

        private ContextBinding(Scope root, Scope previousRoot, boolean resourceBound) {
            this.root = root;
            this.previousRoot = previousRoot;
            this.resourceBound = resourceBound;
        }

        boolean resourceBound() {
            return resourceBound;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true) || root == null) return;
            root.releaseAsyncBinding(resourceBound);
            if (previousRoot != null && previousRoot != root && !previousRoot.finished) {
                aggregateRootContext.set(previousRoot);
            }
        }
    }

    public final class Scope {
        private final String type;
        private final String name;
        private final String threadName;
        private final long threadId;
        private volatile long startNanos;
        private final RuntimeOperationJfrEvent jfrEvent;
        private final ActiveOperationRegistry.Scope activeScope;
        private final String traceId;
        private final String detail;
        private final Supplier<String> incidentDetail;
        private final boolean persistAll;
        private final boolean aggregateRoot;
        private final Scope parentRoot;
        private final Scope previousRoot;
        private final boolean ownsTraceId;
        private final boolean recordGeneric;
        /** Root-only aggregation state. Ordinary SQL/remote/transaction scopes never allocate these collections. */
        private Map<String, MutableChildSummary> childSummaries;
        /** Allocated only after this scope or one of its children becomes slow or fails. */
        private Set<String> incidentReasons;
        /** Lifecycle and child aggregation mutations are serialized on this scope's intrinsic monitor. */
        private volatile boolean finished;
        private boolean initialAsyncHandoffReleased;
        /** Only one worker may own the single-thread resource counter segment at a time. */
        private boolean resourceSegmentBound;
        private boolean childSlow;
        private boolean childError;
        private String incidentErrorType = "";
        private String incidentErrorMessage = "";
        private String incidentStackTrace = "";
        private RecentTraceStore.IncidentCause primarySlowCause;
        private RecentTraceStore.IncidentCause primaryErrorCause;
        private volatile Completion completion = Completion.none();
        /** Root wrappers reuse this payload instead of walking the same Throwable stack again. */
        private volatile BoundedThrowableFormatter.FailureEvidence failureEvidence =
                BoundedThrowableFormatter.FailureEvidence.EMPTY;
        private volatile long businessEndNanos = -1;
        private volatile java.time.LocalDateTime businessFinishedAt;
        /** Guarded by this scope. JFR timing starts at the same explicit boundary as wall/resource accounting. */
        private boolean businessBoundaryStarted;
        private boolean jfrEventBegun;
        private boolean jfrEventEnded;

        private Scope() {
            this.type = "";
            this.name = "";
            this.threadName = "";
            this.threadId = -1;
            this.startNanos = System.nanoTime();
            this.jfrEvent = null;
            this.activeScope = null;
            this.traceId = "";
            this.detail = "";
            this.incidentDetail = null;
            this.persistAll = false;
            this.aggregateRoot = false;
            this.parentRoot = null;
            this.previousRoot = null;
            this.ownsTraceId = false;
            this.recordGeneric = false;
            this.initialAsyncHandoffReleased = true;
            this.resourceSegmentBound = false;
        }

        private Scope(String type, String name, String threadName, long startNanos,
                      RuntimeOperationJfrEvent jfrEvent,
                      ActiveOperationRegistry.Scope activeScope, String traceId, String detail,
                      Supplier<String> incidentDetail, boolean persistAll, boolean aggregateRoot,
                      Scope parentRoot, Scope previousRoot, boolean ownsTraceId, boolean recordGeneric) {
            this.type = type;
            this.name = name;
            this.threadName = threadName;
            this.threadId = Thread.currentThread().getId();
            this.startNanos = startNanos;
            this.jfrEvent = jfrEvent;
            this.activeScope = activeScope;
            this.traceId = traceId;
            this.detail = detail;
            this.incidentDetail = incidentDetail;
            this.persistAll = persistAll;
            this.aggregateRoot = aggregateRoot;
            this.parentRoot = parentRoot;
            this.previousRoot = previousRoot;
            this.ownsTraceId = ownsTraceId;
            this.recordGeneric = recordGeneric;
            this.initialAsyncHandoffReleased = !aggregateRoot;
            this.resourceSegmentBound = activeScope != null;
        }

        public void finish(String outcome) { finish(outcome, null); }

        public void finish(String outcome, Throwable failure) {
            finishInternal(outcome, failure, null);
        }

        public void finishAsync(String outcome, Throwable failure) {
            finishInternal(outcome, failure, null);
        }

        public void finishAsync(String outcome, Throwable failure,
                                BoundedThrowableFormatter.FailureEvidence existingEvidence) {
            finishInternal(outcome, failure, existingEvidence);
        }

        public Completion completion() {
            return completion;
        }

        public BoundedThrowableFormatter.FailureEvidence failureEvidence() {
            return failureEvidence;
        }

        /** Marks the exact boundary after monitoring setup and immediately before application code executes. */
        public synchronized void beginBusinessBoundary() {
            if (finished || businessBoundaryStarted) return;
            try {
                if (activeScope != null) activeScope.rebaseCurrentThread();
            } catch (Throwable exception) {
                safeStartFailure(type, name, exception);
            } finally {
                startNanos = System.nanoTime();
                businessBoundaryStarted = true;
                beginJfrEventSafely();
            }
        }

        /** Freezes wall time before trace rendering, stack capture, metrics, or persistence start. */
        public synchronized void endBusinessBoundary() {
            if (!finished && businessEndNanos < 0) {
                businessEndNanos = System.nanoTime();
                businessFinishedAt = java.time.LocalDateTime.now();
                // Compatibility callers that do not mark an explicit start still get a valid (near-zero) event.
                // Production wrappers all call beginBusinessBoundary immediately before application code.
                beginJfrEventSafely();
                endJfrEventSafely();
            }
        }

        public java.time.LocalDateTime finishedAt() {
            return businessFinishedAt;
        }

        /** Best-effort, allocation-light cleanup used when monitoring finalization itself fails. */
        public void discard() {
            synchronized (this) {
                finished = true;
                resourceSegmentBound = false;
                endJfrEventSafely();
                notifyAll();
            }
            try {
                if (activeScope != null) activeScope.discard();
            } catch (Throwable ignored) {
                // Preserve the business result even when the JVM is already under resource pressure.
            }
            if (aggregateRoot && aggregateRootContext.get() == this) aggregateRootContext.remove();
            if (ownsTraceId && java.util.Objects.equals(traceId, TraceIdContext.currentTraceId())) {
                TraceIdContext.clear();
            }
        }

        public synchronized void markAsyncWaiting() {
            if (finished) return;
            if (resourceSegmentBound && activeScope != null) activeScope.markAsyncWaiting();
            resourceSegmentBound = false;
            if (aggregateRoot && aggregateRootContext.get() == this) aggregateRootContext.remove();
            if (aggregateRoot) {
                initialAsyncHandoffReleased = true;
                notifyAll();
            }
        }

        public synchronized void rebindCurrentThread() {
            if (finished || resourceSegmentBound) return;
            if (activeScope != null) activeScope.rebindCurrentThread();
            resourceSegmentBound = true;
            if (aggregateRoot) aggregateRootContext.set(this);
        }

        /**
         * Attempts the servlet-to-worker handoff without waiting. The scope monitor prevents a late worker from
         * reviving a scope that is finishing.
         */
        public boolean rebindAfterInitialAsyncHandoff() {
            synchronized (this) {
                if (finished) return false;
                // Keep child operation summaries correlated even when the worker wins the handoff race. Resource
                // counters remain unbound until the servlet segment has been released, so there is no overlap.
                aggregateRootContext.set(this);
                if (!initialAsyncHandoffReleased || resourceSegmentBound) return false;
                if (activeScope != null) activeScope.rebindCurrentThread();
                resourceSegmentBound = true;
                return true;
            }
        }

        public synchronized void releaseAsyncBinding(boolean resourceBound) {
            if (resourceBound && resourceSegmentBound && !finished) {
                if (activeScope != null) activeScope.markAsyncWaiting();
                resourceSegmentBound = false;
            }
            if (aggregateRoot && aggregateRootContext.get() == this) {
                aggregateRootContext.remove();
            }
        }

        private void finishInternal(String outcome, Throwable failure,
                                    BoundedThrowableFormatter.FailureEvidence existingEvidence) {
            endBusinessBoundary();
            long boundaryNanos = businessEndNanos;
            long finishedNanos = boundaryNanos >= 0 ? boundaryNanos : System.nanoTime();
            java.time.LocalDateTime finishedAt = businessFinishedAt;
            if (finishedAt == null) {
                finishedAt = java.time.LocalDateTime.now();
                businessFinishedAt = finishedAt;
            }
            synchronized (this) {
                if (finished) return;
                finished = true;
                resourceSegmentBound = false;
                notifyAll();
            }
            if (activeScope == null) return;
            long durationMs = Math.max((finishedNanos - startNanos) / 1_000_000, 0);
            boolean failed = !"success".equals(outcome);
            long configuredThreshold = threshold(type, config);
            boolean ownSlow = durationMs >= configuredThreshold;
            boolean detailed = ownSlow || failed || childSlow || childError;
            // A slow/error child already captured its own causal stack and locks. Capturing the parent again at the
            // end of an otherwise fast/successful root adds cost and usually shows only framework unwinding.
            // endBusinessBoundary already froze wall/JFR time. finishAndClose now freezes CPU/allocation before it
            // takes the optional stack/lock snapshot, so monitoring finalization never looks like business work.
            ActiveOperationRegistry.FinishedOperation finishedOperation = activeScope.finishAndClose(ownSlow || failed);
            ActiveOperationRegistry.ThreadDiagnostic diagnostic = finishedOperation.diagnostic();
            ActiveOperationRegistry.ResourceUsage resources = finishedOperation.resources();
            String completedDetail = resolveDetail(persistAll || detailed);
            if (ownSlow) {
                String reason = slowReason(type);
                addIncidentReason(reason);
                considerSlowCause(new RecentTraceStore.IncidentCause(type, name, completedDetail, durationMs,
                        persistedThreshold(configuredThreshold), true, failed, reason));
                if (!aggregateRoot) TraceContext.markSlow(reason);
            }
            if (failed) {
                String reason = errorReason(type);
                addIncidentReason(reason);
                considerErrorCause(new RecentTraceStore.IncidentCause(type, name, completedDetail, durationMs,
                        persistedThreshold(configuredThreshold), ownSlow, true, reason));
                failureEvidence = existingEvidence != null && existingEvidence.present()
                        ? existingEvidence : BoundedThrowableFormatter.capture(failure);
                captureFailure(type, failure, failureEvidence);
                if (!aggregateRoot) TraceContext.markError(reason, failureEvidence);
            }
            boolean incidentSlow = ownSlow || childSlow;
            boolean incidentError = failed || childError;
            RecentTraceStore.IncidentCause primaryCause = primaryErrorCause != null
                    ? primaryErrorCause : primarySlowCause;
            completion = incidentSlow || incidentError
                    ? new Completion(incidentSlow, incidentError, snapshotIncidentReasons(), incidentErrorType,
                            incidentErrorMessage, incidentStackTrace, primaryCause, primarySlowCause,
                            primaryErrorCause)
                    : Completion.none();
            long cpuMs = resources.cpuTimeMs();
            long allocated = resources.allocatedBytes();
            long blockedCount = resources.blockedCount();
            long blockedTime = resources.blockedTimeMs();
            long waitedCount = resources.waitedCount();
            long waitedTime = resources.waitedTimeMs();
            if (parentRoot != null) parentRoot.recordChild(type, durationMs, completion, failure);
            String lockInfo = detailed ? diagnostic.lockInfo() : "";
            String stack = !detailed ? "" : failure == null ? diagnostic.stackTrace()
                    : failureEvidence.stackTrace();
            if (recordGeneric && operationRecorder != null) {
                Map<String, OperationRecord.OperationSummary> summaries = new LinkedHashMap<>();
                snapshotChildSummaries().forEach((key, summary) -> summaries.put(key,
                        new OperationRecord.OperationSummary(summary.count(), summary.errorCount(),
                                summary.totalDurationMs(), summary.maxDurationMs())));
                java.time.Instant finishedInstant = finishedAt.atZone(java.time.ZoneId.systemDefault()).toInstant();
                operationRecorder.record(new OperationRecord(type, traceId, name, threadName, threadId, outcome,
                        durationMs, cpuMs, allocated, blockedCount, blockedTime, waitedCount, waitedTime,
                        completedDetail, lockInfo, stack, completion.slow(), completion.error(), completion.reasons(),
                        completion.errorType(), completion.errorMessage(), summaries, finishedInstant));
            } else if (recordGeneric) {
                recordResourceMetrics(type, outcome, durationMs, resources);
            }
            try {
                if (jfrEvent != null) {
                    jfrEvent.detail = completedDetail;
                    jfrEvent.outcome = outcome;
                    jfrEvent.errorType = completion.errorType();
                    jfrEvent.cpuTimeMs = cpuMs;
                    jfrEvent.allocatedBytes = allocated;
                    jfrEvent.blockedCount = blockedCount;
                    jfrEvent.waitedCount = waitedCount;
                    jfrEvent.commit();
                }
                if (persistAll || detailed) {
                    store.recordOperation(new RecentTraceStore.OperationSample(type, traceId, name, threadName,
                            outcome, durationMs, cpuMs, allocated, blockedCount, blockedTime, waitedCount,
                            waitedTime, completedDetail, lockInfo, stack, snapshotChildSummaries(),
                            finishedAt));
                }
                if (!aggregateRoot && parentRoot == null && (completion.slow() || completion.error())) {
                    store.recordIncident(type, traceId, name, completedDetail, completion.error() ? 500 : 200,
                            type + " " + name, durationMs, completion.slow(), completion.error(),
                            completion.reasons(), completion.errorType(), completion.errorMessage(),
                            completion.stackTrace(), completion.primaryCause(), completion.slowCause(),
                            completion.errorCause(), finishedAt);
                }
            } finally {
                if (aggregateRoot && aggregateRootContext.get() == this) {
                    if (previousRoot != null && !previousRoot.finished) aggregateRootContext.set(previousRoot);
                    else aggregateRootContext.remove();
                }
                try {
                    activeScope.close();
                } finally {
                    if (ownsTraceId && java.util.Objects.equals(traceId, TraceIdContext.currentTraceId())) {
                        TraceIdContext.clear();
                    }
                }
            }
        }

        private void beginJfrEventSafely() {
            if (jfrEvent == null || jfrEventBegun) return;
            try {
                jfrEvent.begin();
                jfrEventBegun = true;
            } catch (Throwable exception) {
                safeStartFailure(type, name, exception);
            }
        }

        private void endJfrEventSafely() {
            if (jfrEvent == null || !jfrEventBegun || jfrEventEnded) return;
            jfrEventEnded = true;
            try {
                jfrEvent.end();
            } catch (Throwable exception) {
                safeStartFailure(type, name, exception);
            }
        }

        private String resolveDetail(boolean needed) {
            if (!needed || incidentDetail == null) return detail;
            try {
                String resolved = incidentDetail.get();
                return resolved == null || resolved.isBlank() ? detail : resolved;
            } catch (RuntimeException exception) {
                log.debug("运行诊断延迟详情解析失败，回退到摘要: {} {}", type, name, exception);
                return detail;
            }
        }

        private synchronized void recordChild(String childType, long durationMs, Completion child,
                                              Throwable failure) {
            if (finished || childType == null || childType.isBlank()) return;
            if (childSummaries == null) childSummaries = new HashMap<>();
            childSummaries.computeIfAbsent(childType, ignored -> new MutableChildSummary())
                    .add(durationMs, child.error());
            if (child.slow()) childSlow = true;
            if (child.error()) childError = true;
            addIncidentReasons(child.reasons());
            if (child.slowCause() != null) considerSlowCause(child.slowCause());
            if (child.errorCause() != null) considerErrorCause(child.errorCause());
            if (child.error() && incidentErrorType.isBlank()) {
                if (!child.errorType().isBlank()) {
                    incidentErrorType = child.errorType();
                    incidentErrorMessage = child.errorMessage();
                    incidentStackTrace = child.stackTrace();
                } else {
                    captureFailure(childType, failure, BoundedThrowableFormatter.capture(failure));
                }
            }
        }

        private synchronized void considerSlowCause(RecentTraceStore.IncidentCause candidate) {
            if (betterCause(candidate, primarySlowCause)) primarySlowCause = candidate;
        }

        private synchronized void considerErrorCause(RecentTraceStore.IncidentCause candidate) {
            if (betterCause(candidate, primaryErrorCause)) primaryErrorCause = candidate;
        }

        private Map<String, RecentTraceStore.ChildOperationSummary> snapshotChildSummaries() {
            if (childSummaries == null || childSummaries.isEmpty()) return Map.of();
            Map<String, RecentTraceStore.ChildOperationSummary> result = new LinkedHashMap<>();
            childSummaries.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> result.put(entry.getKey(), entry.getValue().snapshot()));
            return Map.copyOf(result);
        }

        private synchronized void addIncidentReason(String reason) {
            if (reason == null || reason.isBlank()) return;
            if (incidentReasons == null) incidentReasons = new HashSet<>();
            incidentReasons.add(reason);
        }

        private synchronized void addIncidentReasons(List<String> reasons) {
            if (reasons == null || reasons.isEmpty()) return;
            if (incidentReasons == null) incidentReasons = new HashSet<>();
            incidentReasons.addAll(reasons);
        }

        private synchronized List<String> snapshotIncidentReasons() {
            return incidentReasons == null || incidentReasons.isEmpty()
                    ? List.of()
                    : incidentReasons.stream().sorted().toList();
        }

        /** Visible to focused tests without adding counters or allocations to the observed hot path. */
        int lazyStateAllocationCount() {
            int count = 0;
            if (childSummaries != null) count++;
            if (incidentReasons != null) count++;
            return count;
        }

        private synchronized void captureFailure(String failedType, Throwable failure,
                                                 BoundedThrowableFormatter.FailureEvidence evidence) {
            if (!incidentErrorType.isBlank()) return;
            if (failure == null) {
                incidentErrorType = errorReason(failedType);
                incidentErrorMessage = failedType + " 执行失败";
                return;
            }
            BoundedThrowableFormatter.FailureEvidence captured = evidence == null
                    ? BoundedThrowableFormatter.capture(failure) : evidence;
            incidentErrorType = captured.type();
            incidentErrorMessage = captured.message();
            incidentStackTrace = captured.stackTrace();
        }
    }

    private static final class MutableChildSummary {
        private long count;
        private long errorCount;
        private long totalDurationMs;
        private long maxDurationMs;

        private void add(long durationMs, boolean error) {
            count++;
            if (error) errorCount++;
            long safeDuration = Math.max(0, durationMs);
            try { totalDurationMs = Math.addExact(totalDurationMs, safeDuration); }
            catch (ArithmeticException ignored) { totalDurationMs = Long.MAX_VALUE; }
            maxDurationMs = Math.max(maxDurationMs, safeDuration);
        }

        private RecentTraceStore.ChildOperationSummary snapshot() {
            return new RecentTraceStore.ChildOperationSummary(count, errorCount, totalDurationMs, maxDurationMs);
        }
    }

    private static long threshold(String type, EasyNextAdminConfig config) {
        long configured = switch (type) {
            case "TRANSACTION" -> config.getTrace().getTransactionSlowThresholdMs();
            case "SQL" -> config.getTrace().getSqlSlowThresholdMs();
            case "REMOTE" -> config.getTrace().getRemoteSlowThresholdMs();
            case "JOB" -> config.getTrace().getScheduleSlowThresholdMs();
            case "KAFKA" -> config.getTrace().getKafkaConsumerSlowThresholdMs();
            default -> config.getTrace().getHttpSlowThresholdMs();
        };
        return configured <= 0 ? Long.MAX_VALUE : configured;
    }

    private static String slowReason(String type) {
        return switch (type) {
            case "HTTP" -> "SLOW_REQUEST";
            case "TRANSACTION" -> "LONG_TRANSACTION";
            default -> "SLOW_" + type;
        };
    }

    private static String errorReason(String type) {
        return "HTTP".equals(type) ? "ERROR" : type + "_ERROR";
    }

    public record Completion(boolean slow, boolean error, List<String> reasons,
                             String errorType, String errorMessage, String stackTrace,
                             RecentTraceStore.IncidentCause primaryCause,
                             RecentTraceStore.IncidentCause slowCause,
                             RecentTraceStore.IncidentCause errorCause) {
        private static final Completion NONE = new Completion(
                false, false, List.of(), "", "", "", null, null, null);

        public Completion {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
            errorType = errorType == null ? "" : errorType;
            errorMessage = errorMessage == null ? "" : errorMessage;
            stackTrace = stackTrace == null ? "" : stackTrace;
        }

        static Completion none() {
            return NONE;
        }
    }

    private static boolean betterCause(RecentTraceStore.IncidentCause candidate,
                                       RecentTraceStore.IncidentCause current) {
        if (candidate == null) return false;
        if (current == null) return true;
        int diagnostic = Integer.compare(diagnosticRank(candidate.type()), diagnosticRank(current.type()));
        if (diagnostic != 0) return diagnostic > 0;
        double candidateSeverity = relativeSeverity(candidate);
        double currentSeverity = relativeSeverity(current);
        int severity = Double.compare(candidateSeverity, currentSeverity);
        if (severity != 0) return severity > 0;
        if (candidate.durationMs() != current.durationMs()) return candidate.durationMs() > current.durationMs();
        int detail = Boolean.compare(!candidate.detail().isBlank(), !current.detail().isBlank());
        if (detail != 0) return detail > 0;
        return false;
    }

    private static int diagnosticRank(String type) {
        return switch (type) {
            case "SQL" -> 60;
            case "REMOTE" -> 50;
            case "TRANSACTION" -> 40;
            case "JOB", "KAFKA" -> 20;
            case "HTTP" -> 10;
            default -> 30;
        };
    }

    private static double relativeSeverity(RecentTraceStore.IncidentCause cause) {
        return cause.thresholdMs() > 0 ? (double) cause.durationMs() / cause.thresholdMs() : cause.durationMs();
    }

    private static long persistedThreshold(long value) {
        return value == Long.MAX_VALUE ? 0 : Math.max(0, value);
    }

    private void recordResourceMetrics(String type, String outcome, long durationMs,
                                       ActiveOperationRegistry.ResourceUsage resources) {
        if (operationMeters == null) return;
        try {
            OperationMeters meters = operationMeters(type, outcome);
            if (meters == null) return;
            meters.duration().record(durationMs, TimeUnit.MILLISECONDS);
            recordSummary(meters.cpuTime(), resources.cpuTimeMs());
            recordSummary(meters.allocated(), resources.allocatedBytes());
            recordSummary(meters.blockedCount(), resources.blockedCount());
            recordSummary(meters.blockedTime(), resources.blockedTimeMs());
            recordSummary(meters.waitedCount(), resources.waitedCount());
            recordSummary(meters.waitedTime(), resources.waitedTimeMs());
        } catch (RuntimeException ignored) {
            // Resource accounting must never change the observed business result.
        }
    }

    private OperationMeters operationMeters(String type, String outcome) {
        int typeIndex = metricTypeIndex(type);
        int outcomeIndex = "success".equals(outcome) ? 0 : 1;
        int index = typeIndex * METRIC_OUTCOMES.size() + outcomeIndex;
        OperationMeters cached = operationMeters.get(index);
        if (cached != null) return cached;
        synchronized (operationMeters) {
            cached = operationMeters.get(index);
            if (cached != null) return cached;
            String safeType = METRIC_TYPES.get(typeIndex);
            String safeOutcome = METRIC_OUTCOMES.get(outcomeIndex);
            cached = new OperationMeters(
                    timer("easy.operations.duration", "运行操作耗时", safeType, safeOutcome),
                    summary("easy.operations.cpu.time", "运行操作线程 CPU 时间", "milliseconds",
                            safeType, safeOutcome),
                    summary("easy.operations.allocated", "运行操作线程分配字节", "bytes",
                            safeType, safeOutcome),
                    summary("easy.operations.blocked.count", "运行操作线程阻塞次数", "events",
                            safeType, safeOutcome),
                    summary("easy.operations.blocked.time", "运行操作线程阻塞时间", "milliseconds",
                            safeType, safeOutcome),
                    summary("easy.operations.waited.count", "运行操作线程等待次数", "events",
                            safeType, safeOutcome),
                    summary("easy.operations.waited.time", "运行操作线程等待时间", "milliseconds",
                            safeType, safeOutcome));
            operationMeters.set(index, cached);
            return cached;
        }
    }

    private Timer timer(String name, String description, String type, String outcome) {
        return Timer.builder(name).description(description)
                .tag("type", type).tag("outcome", outcome).register(meterRegistry);
    }

    private DistributionSummary summary(String name, String description, String baseUnit,
                                        String type, String outcome) {
        return DistributionSummary.builder(name).description(description).baseUnit(baseUnit)
                .tag("type", type).tag("outcome", outcome).register(meterRegistry);
    }

    private static int metricTypeIndex(String type) {
        return switch (type) {
            case "HTTP" -> 0;
            case "JOB" -> 1;
            case "KAFKA" -> 2;
            case "TRANSACTION" -> 3;
            case "SQL" -> 4;
            case "REMOTE" -> 5;
            default -> 6;
        };
    }

    private static void recordSummary(DistributionSummary summary, long value) {
        if (value >= 0) summary.record(value);
    }

    private static void safeStartFailure(String type, String name, Throwable failure) {
        try {
            if (!(failure instanceof VirtualMachineError)) {
                log.debug("运行诊断操作启动失败，不影响业务操作: {} {}", type, name, failure);
            }
        } catch (Throwable ignored) {
            // Observability startup must remain fail-open, including while the JVM is under memory pressure.
        }
    }

    private record OperationMeters(Timer duration, DistributionSummary cpuTime,
                                   DistributionSummary allocated, DistributionSummary blockedCount,
                                   DistributionSummary blockedTime, DistributionSummary waitedCount,
                                   DistributionSummary waitedTime) { }
}
