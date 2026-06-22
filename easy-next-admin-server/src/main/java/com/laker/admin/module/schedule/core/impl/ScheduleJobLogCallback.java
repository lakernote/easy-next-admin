package com.laker.admin.module.schedule.core.impl;

import com.laker.admin.common.constant.EasyNextAdminConstants;
import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.infrastructure.observability.trace.EasyTraceIdContext;
import com.laker.admin.infrastructure.observability.trace.SpanType;
import com.laker.admin.infrastructure.observability.trace.TraceContext;
import com.laker.admin.module.schedule.core.ScheduleJobCallback;
import com.laker.admin.module.schedule.core.ScheduleJobDefinition;
import com.laker.admin.module.schedule.entity.ScheduleJobLog;
import com.laker.admin.module.schedule.service.IScheduleJobLogService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(prefix = "easy.features", name = "scheduler", havingValue = "true", matchIfMissing = true)
@Slf4j
public class ScheduleJobLogCallback implements ScheduleJobCallback {
    private final ThreadLocal<ScheduleJobLog> threadLocal = new ThreadLocal<>();
    private final ThreadLocal<Exception> exceptionThreadLocal = new ThreadLocal<>();
    private final IScheduleJobLogService scheduleJobLogService;
    private final EasyNextAdminConfig easyNextAdminConfig;

    public ScheduleJobLogCallback(IScheduleJobLogService scheduleJobLogService,
                                  EasyNextAdminConfig easyNextAdminConfig) {
        this.scheduleJobLogService = scheduleJobLogService;
        this.easyNextAdminConfig = easyNextAdminConfig;
    }

    @Override
    public void start(ScheduleJobDefinition jobDefinition) {
        exceptionThreadLocal.remove();
        EasyTraceIdContext.getOrCreateTraceId();
        long thresholdMs = easyNextAdminConfig.getTrace().getScheduleSlowThresholdMs();
        if (easyNextAdminConfig.getTrace().isEnabled() && thresholdMs > 0) {
            TraceContext.startRoot("ScheduleJob", SpanType.Schedule,
                    "code=%s,name=%s".formatted(jobDefinition.getJobCode(), jobDefinition.getJobName()),
                    easyNextAdminConfig.getTrace().getMaxDepth(),
                    easyNextAdminConfig.getTrace().getMinNodeCostMs());
        }
        // 每次执行先落一条日志，异常和结束回调基于 ThreadLocal 回填状态与耗时。
        ScheduleJobLog jobLog = new ScheduleJobLog();
        jobLog.setStartTime(LocalDateTime.now());
        jobLog.setJobCode(jobDefinition.getJobCode());
        jobLog.setStatus(1);
        jobLog.setThreadName(Thread.currentThread().getName());
        scheduleJobLogService.save(jobLog);
        threadLocal.set(jobLog);
        log.debug("Schedule job started, code={}, name={}", jobDefinition.getJobCode(), jobDefinition.getJobName());
    }

    @Override
    public void exception(ScheduleJobDefinition jobDefinition, Exception e) {
        ScheduleJobLog jobLog = threadLocal.get();
        if (jobLog == null) {
            log.error("Schedule job failed before log context was initialized, code={}, name={}",
                    jobDefinition.getJobCode(), jobDefinition.getJobName(), e);
            return;
        }
        jobLog.setStatus(2);
        exceptionThreadLocal.set(e);
        scheduleJobLogService.updateById(jobLog);
        log.error("Schedule job failed, code={}, name={}", jobDefinition.getJobCode(), jobDefinition.getJobName(), e);
    }

    @Override
    public void end(ScheduleJobDefinition jobDefinition) {
        ScheduleJobLog jobLog = threadLocal.get();
        if (jobLog == null) {
            TraceContext.clear();
            MDC.remove(EasyNextAdminConstants.TRACE_ID);
            return;
        }
        try {
            jobLog.setEndTime(LocalDateTime.now());
            jobLog.setCost((int) Duration.between(jobLog.getStartTime(), jobLog.getEndTime()).toMillis());
            scheduleJobLogService.updateById(jobLog);
            log.debug("Schedule job ended, code={}, name={}, cost={}ms",
                    jobDefinition.getJobCode(), jobDefinition.getJobName(), jobLog.getCost());
        } finally {
            Exception exception = exceptionThreadLocal.get();
            threadLocal.remove();
            exceptionThreadLocal.remove();
            if (easyNextAdminConfig.getTrace().isEnabled()) {
                TraceContext.stopRoot(easyNextAdminConfig.getTrace().getScheduleSlowThresholdMs(),
                        "Schedule job trace tree", exception);
            } else {
                TraceContext.clear();
            }
            MDC.remove(EasyNextAdminConstants.TRACE_ID);
        }
    }
}
