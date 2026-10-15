package com.laker.admin.infrastructure.observability.trace;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.infrastructure.observability.metrics.EasyBusinessMetrics;
import com.laker.admin.infrastructure.observability.jfr.OperationObservation;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.time.LocalDateTime;

/** Captures actual transaction boundaries from Spring's transaction manager callbacks. */
@Component
@ConditionalOnProperty(prefix = "easy.features", name = "monitor", havingValue = "true", matchIfMissing = true)
@Slf4j
public class TransactionObservationListener implements TransactionExecutionListener {
    private final RecentTraceStore store;
    private final OperationObservation operationObservation;
    private final long slowThresholdMs;
    private final Map<TransactionMetricKey, Timer> transactionTimers;
    private final LongSupplier nanoTime;
    private final ThreadLocal<Map<TransactionExecution, State>> states = ThreadLocal.withInitial(IdentityHashMap::new);

    @Autowired
    public TransactionObservationListener(RecentTraceStore store, MeterRegistry meterRegistry,
                                          OperationObservation operationObservation,
                                          EasyNextAdminConfig config) {
        this(store, meterRegistry, operationObservation, config, System::nanoTime);
    }

    TransactionObservationListener(RecentTraceStore store, MeterRegistry meterRegistry,
                                   OperationObservation operationObservation,
                                   EasyNextAdminConfig config, LongSupplier nanoTime) {
        this.store = store;
        this.operationObservation = operationObservation;
        this.slowThresholdMs = config.getTrace().getTransactionSlowThresholdMs();
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        meterRegistry.gauge("easy.transactions.active", store, RecentTraceStore::activeTransactions);
        this.transactionTimers = registerTransactionTimers(meterRegistry);
    }

    @Override
    public void beforeBegin(TransactionExecution execution) {
        if (ObservationSuppression.active()) return;
        try {
            String currentTraceId = EasyTraceIdContext.currentTraceId();
            boolean ownsTraceId = currentTraceId == null || currentTraceId.isBlank();
            String traceId = ownsTraceId ? EasyTraceIdContext.getOrCreateTraceId() : currentTraceId;
            State state = new State(nanoTime.getAsLong(), execution.getTransactionName(), traceId, ownsTraceId);
            states.get().put(execution, state);
        } catch (Throwable exception) {
            discard(execution);
            safeDebug("事务观测启动失败，不影响业务事务", execution.getTransactionName(), exception);
        }
    }

    @Override
    public void afterBegin(TransactionExecution execution, Throwable failure) {
        State state = states.get().get(execution);
        if (state == null) return;
        try {
            if (failure != null) {
                finish(execution, state, false, failure);
                return;
            }
            store.transactionStarted();
            state.transactionStarted = true;
            String detail = execution.isReadOnly() ? "read-only" : "write";
            TraceContext.DetachedSpan transactionSpan =
                    TraceContext.startDetachedSpan(state.name, SpanType.Transaction, detail);
            state.stopSpan = transactionSpan;
            state.observation = operationObservation.start("TRANSACTION", state.name, detail, false);
            state.observation.beginBusinessBoundary();
            transactionSpan.beginBusinessBoundary();
            state.started = nanoTime.getAsLong();
        } catch (Throwable exception) {
            discard(execution);
            safeDebug("事务 begin 观测完成失败，不影响业务事务", state.name, exception);
        }
    }

    @Override
    public void afterCommit(TransactionExecution execution, Throwable failure) {
        finishSafely(execution, states.get().get(execution), failure == null, failure);
    }

    @Override
    public void afterRollback(TransactionExecution execution, Throwable failure) {
        finishSafely(execution, states.get().get(execution), false, failure);
    }

    private void finishSafely(TransactionExecution execution, State state, boolean success, Throwable failure) {
        try {
            finish(execution, state, success, failure);
        } catch (Throwable exception) {
            if (state != null && state.observation != null) state.observation.discard();
            TraceContext.clear();
            safeDebug("事务观测结束失败，不影响业务事务",
                    state == null ? execution.getTransactionName() : state.name, exception);
        }
    }

    private void finish(TransactionExecution execution, State state, boolean success, Throwable failure) {
        if (state == null) return;
        states.get().remove(execution);
        try {
            long duration = Math.max(TimeUnit.NANOSECONDS.toMillis(nanoTime.getAsLong() - state.started), 0);
            if (state.observation != null) state.observation.endBusinessBoundary();
            LocalDateTime finishedAt = state.observation == null
                    ? LocalDateTime.now() : state.observation.finishedAt();
            // Freeze Trace duration at the real transaction boundary before operation finalization does stack walks,
            // formats failures, updates metrics, or enqueues local history.
            runSafely("停止事务 Trace span", state, state.stopSpan);
            // The real transaction has completed. Freeze its resources before any remaining monitoring finalization.
            if (state.observation != null) {
                try {
                    state.observation.finish(success ? "success" : "failure", failure);
                } catch (Throwable exception) {
                    state.observation.discard();
                    safeDebug("完成事务资源观测", state.name, exception);
                }
            }
            if (state.transactionStarted) runSafely("更新事务活跃计数", state, store::transactionFinished);
            boolean slow = slowThresholdMs > 0 && duration >= slowThresholdMs;
            if (slow) runSafely("标记长事务", state, () -> TraceContext.markSlow("LONG_TRANSACTION"));
            if (!success) runSafely("标记事务错误", state,
                    () -> TraceContext.markError("TRANSACTION_ERROR", failure));
            if (slow || !success) runSafely("保存事务诊断样本", state,
                    () -> store.recordTransaction(state.traceId, state.name, success, duration, finishedAt));
            if (state.observation == null && !success && !state.transactionStarted) {
                String detail = execution.isReadOnly() ? "read-only" : "write";
                runSafely("记录事务 begin 失败", state,
                        () -> operationObservation.recordUnstartedFailure(
                                "TRANSACTION", state.name, state.traceId, detail, duration, failure));
            }
            runSafely("记录事务 Metrics", state, () -> transactionTimers.get(
                    new TransactionMetricKey(success, execution.isReadOnly()))
                    .record(duration, TimeUnit.MILLISECONDS));
        } finally {
            if (state.ownsTraceId && Objects.equals(state.traceId, EasyTraceIdContext.currentTraceId())) {
                EasyTraceIdContext.clear();
            }
            if (states.get().isEmpty()) states.remove();
        }
    }

    private void discard(TransactionExecution execution) {
        State state = states.get().remove(execution);
        if (state == null) return;
        if (state.observation != null) {
            try {
                // This path represents monitoring setup/finalization failure. Recording it as a transaction failure
                // would create a false incident even though the business transaction is allowed to continue.
                state.observation.discard();
            } catch (Throwable exception) {
                safeDebug("清理事务资源观测失败", state.name, exception);
            }
        }
        try {
            state.stopSpan.run();
        } catch (Throwable exception) {
            safeDebug("清理事务 Trace span 失败", state.name, exception);
        }
        if (state.transactionStarted) {
            try {
                store.transactionFinished();
            } catch (Throwable exception) {
                safeDebug("清理事务活跃计数失败", state.name, exception);
            }
        }
        if (state.ownsTraceId && Objects.equals(state.traceId, EasyTraceIdContext.currentTraceId())) {
            EasyTraceIdContext.clear();
        }
        if (states.get().isEmpty()) states.remove();
    }

    private void runSafely(String action, State state, Runnable task) {
        try {
            task.run();
        } catch (Throwable exception) {
            safeDebug(action, state.name, exception);
        }
    }

    private static void safeDebug(String action, String name, Throwable failure) {
        try {
            if (!(failure instanceof VirtualMachineError)) {
                log.debug("{}: {}, type={}", action, name, failure.getClass().getName());
            }
        } catch (Throwable ignored) { }
    }

    /** The transaction metric has exactly four legal tag combinations; register them once off the hot path. */
    private static Map<TransactionMetricKey, Timer> registerTransactionTimers(MeterRegistry meterRegistry) {
        Map<TransactionMetricKey, Timer> timers = new LinkedHashMap<>();
        for (boolean success : new boolean[]{true, false}) {
            for (boolean readOnly : new boolean[]{true, false}) {
                TransactionMetricKey key = new TransactionMetricKey(success, readOnly);
                timers.put(key, Timer.builder(EasyBusinessMetrics.TRANSACTION_TIMER)
                        .description("Spring 事务执行次数与耗时")
                        .tag("result", success ? "success" : "failure")
                        .tag("read_only", Boolean.toString(readOnly))
                        .register(meterRegistry));
            }
        }
        return Map.copyOf(timers);
    }

    private record TransactionMetricKey(boolean success, boolean readOnly) { }

    private static final class State {
        private long started;
        private final String name;
        private final String traceId;
        private boolean transactionStarted;
        private Runnable stopSpan = () -> { };
        private OperationObservation.Scope observation;
        private final boolean ownsTraceId;
        private State(long started, String name, String traceId, boolean ownsTraceId) {
            this.started = started;
            this.name = name == null || name.isBlank() ? "transaction" : name;
            this.traceId = traceId;
            this.ownsTraceId = ownsTraceId;
        }
    }
}
