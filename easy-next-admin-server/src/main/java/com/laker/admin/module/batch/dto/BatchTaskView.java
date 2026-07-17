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
    private Integer runNo;
    private String triggerType;
    private String triggerRefId;
    private String paramsJson;
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
                .runNo(task.getRunNo() == null ? 1 : task.getRunNo())
                .triggerType(task.getTriggerType())
                .triggerRefId(task.getTriggerRefId())
                .paramsJson(task.getParamsJson())
                .status(task.getStatus())
                .totalCount(defaultInt(task.getTotalCount()))
                .successCount(defaultInt(task.getSuccessCount()))
                .failedCount(defaultInt(task.getFailedCount()))
                .skippedCount(defaultInt(task.getSkippedCount()))
                .progressPercent(progressPercent(task))
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

    private static int progressPercent(BatchTask task) {
        int total = defaultInt(task.getTotalCount());
        if (total <= 0) {
            return task.getFinishedAt() == null ? 0 : 100;
        }
        int completed = defaultInt(task.getSuccessCount())
                + defaultInt(task.getFailedCount())
                + defaultInt(task.getSkippedCount());
        return Math.min(100, (completed * 100) / total);
    }
}
