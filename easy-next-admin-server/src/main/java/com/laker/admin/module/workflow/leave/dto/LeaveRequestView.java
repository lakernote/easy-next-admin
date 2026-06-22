package com.laker.admin.module.workflow.leave.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class LeaveRequestView {
    private Long id;
    private String requestNo;
    private Long applicantId;
    private String applicantName;
    private String leaveType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal days;
    private String reason;
    private String status;
    private String workflowStatus;
    private Long workflowInstanceId;
    private LocalDateTime createdAt;
}
