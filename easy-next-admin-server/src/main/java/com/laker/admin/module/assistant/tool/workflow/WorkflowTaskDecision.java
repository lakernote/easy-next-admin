package com.laker.admin.module.assistant.tool.workflow;

/** Agent 支持的标准流程任务决策；每种决策仍由独立 Tool 和权限保护。 */
public enum WorkflowTaskDecision {
    APPROVE,
    REJECT
}
