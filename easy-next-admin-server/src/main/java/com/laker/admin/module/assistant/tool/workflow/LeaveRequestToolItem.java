package com.laker.admin.module.assistant.tool.workflow;

import java.math.BigDecimal;

/** 请假单 Tool 的稳定输出合同，避免把业务 DTO 直接暴露为 Agent 协议。 */
public record LeaveRequestToolItem(
        String requestNo,
        String applicantName,
        String leaveType,
        String startTime,
        String endTime,
        BigDecimal days,
        String reason,
        String status,
        String workflowStatus,
        Long workflowInstanceId
) {
}
