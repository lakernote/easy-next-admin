package com.laker.admin.module.batch.dto;

import lombok.Data;

@Data
public class BatchTaskQuery {
    private long page = 1;
    private long limit = 10;
    private String keyword;
    private String taskType;
    private String triggerType;
    private String status;
}
