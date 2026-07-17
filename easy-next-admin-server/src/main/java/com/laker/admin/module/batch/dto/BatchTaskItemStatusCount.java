package com.laker.admin.module.batch.dto;

import lombok.Data;

@Data
public class BatchTaskItemStatusCount {
    private String status;
    private Long itemCount;
}
