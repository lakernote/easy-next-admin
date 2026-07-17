package com.laker.admin.module.assistant.tool.workflow;

/** 同意或驳回流程任务的待确认动作草稿。 */
public record WorkflowTaskDecisionDraft(
        WorkflowTaskDecision decision,
        String toolName,
        Long taskId,
        Long instanceId,
        String businessId,
        String title,
        String nodeName,
        String comment,
        String summary
) {
}
