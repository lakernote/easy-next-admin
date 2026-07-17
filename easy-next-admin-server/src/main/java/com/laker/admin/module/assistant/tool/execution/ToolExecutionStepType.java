package com.laker.admin.module.assistant.tool.execution;

/** 企业助手主循环中标准的执行 / 观察 / 回复步骤类型。 */
public enum ToolExecutionStepType {
    ASK_USER,
    CALL_TOOL,
    CREATE_ACTION_DRAFT,
    WAIT_FOR_CONFIRMATION,
    CONFIRM_ACTION,
    OBSERVE_RESULT,
    FINAL_RESPONSE,
    STOP
}
