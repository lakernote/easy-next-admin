package com.laker.admin.infrastructure.observability.metrics;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.infrastructure.observability.remote.entity.RemoteCallLog;
import com.laker.admin.infrastructure.observability.remote.service.RemoteCallLogService;
import com.laker.admin.infrastructure.observability.jfr.OperationObservation;
import com.laker.admin.infrastructure.observability.trace.EasyTraceIdContext;
import com.laker.admin.infrastructure.observability.trace.ObservationSuppression;
import com.laker.admin.infrastructure.observability.trace.SpanType;
import com.laker.admin.infrastructure.observability.trace.TraceContext;
import com.laker.admin.infrastructure.observability.DiagnosisSchedulingConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * 远程调用运行态指标。
 *
 * <p>业务模块中的 Feign 接口、client/remote 包下的 RestClient 包装类会被记录为低基数指标，
 * 供监控页面查询真实外部调用次数、成功率和耗时。</p>
 */
@Aspect
@Component
@ConditionalOnProperty(prefix = "easy.features", name = "monitor", havingValue = "true", matchIfMissing = true)
@Slf4j
public class RemoteCallMetricsAspect {
    private final EasyBusinessMetrics businessMetrics;
    private final ObjectProvider<RemoteCallLogService> remoteCallLogServiceProvider;
    private final OperationObservation operationObservation;
    private final long slowThresholdMs;
    private final boolean remoteLogPersistenceEnabled;
    private final Executor persistenceExecutor;
    private final LongSupplier nanoTime;
    private final AtomicLong droppedPersistenceTasks = new AtomicLong();

    @Autowired
    public RemoteCallMetricsAspect(EasyBusinessMetrics businessMetrics,
                                   ObjectProvider<RemoteCallLogService> remoteCallLogServiceProvider,
                                   ObjectProvider<OperationObservation> operationObservationProvider,
                                   EasyNextAdminConfig config,
                                   @Qualifier(DiagnosisSchedulingConfiguration.PERSISTENCE_EXECUTOR)
                                   Executor persistenceExecutor) {
        this(businessMetrics, remoteCallLogServiceProvider, operationObservationProvider, config,
                persistenceExecutor, System::nanoTime);
    }

    RemoteCallMetricsAspect(EasyBusinessMetrics businessMetrics,
                            ObjectProvider<RemoteCallLogService> remoteCallLogServiceProvider,
                            ObjectProvider<OperationObservation> operationObservationProvider,
                            EasyNextAdminConfig config, Executor persistenceExecutor,
                            LongSupplier nanoTime) {
        this.businessMetrics = businessMetrics;
        this.remoteCallLogServiceProvider = remoteCallLogServiceProvider;
        this.operationObservation = operationObservationProvider.getIfAvailable();
        this.slowThresholdMs = config.getTrace().getRemoteSlowThresholdMs();
        this.remoteLogPersistenceEnabled = config.getMonitor().getDiagnosis().isRemoteLogPersistenceEnabled();
        this.persistenceExecutor = persistenceExecutor;
        this.nanoTime = nanoTime;
    }

    public RemoteCallMetricsAspect(EasyBusinessMetrics businessMetrics,
                                   ObjectProvider<RemoteCallLogService> remoteCallLogServiceProvider) {
        this.businessMetrics = businessMetrics;
        this.remoteCallLogServiceProvider = remoteCallLogServiceProvider;
        this.operationObservation = null;
        this.slowThresholdMs = new EasyNextAdminConfig().getTrace().getRemoteSlowThresholdMs();
        this.remoteLogPersistenceEnabled = false;
        this.persistenceExecutor = Runnable::run;
        this.nanoTime = System::nanoTime;
    }

    RemoteCallMetricsAspect(EasyBusinessMetrics businessMetrics,
                            ObjectProvider<RemoteCallLogService> remoteCallLogServiceProvider,
                            long slowThresholdMs) {
        this(businessMetrics, remoteCallLogServiceProvider, slowThresholdMs, Runnable::run);
    }

    RemoteCallMetricsAspect(EasyBusinessMetrics businessMetrics,
                            ObjectProvider<RemoteCallLogService> remoteCallLogServiceProvider,
                            long slowThresholdMs, Executor persistenceExecutor) {
        this(businessMetrics, remoteCallLogServiceProvider, slowThresholdMs, persistenceExecutor, true);
    }

    RemoteCallMetricsAspect(EasyBusinessMetrics businessMetrics,
                            ObjectProvider<RemoteCallLogService> remoteCallLogServiceProvider,
                            long slowThresholdMs, Executor persistenceExecutor,
                            boolean remoteLogPersistenceEnabled) {
        this.businessMetrics = businessMetrics;
        this.remoteCallLogServiceProvider = remoteCallLogServiceProvider;
        this.operationObservation = null;
        this.slowThresholdMs = slowThresholdMs;
        this.remoteLogPersistenceEnabled = remoteLogPersistenceEnabled;
        this.persistenceExecutor = persistenceExecutor;
        this.nanoTime = System::nanoTime;
    }

    @Pointcut("@within(org.springframework.cloud.openfeign.FeignClient) " +
            "|| execution(public * com.laker.admin.module..client..*.*(..)) " +
            "|| execution(public * com.laker.admin.module..remote..*.*(..))")
    public void remoteCall() {
        // pointcut only
    }

    @Around("remoteCall()")
    public Object measure(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String target = signature.getDeclaringType().getSimpleName();
        String method = signature.getMethod().getName();
        OperationObservation.Scope operation = startOperation(target + "#" + method);
        String traceId = EasyTraceIdContext.currentTraceId();
        boolean traceActive = TraceContext.active();
        boolean traceSpanStarted = false;
        if (traceActive) {
            try {
                TraceContext.addSpan(target + "#" + method, SpanType.Remote);
                traceSpanStarted = true;
            } catch (Throwable monitoringFailure) {
                // A partially-created span can leave the per-thread tree unbalanced. Drop that tree and keep the
                // remote call itself available; operation metrics and the parent traceId still remain usable.
                TraceContext.clear();
                safeMonitoringFailureLog(target, method, monitoringFailure);
            }
        }
        if (operation != null) operation.beginBusinessBoundary();
        if (traceSpanStarted) TraceContext.beginCurrentSpanBoundary();
        // Start at the actual outbound-call boundary. Operation/span setup is monitoring work and must not make a
        // fast call look slow or inflate the Micrometer remote-call timer.
        long startNanos = nanoTime.getAsLong();
        Throwable failure = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable ex) {
            failure = ex;
            throw ex;
        } finally {
            long durationNanos = Math.max(0, nanoTime.getAsLong() - startNanos);
            if (operation != null) operation.endBusinessBoundary();
            Throwable monitoringFailure = null;
            try {
                if (traceSpanStarted) TraceContext.stopSpan();
            } catch (Throwable exception) {
                monitoringFailure = exception;
                TraceContext.clear();
            }
            try {
                // Freeze resource counters at the outbound-call boundary. Metric/detail persistence below is
                // monitoring work and must not inflate the remote call's CPU/allocation values.
                if (operation != null) operation.finish(failure == null ? "success" : "failure", failure);
            } catch (Throwable exception) {
                if (monitoringFailure == null) monitoringFailure = exception;
                safeDiscard(operation);
            }
            try {
                try (ObservationSuppression.Scope ignored = ObservationSuppression.enter()) {
                    record(traceId, target, method, durationNanos, failure == null, failure);
                }
            } catch (Throwable exception) {
                if (monitoringFailure == null) monitoringFailure = exception;
            }
            if (monitoringFailure != null) {
                retainOriginalFailure(failure, monitoringFailure);
                safeMonitoringFailureLog(target, method, monitoringFailure);
            }
        }
    }

    private OperationObservation.Scope startOperation(String name) {
        if (operationObservation == null) return null;
        try {
            return operationObservation.start("REMOTE", name, "outbound", false);
        } catch (Throwable exception) {
            safeMonitoringFailureLog(name, "start", exception);
            return null;
        }
    }

    private void record(String traceId, String target, String method, long durationNanos,
                        boolean success, Throwable error) {
        businessMetrics.recordRemoteCall(target, method, success, durationNanos);
        long durationMs = TimeUnit.NANOSECONDS.toMillis(durationNanos);
        boolean slow = slowThresholdMs > 0 && durationMs >= slowThresholdMs;
        if (remoteLogPersistenceEnabled && (!success || slow)) {
            persist(traceId, target, method, success, durationMs, error);
        }
    }

    private void persist(String traceId, String target, String method, boolean success,
                         long durationMs, Throwable error) {
        RemoteCallLogService remoteCallLogService = remoteCallLogServiceProvider.getIfAvailable();
        if (remoteCallLogService == null) {
            return;
        }
        RemoteCallLog remoteCallLog = new RemoteCallLog();
        remoteCallLog.setTraceId(traceId);
        remoteCallLog.setTarget(target);
        remoteCallLog.setMethod(method);
        remoteCallLog.setSuccess(success);
        remoteCallLog.setDurationMs(durationMs);
        remoteCallLog.setErrorMessage(error == null ? null : truncate(error.getMessage(), 500));
        remoteCallLog.setCreateTime(LocalDateTime.now());
        try {
            persistenceExecutor.execute(() -> persistSafely(remoteCallLogService, remoteCallLog));
        } catch (RuntimeException ex) {
            long dropped = droppedPersistenceTasks.incrementAndGet();
            if (dropped == 1 || dropped % 1_000 == 0) {
                log.warn("远程调用诊断写入队列已满或关闭，累计丢弃 {} 条兼容明细", dropped);
            }
        }
    }

    private void persistSafely(RemoteCallLogService remoteCallLogService, RemoteCallLog remoteCallLog) {
        try (ObservationSuppression.Scope ignored = ObservationSuppression.enter()) {
            remoteCallLogService.save(remoteCallLog);
        } catch (RuntimeException ex) {
            // 允许老环境先上线代码后补 SQL；统计接口会退回 Micrometer 运行态数据。
            log.debug("远程调用日志持久化失败，可能尚未创建 observability_remote_call_log 表", ex);
        }
    }

    private static void retainOriginalFailure(Throwable businessFailure, Throwable monitoringFailure) {
        if (businessFailure == null || businessFailure instanceof VirtualMachineError
                || monitoringFailure instanceof VirtualMachineError) return;
        try { businessFailure.addSuppressed(monitoringFailure); }
        catch (Throwable ignored) { }
    }

    private static void safeDiscard(OperationObservation.Scope operation) {
        if (operation == null) return;
        try { operation.discard(); }
        catch (Throwable ignored) { }
    }

    private static void safeMonitoringFailureLog(String target, String method, Throwable failure) {
        try {
            if (!(failure instanceof VirtualMachineError)) {
                log.debug("远程调用运行诊断失败，已保留原业务结果: {}#{}, type={}",
                        target, method, failure.getClass().getName());
            }
        } catch (Throwable ignored) { }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
