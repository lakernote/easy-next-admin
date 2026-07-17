package com.laker.admin.module.assistant.tool.workflow;

import java.util.List;

public interface WorkflowTaskToolQueryService {
    List<WorkflowTaskToolItem> myPendingTasks(Long userId, int limit);
}
