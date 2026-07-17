package com.laker.admin.module.schedule.dto;

import com.laker.admin.module.schedule.enums.JobExecutionMode;
import com.laker.admin.module.schedule.enums.JobStateEnum;
import com.laker.admin.module.schedule.entity.ScheduleJob;
import lombok.Data;

/**
 * 定时任务保存请求。只接收页面可维护字段，避免请求体绑定持久化审计字段。
 */
@Data
public class ScheduleJobRequest {
    private static final int DEFAULT_LOCK_LEASE_SECONDS = 300;
    private static final int MIN_LOCK_LEASE_SECONDS = 30;
    private static final int MAX_LOCK_LEASE_SECONDS = 3600;

    private Long jobId;
    private String jobCode;
    private String jobName;
    private String jobClassName;
    private String cronExpression;
    private Integer lockLeaseSeconds;
    private JobExecutionMode executionMode;
    private Boolean enable;
    private JobStateEnum jobState;
    private String remark;

    public ScheduleJob toEntity() {
        ScheduleJob job = new ScheduleJob();
        job.setJobId(jobId);
        job.setJobCode(jobCode);
        job.setJobName(jobName);
        job.setJobClassName(jobClassName);
        job.setCronExpression(cronExpression);
        job.setLockLeaseSeconds(normalizeLockLeaseSeconds(lockLeaseSeconds));
        job.setExecutionMode(JobExecutionMode.normalize(executionMode));
        job.setEnable(enable == null || Boolean.TRUE.equals(enable));
        job.setJobState(jobState == null ? JobStateEnum.STOP : jobState);
        job.setRemark(remark);
        return job;
    }

    private Integer normalizeLockLeaseSeconds(Integer seconds) {
        if (seconds == null || seconds <= 0) {
            return DEFAULT_LOCK_LEASE_SECONDS;
        }
        return Math.max(MIN_LOCK_LEASE_SECONDS, Math.min(MAX_LOCK_LEASE_SECONDS, seconds));
    }
}
