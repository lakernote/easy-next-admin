package com.laker.admin.module.assistant.tool.workflow;

import com.laker.admin.module.assistant.context.AssistantRequestContext.AssistantUserContext;

/** 助手任务动作 Tool 与工作流运行时之间的业务边界。 */
public interface WorkflowTaskActionToolService {
    WorkflowTaskActionTarget resolveTarget(AssistantUserContext user, String taskIdentifier);

    WorkflowTaskActionResult approve(Long taskId, String comment);

    WorkflowTaskActionResult reject(Long taskId, String comment);
}
