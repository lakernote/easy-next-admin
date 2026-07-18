package com.laker.admin.module.assistant.tool.workflow;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 请假动作草稿。
 *
 * <p>这是给用户确认的脱敏数据，不代表已经提交请假单。</p>
 */
public record LeaveRequestActionDraft(
        String actionType,
        String toolName,
        String leaveType,
        String leaveTypeLabel,
        LocalDateTime startTime,
        LocalDateTime endTime,
        BigDecimal days,
        String reason,
        String confirmHint
) {
}
