package com.laker.admin.module.assistant.tool.execution;

/**
 * Plan Step 在 AssistantToolRunner 中的执行状态。
 */
public enum ToolExecutionStepStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    WAITING,
    SKIPPED,
    /** 已进入副作用边界，但最终结果暂时无法确认。 */
    INDETERMINATE,
    FAILED
}
