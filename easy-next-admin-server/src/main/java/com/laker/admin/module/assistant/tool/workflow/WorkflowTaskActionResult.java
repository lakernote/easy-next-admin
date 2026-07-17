package com.laker.admin.module.assistant.tool.workflow;

/** 流程任务动作成功后返回给助手的最小结构化结果。 */
public record WorkflowTaskActionResult(
        WorkflowTaskDecision decision,
        Long taskId,
        Long instanceId,
        String businessId,
        String title,
        String currentNodeKey,
        String status
) {
}
