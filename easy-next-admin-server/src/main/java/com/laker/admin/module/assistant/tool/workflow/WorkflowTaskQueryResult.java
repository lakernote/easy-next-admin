package com.laker.admin.module.assistant.tool.workflow;

import java.util.List;

public record WorkflowTaskQueryResult(
        int total,
        List<WorkflowTaskToolItem> items
) {
    public WorkflowTaskQueryResult {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
