package com.laker.admin.module.batch.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BatchTaskSubmitRequest {
    @NotBlank(message = "任务类型不能为空")
    @Size(max = 64, message = "任务类型不能超过 64 个字符")
    private String taskType;
    @NotBlank(message = "任务名称不能为空")
    @Size(max = 120, message = "任务名称不能超过 120 个字符")
    private String taskName;
    @Size(max = 128, message = "业务幂等键不能超过 128 个字符")
    private String businessKey;
    @Size(max = 32, message = "触发类型不能超过 32 个字符")
    private String triggerType;
    @Size(max = 128, message = "触发来源不能超过 128 个字符")
    private String triggerRefId;
    @Min(value = 0, message = "总数不能小于 0")
    @Max(value = 10000000, message = "总数不能超过 10000000")
    private Integer totalCount;
    @Size(max = 128, message = "traceId 不能超过 128 个字符")
    private String traceId;
    @Size(max = 500, message = "说明不能超过 500 个字符")
    private String remark;
    @Valid
    private List<BatchTaskItemSubmitRequest> items;
}
