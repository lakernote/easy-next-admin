package com.laker.admin.module.schedule.dto;

import com.laker.admin.module.schedule.enums.JobStateEnum;
import com.laker.admin.module.schedule.entity.ScheduleJob;
import lombok.Data;

/**
 * 定时任务保存请求。只接收页面可维护字段，避免请求体绑定持久化审计字段。
 */
@Data
public class ScheduleJobRequest {
    private Long jobId;
    private String jobCode;
    private String jobName;
    private String jobClassName;
    private String cronExpression;
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
        job.setEnable(enable);
        job.setJobState(jobState);
        job.setRemark(remark);
        return job;
    }
}
