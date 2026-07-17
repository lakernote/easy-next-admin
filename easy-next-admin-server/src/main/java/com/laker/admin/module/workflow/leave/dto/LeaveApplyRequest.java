package com.laker.admin.module.workflow.leave.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class LeaveApplyRequest {
    @NotBlank(message = "请假类型不能为空")
    private String leaveType;
    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;
    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;
    @NotNull(message = "请假天数不能为空")
    @DecimalMin(value = "0.5", message = "请假天数至少为0.5天")
    private BigDecimal days;
    @NotBlank(message = "请假事由不能为空")
    @Size(max = 500, message = "请假事由不能超过500个字符")
    private String reason;
}
