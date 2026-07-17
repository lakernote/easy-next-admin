package com.laker.admin.module.assistant.tool.workflow;

import java.util.List;

public record WorkflowProcessQueryResult(
        int total,
        List<WorkflowProcessToolItem> items
) {
    public WorkflowProcessQueryResult {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
