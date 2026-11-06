package com.laker.admin.module.schedule.core.impl;

import com.laker.admin.infrastructure.observability.metrics.EasyAdminMetrics;
import com.laker.admin.module.schedule.core.ScheduleJobCallback;
import com.laker.admin.module.schedule.core.ScheduleJobDefinition;
import com.laker.admin.module.schedule.core.ScheduleJobRunContext;
import com.laker.admin.module.schedule.entity.ScheduleJobLog;
import com.laker.admin.module.schedule.service.IScheduleJobLogService;
import io.github.lakernote.meerkat.trace.TraceIdContext;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(prefix = "easy.features", name = "scheduler", havingValue = "true", matchIfMissing = true)
@Slf4j
public class ScheduleJobLogCallback implements ScheduleJobCallback {
    private final ThreadLocal<ScheduleJobLog> jobLogContext = new ThreadLocal<>();
    private final ThreadLocal<Throwable> failureContext = new ThreadLocal<>();
    private final ThreadLocal<Observation> observationContext = new ThreadLocal<>();
    private final ThreadLocal<Observation.Scope> observationScopeContext = new ThreadLocal<>();
    private final IScheduleJobLogService scheduleJobLogService;
    private final EasyAdminMetrics businessMetrics;
    private final ObservationRegistry observationRegistry;

    public ScheduleJobLogCallback(IScheduleJobLogService scheduleJobLogService,
                                  EasyAdminMetrics businessMetrics,
                                  ObservationRegistry observationRegistry) {
        this.scheduleJobLogService = scheduleJobLogService;
        this.businessMetrics = businessMetrics;
        this.observationRegistry = observationRegistry;
    }

    @Override
    public void start(ScheduleJobDefinition jobDefinition) {
        failureContext.remove();
        Observation observation = Observation.createNotStarted("tasks.scheduled.execution", observationRegistry)
                .lowCardinalityKeyValue("code.function", jobDefinition.getJobCode())
                .contextualName(jobDefinition.getJobCode())
                .start();
        observationContext.set(observation);
        observationScopeContext.set(observation.openScope());

        String traceId = TraceIdContext.currentTraceId();
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
        try {
            scheduleJobLogService.save(jobLog);
        } catch (Throwable persistenceFailure) {
            log.debug("保存任务执行日志失败，不影响任务执行: code={}, type={}",
                    jobDefinition.getJobCode(), persistenceFailure.getClass().getName());
        }
        jobLogContext.set(jobLog);
        jobLog.setStartTime(LocalDateTime.now());
        log.debug("Schedule job started, code={}, name={}", jobDefinition.getJobCode(), jobDefinition.getJobName());
    }

    @Override
    public void exception(ScheduleJobDefinition jobDefinition, Throwable failure) {
        failureContext.set(failure);
        Observation observation = observationContext.get();
        if (observation != null) observation.error(failure);
        ScheduleJobLog jobLog = jobLogContext.get();
        if (jobLog == null) {
            safeFailureSummary(jobDefinition, failure, true);
            return;
        }
        jobLog.setStatus(2);
        jobLog.setErrorMessage(limitMessage(safeFailureMessage(failure)));
        safeFailureSummary(jobDefinition, failure, false);
    }

    @Override
    public void end(ScheduleJobDefinition jobDefinition) {
        ScheduleJobLog jobLog = jobLogContext.get();
        Throwable failure = failureContext.get();
        try {
            if (jobLog != null) {
                jobLog.setEndTime(LocalDateTime.now());
                jobLog.setCost((int) Math.max(0, Duration.between(jobLog.getStartTime(), jobLog.getEndTime()).toMillis()));
            }
            Observation observation = observationContext.get();
            if (observation != null && failure != null) observation.error(failure);
            Observation.Scope observationScope = observationScopeContext.get();
            if (observationScope != null) observationScope.close();
            if (observation != null) observation.stop();

            if (jobLog != null) {
                try {
                    scheduleJobLogService.updateById(jobLog);
                } catch (Throwable persistenceFailure) {
                    log.debug("更新任务执行日志失败，不影响任务结果: code={}, type={}",
                            jobDefinition.getJobCode(), persistenceFailure.getClass().getName());
                }
                businessMetrics.recordScheduleJob(jobDefinition.getJobCode(), failure == null, jobLog.getCost());
                log.debug("Schedule job ended, code={}, name={}, cost={}ms",
                        jobDefinition.getJobCode(), jobDefinition.getJobName(), jobLog.getCost());
            }
        } finally {
            jobLogContext.remove();
            failureContext.remove();
            observationContext.remove();
            observationScopeContext.remove();
        }
    }

    private static String limitMessage(String message) {
        return message == null || message.length() <= 1000 ? message : message.substring(0, 1000);
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

    private static String safeFailureMessage(Throwable failure) {
        try {
            String message = failure.getMessage();
            return message == null || message.isBlank() ? failure.getClass().getName() : message;
        } catch (Throwable ignored) {
            return failure == null ? "" : failure.getClass().getName();
        }
    }
}
