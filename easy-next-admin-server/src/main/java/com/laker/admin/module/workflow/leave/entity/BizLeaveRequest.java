package com.laker.admin.module.workflow.leave.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("biz_leave_request")
public class BizLeaveRequest {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private String requestNo;
    private Long applicantId;
    private Long applicantDeptId;
    private String leaveType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal days;
    private String reason;
    private String status;
    private Long workflowInstanceId;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
