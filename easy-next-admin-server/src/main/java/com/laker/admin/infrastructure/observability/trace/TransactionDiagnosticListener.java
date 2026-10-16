package com.laker.admin.infrastructure.observability.trace;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.infrastructure.observability.jfr.OperationObservation;
import com.laker.observability.trace.TraceIdContext;
import com.laker.observability.trace.ObservationSuppression;
import com.laker.observability.trace.SpanKind;
import com.laker.observability.trace.TraceContext;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.time.LocalDateTime;
import java.util.function.LongSupplier;

/** Captures actual transaction boundaries from Spring's transaction manager callbacks. */
@Component
@ConditionalOnProperty(prefix = "easy.features", name = "monitor", havingValue = "true", matchIfMissing = true)
@Slf4j
public class TransactionDiagnosticListener implements TransactionExecutionListener {
    private final RecentTraceStore store;
    private final OperationObservation operationObservation;
    private final long slowThresholdMs;
    private final LongSupplier nanoTime;
    private final ThreadLocal<Map<TransactionExecution, State>> states = ThreadLocal.withInitial(IdentityHashMap::new);

    @Autowired
    public TransactionDiagnosticListener(RecentTraceStore store, MeterRegistry meterRegistry,
                                          OperationObservation operationObservation,
                                          EasyNextAdminConfig config) {
        this(store, meterRegistry, operationObservation, config, System::nanoTime);
    }

    TransactionDiagnosticListener(RecentTraceStore store, MeterRegistry meterRegistry,
                                   OperationObservation operationObservation,
                                   EasyNextAdminConfig config, LongSupplier nanoTime) {
        this.store = store;
        this.operationObservation = operationObservation;
        this.slowThresholdMs = config.getTrace().getTransactionSlowThresholdMs();
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    @Override
    public void beforeBegin(TransactionExecution execution) {
        if (ObservationSuppression.active()) return;
        try {
            String currentTraceId = TraceIdContext.currentTraceId();
            boolean ownsTraceId = currentTraceId == null || currentTraceId.isBlank();
            String traceId = ownsTraceId ? TraceIdContext.getOrCreateTraceId() : currentTraceId;
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
            state.observation = operationObservation.startDiagnosticOnly("TRANSACTION", state.name, detail);
            state.observation.beginBusinessBoundary();
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
            long duration = Math.max(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(nanoTime.getAsLong() - state.started), 0);
            if (state.observation != null) state.observation.endBusinessBoundary();
            LocalDateTime finishedAt = state.observation == null
                    ? LocalDateTime.now() : state.observation.finishedAt();
            // The real transaction has completed. Freeze local JFR diagnostics at this boundary.
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
            if (!success || slow) runSafely("保存事务诊断样本", state,
                    () -> store.recordTransaction(state.traceId, state.name, success, duration, finishedAt));
        } finally {
            if (state.ownsTraceId && Objects.equals(state.traceId, TraceIdContext.currentTraceId())) {
                TraceIdContext.clear();
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
        if (state.transactionStarted) {
            try {
                store.transactionFinished();
            } catch (Throwable exception) {
                safeDebug("清理事务活跃计数失败", state.name, exception);
            }
        }
        if (state.ownsTraceId && Objects.equals(state.traceId, TraceIdContext.currentTraceId())) {
            TraceIdContext.clear();
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

    private static final class State {
        private long started;
        private final String name;
        private final String traceId;
        private boolean transactionStarted;
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
