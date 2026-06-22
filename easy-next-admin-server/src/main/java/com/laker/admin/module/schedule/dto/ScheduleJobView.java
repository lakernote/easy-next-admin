package com.laker.admin.module.schedule.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.laker.admin.module.enums.JobStateEnum;
import com.laker.admin.module.schedule.entity.ScheduleJob;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务对外响应模型，隐藏审计列、软删标记和乐观锁等内部字段。
 */
@Data
@Builder
public class ScheduleJobView {
    private Long jobId;
    private String jobCode;
    private String jobName;
    private String jobClassName;
    private String cronExpression;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    private Boolean enable;
    private JobStateEnum jobState;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
    private String remark;

    public static ScheduleJobView from(ScheduleJob job) {
        if (job == null) {
            return null;
        }
        return ScheduleJobView.builder()
                .jobId(job.getJobId())
                .jobCode(job.getJobCode())
                .jobName(job.getJobName())
                .jobClassName(job.getJobClassName())
                .cronExpression(job.getCronExpression())
                .createTime(job.getCreateTime())
                .enable(job.getEnable())
                .jobState(job.getJobState())
                .updateTime(job.getUpdateTime())
                .remark(job.getRemark())
                .build();
    }

    public static List<ScheduleJobView> fromList(List<ScheduleJob> jobs) {
        return jobs == null ? List.of() : jobs.stream().map(ScheduleJobView::from).toList();
    }
}
