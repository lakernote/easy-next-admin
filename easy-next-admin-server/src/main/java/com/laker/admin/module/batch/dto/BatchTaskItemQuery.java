package com.laker.admin.module.batch.dto;

import lombok.Data;

@Data
public class BatchTaskItemQuery {
    private long page = 1;
    private long limit = 10;
    private String status;
    private String keyword;
}
