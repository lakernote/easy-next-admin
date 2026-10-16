package com.laker.observability.integration.transaction;

import com.laker.observability.config.EasyObservabilityProperties;
import com.laker.observability.metrics.OperationRecorder;
import com.laker.observability.trace.BoundedThrowableFormatter;
import com.laker.observability.trace.OperationRecord;
import com.laker.observability.trace.SpanKind;
import com.laker.observability.trace.ThreadResourceSnapshot;
import com.laker.observability.trace.TraceContext;
import com.laker.observability.trace.TraceHistory;
import com.laker.observability.trace.TraceArchive;
import com.laker.observability.trace.TraceIdContext;
import com.laker.observability.trace.ObservationSuppression;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;

import java.time.Instant;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/** Records Spring transaction boundaries without proxying application services. */
public final class TransactionTraceListener implements TransactionExecutionListener {
    private final OperationRecorder recorder;
    private final TraceHistory history;
    private final TraceArchive archive;
    private final EasyObservabilityProperties properties;
    private final AtomicLong activeTransactions = new AtomicLong();
    private final Map<TransactionMetricKey, Timer> transactionTimers;
    private final LongSupplier nanoTime;
    private final ThreadLocal<Map<TransactionExecution, State>> states =
            ThreadLocal.withInitial(IdentityHashMap::new);

    public TransactionTraceListener(OperationRecorder recorder, TraceHistory history, MeterRegistry meterRegistry,
                                    EasyObservabilityProperties properties) {
        this(recorder, history, null, meterRegistry, properties, System::nanoTime);
    }

    TransactionTraceListener(OperationRecorder recorder, TraceHistory history, MeterRegistry meterRegistry,
                             EasyObservabilityProperties properties, LongSupplier nanoTime) {
        this(recorder, history, null, meterRegistry, properties, nanoTime);
    }

    public TransactionTraceListener(OperationRecorder recorder, TraceHistory history, TraceArchive archive,
                                    MeterRegistry meterRegistry, EasyObservabilityProperties properties) {
        this(recorder, history, archive, meterRegistry, properties, System::nanoTime);
    }

    TransactionTraceListener(OperationRecorder recorder, TraceHistory history, TraceArchive archive,
                             MeterRegistry meterRegistry, EasyObservabilityProperties properties,
                             LongSupplier nanoTime) {
        this.recorder = recorder;
        this.history = history;
        this.archive = archive;
        this.properties = properties;
        this.nanoTime = nanoTime;
        if (properties.getMetrics().isEnabled()) {
            meterRegistry.gauge("easy.transactions.active", activeTransactions, AtomicLong::get);
        }
        this.transactionTimers = registerTransactionTimers(meterRegistry, properties.getMetrics().isEnabled());
    }

    @Override
    public void beforeBegin(TransactionExecution execution) {
        if (ObservationSuppression.active()) return;
        try {
            String currentId = TraceIdContext.currentTraceId();
            boolean tracing = properties.getTrace().isEnabled();
            boolean ownsTraceId = tracing && (currentId == null || currentId.isBlank());
            String traceId = !tracing ? currentId : ownsTraceId ? TraceIdContext.getOrCreateTraceId() : currentId;
            states.get().put(execution, new State(traceId, ownsTraceId, tracing,
                    safeName(execution.getTransactionName()), execution.isReadOnly(), nanoTime.getAsLong()));
        } catch (Throwable ignored) {
            states.get().remove(execution);
        }
    }

    @Override
    public void afterBegin(TransactionExecution execution, Throwable failure) {
        State state = states.get().get(execution);
        if (state == null) return;
        if (failure != null) {
            finish(execution, state, false, failure, false);
            return;
        }
        try {
            state.startedNanos = nanoTime.getAsLong();
            state.threadId = Thread.currentThread().getId();
            state.threadName = Thread.currentThread().getName();
            state.resources = ThreadResourceSnapshot.capture(state.threadId,
                    properties.getTrace().isResourceSamplingEnabled());
            if (state.tracing) {
                state.root = !TraceContext.active();
                if (state.root) TraceContext.startRoot(state.name, SpanKind.TRANSACTION,
                        state.readOnly ? "read-only" : "write", 8, 0);
                else state.span = TraceContext.startDetachedSpan(state.name, SpanKind.TRANSACTION,
                        state.readOnly ? "read-only" : "write");
            }
            state.started = true;
            state.countedActive = true;
            activeTransactions.incrementAndGet();
        } catch (Throwable ignored) {
            // Instrumentation must never change the transaction result.
        }
    }

    @Override
    public void afterCommit(TransactionExecution execution, Throwable failure) {
        finish(execution, states.get().get(execution), failure == null, failure, true);
    }

    @Override
    public void afterRollback(TransactionExecution execution, Throwable failure) {
        finish(execution, states.get().get(execution), false, failure, true);
    }

    private void finish(TransactionExecution execution, State state, boolean success, Throwable failure,
                        boolean transactionStarted) {
        if (state == null) return;
        states.get().remove(execution);
        try {
            long durationMs = Math.max(0, TimeUnit.NANOSECONDS.toMillis(nanoTime.getAsLong()
                    - (state.started ? state.startedNanos : state.createdNanos)));
            boolean slow = properties.getTransaction().getSlowThresholdMs() > 0
                    && durationMs >= properties.getTransaction().getSlowThresholdMs();
            if (state.tracing && slow) TraceContext.markSlow("LONG_TRANSACTION");
            if (state.tracing && !success && failure != null) TraceContext.markError("TRANSACTION_ERROR", failure);
            if (state.span != null) state.span.run();

            ThreadResourceSnapshot end = transactionStarted
                    ? ThreadResourceSnapshot.capture(state.threadId, properties.getTrace().isResourceSamplingEnabled())
                    : null;
            BoundedThrowableFormatter.FailureEvidence error = failure == null ? null
                    : BoundedThrowableFormatter.capture(failure);
            recorder.record(new OperationRecord("TRANSACTION", state.traceId, state.name,
                    state.threadName == null ? Thread.currentThread().getName() : state.threadName,
                    state.threadId, success ? "success" : "failure", durationMs,
                    delta(end, state.resources, Resource.CPU), delta(end, state.resources, Resource.ALLOCATED),
                    delta(end, state.resources, Resource.BLOCKED_COUNT), delta(end, state.resources, Resource.BLOCKED_TIME),
                    delta(end, state.resources, Resource.WAITED_COUNT), delta(end, state.resources, Resource.WAITED_TIME),
                    state.readOnly ? "read-only" : "write", "", error == null ? "" : error.stackTrace(),
                    slow, !success, slow ? List.of("LONG_TRANSACTION") : List.of(),
                    error == null ? "" : error.type(), error == null ? "" : error.message(), Map.of(), Instant.now()));
            Timer transactionTimer = transactionTimers.get(new TransactionMetricKey(success, state.readOnly));
            if (transactionTimer != null) transactionTimer.record(durationMs, TimeUnit.MILLISECONDS);

            if (state.root) {
                TraceContext.stopRoot(properties.getTransaction().getSlowThresholdMs(), "transaction", failure,
                        completion -> {
                            boolean traceSlow = slow || completion.slow();
                            boolean traceError = !success || completion.error();
                            history.add(state.traceId, state.name,
                                    state.readOnly ? "read-only" : "write", "TRANSACTION", success ? 200 : 500,
                                    durationMs, traceSlow, traceError, completion.tree());
                            if (archive != null && (traceSlow || traceError)) {
                                try { archive.record(history.find(state.traceId)); }
                                catch (Throwable ignored) { }
                            }
                        });
            }
        } catch (Throwable ignored) {
            if (state.root) TraceContext.clear();
        } finally {
            if (state.countedActive) activeTransactions.updateAndGet(value -> Math.max(0, value - 1));
            if (state.ownsTraceId && state.traceId.equals(TraceIdContext.currentTraceId())) TraceIdContext.clear();
            if (states.get().isEmpty()) states.remove();
        }
    }

    private static long delta(ThreadResourceSnapshot end, ThreadResourceSnapshot start, Resource resource) {
        if (end == null || start == null) return -1;
        return switch (resource) {
            case CPU -> end.cpuTimeMsSince(start);
            case ALLOCATED -> end.allocatedBytesSince(start);
            case BLOCKED_COUNT -> end.blockedCountSince(start);
            case BLOCKED_TIME -> end.blockedTimeMsSince(start);
            case WAITED_COUNT -> end.waitedCountSince(start);
            case WAITED_TIME -> end.waitedTimeMsSince(start);
        };
    }

    private static String safeName(String name) {
        return name == null || name.isBlank() ? "transaction" : name.length() > 200 ? name.substring(0, 200) : name;
    }

    private static Map<TransactionMetricKey, Timer> registerTransactionTimers(MeterRegistry registry,
                                                                              boolean enabled) {
        if (!enabled) return Map.of();
        Map<TransactionMetricKey, Timer> timers = new LinkedHashMap<>();
        for (boolean success : new boolean[]{true, false}) {
            for (boolean readOnly : new boolean[]{true, false}) {
                TransactionMetricKey key = new TransactionMetricKey(success, readOnly);
                timers.put(key, Timer.builder("easy.transactions")
                        .description("Spring transaction count and duration")
                        .tag("result", success ? "success" : "failure")
                        .tag("read_only", Boolean.toString(readOnly)).register(registry));
            }
        }
        return Map.copyOf(timers);
    }

    private record TransactionMetricKey(boolean success, boolean readOnly) { }

    private enum Resource { CPU, ALLOCATED, BLOCKED_COUNT, BLOCKED_TIME, WAITED_COUNT, WAITED_TIME }

    private static final class State {
        private final String traceId;
        private final boolean ownsTraceId;
        private final String name;
        private final boolean readOnly;
        private final boolean tracing;
        private final long createdNanos;
        private long startedNanos;
        private long threadId;
        private String threadName;
        private ThreadResourceSnapshot resources;
        private TraceContext.DetachedSpan span;
        private boolean root;
        private boolean started;
        private boolean countedActive;
        private State(String traceId, boolean ownsTraceId, boolean tracing, String name, boolean readOnly,
                      long createdNanos) {
            this.traceId = traceId;
            this.ownsTraceId = ownsTraceId;
            this.tracing = tracing;
            this.name = name;
            this.readOnly = readOnly;
            this.createdNanos = createdNanos;
        }
    }
}
