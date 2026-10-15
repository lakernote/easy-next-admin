package com.laker.admin.module.schedule.core.impl;

import com.laker.admin.common.constant.EasyNextAdminConstants;
import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.infrastructure.observability.metrics.EasyBusinessMetrics;
import com.laker.admin.infrastructure.observability.trace.BoundedThrowableFormatter;
import com.laker.admin.infrastructure.observability.trace.EasyTraceIdContext;
import com.laker.admin.infrastructure.observability.trace.SpanType;
import com.laker.admin.infrastructure.observability.trace.TraceContext;
import com.laker.admin.infrastructure.observability.trace.RecentTraceStore;
import com.laker.admin.infrastructure.observability.trace.ObservationSuppression;
import com.laker.admin.infrastructure.observability.jfr.OperationObservation;
import com.laker.admin.module.schedule.core.ScheduleJobCallback;
import com.laker.admin.module.schedule.core.ScheduleJobDefinition;
import com.laker.admin.module.schedule.core.ScheduleJobRunContext;
import com.laker.admin.module.schedule.entity.ScheduleJobLog;
import com.laker.admin.module.schedule.service.IScheduleJobLogService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "easy.features", name = "scheduler", havingValue = "true", matchIfMissing = true)
@Slf4j
public class ScheduleJobLogCallback implements ScheduleJobCallback {
    private final ThreadLocal<ScheduleJobLog> threadLocal = new ThreadLocal<>();
    private final ThreadLocal<Throwable> exceptionThreadLocal = new ThreadLocal<>();
    private final IScheduleJobLogService scheduleJobLogService;
    private final EasyNextAdminConfig easyNextAdminConfig;
    private final EasyBusinessMetrics businessMetrics;
    private final OperationObservation operationObservation;
    private final RecentTraceStore recentTraceStore;
    private final ThreadLocal<OperationObservation.Scope> operationScope = new ThreadLocal<>();

    @Autowired
    public ScheduleJobLogCallback(IScheduleJobLogService scheduleJobLogService,
                                  EasyNextAdminConfig easyNextAdminConfig,
                                  EasyBusinessMetrics businessMetrics,
                                  OperationObservation operationObservation,
                                  RecentTraceStore recentTraceStore) {
        this.scheduleJobLogService = scheduleJobLogService;
        this.easyNextAdminConfig = easyNextAdminConfig;
        this.businessMetrics = businessMetrics;
        this.operationObservation = operationObservation;
        this.recentTraceStore = recentTraceStore;
    }

    public ScheduleJobLogCallback(IScheduleJobLogService scheduleJobLogService,
                                  EasyNextAdminConfig easyNextAdminConfig,
                                  EasyBusinessMetrics businessMetrics) {
        this(scheduleJobLogService, easyNextAdminConfig, businessMetrics, null, null);
    }

    @Override
    public void start(ScheduleJobDefinition jobDefinition) {
        exceptionThreadLocal.remove();
        String traceId = EasyTraceIdContext.getOrCreateTraceId();
        // 每次执行先落一条日志，异常和结束回调基于 ThreadLocal 回填状态与耗时。
        ScheduleJobLog jobLog = new ScheduleJobLog();
        jobLog.setStartTime(LocalDateTime.now());
        jobLog.setJobCode(jobDefinition.getJobCode());
        jobLog.setTraceId(traceId);
        ScheduleJobRunContext runContext = ScheduleJobRunContext.current();
        if (runContext != null) {
            jobLog.setRunId(runContext.getRunId());
            jobLog.setInstanceId(runContext.getInstanceId());
            jobLog.setLockToken(runContext.getLockToken());
            jobLog.setTriggerTime(runContext.getTriggerTime());
        }
        jobLog.setStatus(1);
        jobLog.setThreadName(Thread.currentThread().getName());
        try (ObservationSuppression.Scope ignored = ObservationSuppression.enter()) {
            scheduleJobLogService.save(jobLog);
        } catch (Throwable monitoringFailure) {
            safeMonitoringFailure("保存任务执行日志", jobDefinition, monitoringFailure);
        }
        threadLocal.set(jobLog);
        boolean monitoringEnabled = easyNextAdminConfig.getFeatures().isMonitor();
        if (monitoringEnabled && operationObservation != null) {
            operationScope.set(operationObservation.start("JOB", jobDefinition.getJobCode()));
        }
        if (monitoringEnabled && easyNextAdminConfig.getTrace().isEnabled()) {
            try {
                TraceContext.startRoot("ScheduleJob", SpanType.Schedule,
                        "code=%s,name=%s".formatted(jobDefinition.getJobCode(), jobDefinition.getJobName()),
                        easyNextAdminConfig.getTrace().getMaxDepth(),
                        easyNextAdminConfig.getTrace().getMinNodeCostMs());
            } catch (Throwable monitoringFailure) {
                TraceContext.clear();
                safeMonitoringFailure("启动任务 Trace", jobDefinition, monitoringFailure);
            }
        }
        OperationObservation.Scope scope = operationScope.get();
        if (scope != null) scope.beginBusinessBoundary();
        if (TraceContext.active()) TraceContext.beginCurrentSpanBoundary();
        // The handler starts after this callback returns; exclude diagnostic setup and log persistence from job cost.
        jobLog.setStartTime(LocalDateTime.now());
        log.debug("Schedule job started, code={}, name={}", jobDefinition.getJobCode(), jobDefinition.getJobName());
    }

    @Override
    public void exception(ScheduleJobDefinition jobDefinition, Throwable failure) {
        exceptionThreadLocal.set(failure);
        // The handler has stopped. Freeze counters before formatting or logging its failure.
        OperationObservation.Scope scope = operationScope.get();
        try {
            if (scope != null) scope.endBusinessBoundary();
            if (TraceContext.active()) TraceContext.freezeRootBoundary();
            if (scope != null) scope.finish("failure", failure);
        } catch (Throwable monitoringFailure) {
            if (scope != null) scope.discard();
        }
        ScheduleJobLog jobLog = threadLocal.get();
        if (jobLog == null) {
            safeFailureSummary(jobDefinition, failure, true);
            return;
        }
        jobLog.setStatus(2);
        BoundedThrowableFormatter.FailureEvidence evidence = scope == null ? null : scope.failureEvidence();
        String message = evidence != null && !evidence.message().isBlank()
                ? evidence.message() : safeFailureMessage(failure);
        jobLog.setErrorMessage(limitMessage(message));
        safeFailureSummary(jobDefinition, failure, false);
    }

    @Override
    public void end(ScheduleJobDefinition jobDefinition) {
        ScheduleJobLog jobLog = threadLocal.get();
        Throwable exception = exceptionThreadLocal.get();
        try {
            OperationObservation.Scope scope = operationScope.get();
            if (jobLog != null) {
                jobLog.setEndTime(LocalDateTime.now());
                jobLog.setCost((int) Duration.between(jobLog.getStartTime(), jobLog.getEndTime()).toMillis());
            }
            if (scope != null) scope.endBusinessBoundary();
            if (TraceContext.active()) TraceContext.freezeRootBoundary();
            if (scope != null) scope.finish(exception == null && jobLog != null ? "success" : "failure", exception);
            TraceContext.Completion[] traceCompletion = new TraceContext.Completion[1];
            long thresholdMs = easyNextAdminConfig.getTrace().getScheduleSlowThresholdMs();
            boolean monitoringEnabled = easyNextAdminConfig.getFeatures().isMonitor();
            if (monitoringEnabled && easyNextAdminConfig.getTrace().isEnabled()) {
                boolean rootSlow = jobLog != null && thresholdMs > 0
                        && jobLog.getCost() != null && jobLog.getCost() >= thresholdMs;
                if (rootSlow) TraceContext.markSlow("SLOW_JOB");
                if (exception != null) {
                    BoundedThrowableFormatter.FailureEvidence failureEvidence =
                            scope == null ? null : scope.failureEvidence();
                    if (failureEvidence != null && failureEvidence.present()) {
                        TraceContext.markError("JOB_ERROR", failureEvidence);
                    } else {
                        TraceContext.markError("JOB_ERROR", exception);
                    }
                }
                TraceContext.stopRoot(thresholdMs, "Schedule job trace tree", exception,
                        (TraceContext.Completion completion) -> traceCompletion[0] = completion);
            } else {
                TraceContext.clear();
            }
            if (monitoringEnabled && recentTraceStore != null && jobLog != null) {
                long costMs = Math.max(0, jobLog.getCost() == null ? 0 : jobLog.getCost());
                boolean rootSlow = thresholdMs > 0 && costMs >= thresholdMs;
                boolean rootError = exception != null;
                OperationObservation.Completion operationCompletion = scope == null ? null : scope.completion();
                if (isIncident(traceCompletion[0], operationCompletion, rootSlow, rootError)) {
                    IncidentEvidence evidence = incidentEvidence(traceCompletion[0], operationCompletion,
                            rootSlow, rootError, exception,
                            rootSlow ? "SLOW_JOB" : null, rootError ? "JOB_ERROR" : null);
                    recentTraceStore.recordIncident("JOB", jobLog.getTraceId(), "ScheduleJob",
                            jobDefinition.getJobCode(), rootError ? 500 : 200, evidence.tree(), costMs,
                            evidence.slow(), evidence.error(), evidence.reasons(), evidence.errorType(),
                            evidence.errorMessage(), evidence.stackTrace(), evidence.primaryCause(),
                            evidence.slowCause(), evidence.errorCause(),
                            scope == null ? jobLog.getEndTime() : scope.finishedAt());
                }
            }
            if (jobLog != null) {
                try (ObservationSuppression.Scope ignored = ObservationSuppression.enter()) {
                    scheduleJobLogService.updateById(jobLog);
                }
                businessMetrics.recordScheduleJob(jobDefinition.getJobCode(), exception == null, jobLog.getCost());
                log.debug("Schedule job ended, code={}, name={}, cost={}ms",
                        jobDefinition.getJobCode(), jobDefinition.getJobName(), jobLog.getCost());
            }
        } finally {
            threadLocal.remove();
            exceptionThreadLocal.remove();
            operationScope.remove();
            MDC.remove(EasyNextAdminConstants.TRACE_ID);
        }
    }

    private static boolean isIncident(TraceContext.Completion trace, OperationObservation.Completion operation,
                                      boolean rootSlow, boolean rootError) {
        return rootSlow || rootError
                || trace != null && (trace.slow() || trace.error())
                || operation != null && (operation.slow() || operation.error());
    }

    private String limitMessage(String message) {
        if (message == null || message.length() <= 1000) {
            return message;
        }
        return message.substring(0, 1000);
    }

    private static void safeFailureSummary(ScheduleJobDefinition definition, Throwable failure,
                                           boolean missingContext) {
        try {
            if (!(failure instanceof VirtualMachineError)) {
                log.error("Schedule job failed, code={}, name={}, exception={}, missingLogContext={}",
                        definition.getJobCode(), definition.getJobName(), failure.getClass().getName(), missingContext);
            }
        } catch (Throwable ignored) { }
    }

    private static void safeMonitoringFailure(String action, ScheduleJobDefinition definition, Throwable failure) {
        try {
            if (!(failure instanceof VirtualMachineError)) {
                log.debug("{}失败，不影响任务执行: code={}, type={}", action,
                        definition.getJobCode(), failure.getClass().getName());
            }
        } catch (Throwable ignored) { }
    }

    private static String safeFailureMessage(Throwable failure) {
        try {
            String message = BoundedThrowableFormatter.message(failure);
            return message == null || message.isBlank() ? failure.getClass().getName() : message;
        } catch (Throwable ignored) {
            return failure == null ? "" : failure.getClass().getName();
        }
    }

    private static IncidentEvidence incidentEvidence(TraceContext.Completion trace,
                                                     OperationObservation.Completion operation,
                                                     boolean rootSlow, boolean rootError, Throwable failure,
                                                     String slowReason, String errorReason) {
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        if (trace != null) reasons.addAll(trace.reasons());
        if (operation != null) reasons.addAll(operation.reasons());
        if (slowReason != null) reasons.add(slowReason);
        if (errorReason != null) reasons.add(errorReason);
        String errorType = first(trace == null ? "" : trace.errorType(),
                operation == null ? "" : operation.errorType());
        String errorMessage = first(trace == null ? "" : trace.errorMessage(),
                operation == null ? "" : operation.errorMessage());
        String stackTrace = first(trace == null ? "" : trace.stackTrace(),
                operation == null ? "" : operation.stackTrace());
        if (failure != null) {
            if (errorType.isBlank()) errorType = failure.getClass().getName();
            if (errorMessage.isBlank()) errorMessage = BoundedThrowableFormatter.message(failure);
            if (stackTrace.isBlank()) stackTrace = BoundedThrowableFormatter.format(failure);
        }
        return new IncidentEvidence(trace == null ? "" : trace.tree(),
                rootSlow || trace != null && trace.slow() || operation != null && operation.slow(),
                rootError || trace != null && trace.error() || operation != null && operation.error(),
                List.copyOf(reasons), errorType, errorMessage, stackTrace,
                operation == null ? null : operation.primaryCause(),
                operation == null ? null : operation.slowCause(),
                operation == null ? null : operation.errorCause());
    }

    private static String first(String first, String second) {
        return first != null && !first.isBlank() ? first : second == null ? "" : second;
    }

    private record IncidentEvidence(String tree, boolean slow, boolean error, List<String> reasons,
                                    String errorType, String errorMessage, String stackTrace,
                                    RecentTraceStore.IncidentCause primaryCause,
                                    RecentTraceStore.IncidentCause slowCause,
                                    RecentTraceStore.IncidentCause errorCause) { }
}
