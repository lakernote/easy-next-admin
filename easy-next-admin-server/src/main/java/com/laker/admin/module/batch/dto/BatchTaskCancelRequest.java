package com.laker.admin.module.batch.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BatchTaskCancelRequest {
    @Size(max = 1000, message = "取消原因不能超过 1000 个字符")
    private String reason;
}
