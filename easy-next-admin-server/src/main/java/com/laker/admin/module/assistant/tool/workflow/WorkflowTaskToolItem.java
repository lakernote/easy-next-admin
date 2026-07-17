package com.laker.admin.module.assistant.tool.workflow;

import java.time.LocalDateTime;

public record WorkflowTaskToolItem(
        Long taskId,
        Long instanceId,
        String instanceTitle,
        String nodeName,
        String status,
        LocalDateTime startedAt
) {
}
