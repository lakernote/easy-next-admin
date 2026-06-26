package com.laker.admin.module.batch.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.laker.admin.module.batch.entity.BatchTask;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class BatchTaskView {
    private Long id;
    private String taskType;
    private String taskName;
    private String businessKey;
    private String triggerType;
    private String triggerRefId;
    private String status;
    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
    private Integer skippedCount;
    private Integer progressPercent;
    private Boolean cancelRequested;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime finishedAt;
    private String traceId;
    private String errorMessage;
    private String resultMessage;
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    public static BatchTaskView from(BatchTask task) {
        if (task == null) {
            return null;
        }
        return BatchTaskView.builder()
                .id(task.getId())
                .taskType(task.getTaskType())
                .taskName(task.getTaskName())
                .businessKey(task.getBusinessKey())
                .triggerType(task.getTriggerType())
                .triggerRefId(task.getTriggerRefId())
                .status(task.getStatus())
                .totalCount(defaultInt(task.getTotalCount()))
                .successCount(defaultInt(task.getSuccessCount()))
                .failedCount(defaultInt(task.getFailedCount()))
                .skippedCount(defaultInt(task.getSkippedCount()))
                .progressPercent(defaultInt(task.getProgressPercent()))
                .cancelRequested(Boolean.TRUE.equals(task.getCancelRequested()))
                .startedAt(task.getStartedAt())
                .finishedAt(task.getFinishedAt())
                .traceId(task.getTraceId())
                .errorMessage(task.getErrorMessage())
                .resultMessage(task.getResultMessage())
                .remark(task.getRemark())
                .createTime(task.getCreateTime())
                .updateTime(task.getUpdateTime())
                .build();
    }

    private static int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }
}
