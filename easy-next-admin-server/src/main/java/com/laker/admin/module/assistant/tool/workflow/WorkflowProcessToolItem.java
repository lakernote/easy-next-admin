package com.laker.admin.module.assistant.tool.workflow;

import java.time.LocalDateTime;

public record WorkflowProcessToolItem(
        Long instanceId,
        String businessType,
        String businessId,
        String title,
        String currentNodeKey,
        String status,
        LocalDateTime startedAt,
        String source
) {
}
