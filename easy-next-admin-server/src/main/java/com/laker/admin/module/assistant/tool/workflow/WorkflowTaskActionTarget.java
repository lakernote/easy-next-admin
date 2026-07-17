package com.laker.admin.module.assistant.tool.workflow;

/** 用户确认流程动作前可以安全展示的流程任务目标。 */
public record WorkflowTaskActionTarget(
        Long taskId,
        Long instanceId,
        String businessId,
        String title,
        String nodeName
) {
}
