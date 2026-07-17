package com.laker.admin.module.batch.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.laker.admin.module.batch.entity.BatchTaskItem;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class BatchTaskItemView {
    private Long id;
    private Long taskId;
    private String itemKey;
    private String itemName;
    private String workType;
    private String status;
    private Integer attemptCount;
    private String workerId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime leaseUntil;
    private String inputJson;
    private String errorMessage;
    private String resultMessage;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime finishedAt;
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    public static BatchTaskItemView from(BatchTaskItem item) {
        if (item == null) {
            return null;
        }
        return BatchTaskItemView.builder()
                .id(item.getId())
                .taskId(item.getTaskId())
                .itemKey(item.getItemKey())
                .itemName(item.getItemName())
                .workType(item.getWorkType())
                .status(item.getStatus())
                .attemptCount(item.getAttemptCount() == null ? 0 : item.getAttemptCount())
                .workerId(item.getWorkerId())
                .leaseUntil(item.getLeaseUntil())
                .inputJson(item.getInputJson())
                .errorMessage(item.getErrorMessage())
                .resultMessage(item.getResultMessage())
                .startedAt(item.getStartedAt())
                .finishedAt(item.getFinishedAt())
                .remark(item.getRemark())
                .createTime(item.getCreateTime())
                .updateTime(item.getUpdateTime())
                .build();
    }
}
