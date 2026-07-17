package com.laker.admin.module.assistant.tool.execution;

/** 一次 Tool Call 经过参数、权限、风险和审批策略后的标准执行状态。 */
public enum ToolExecutionStatus {
    /** 当前用户目标不需要 Tool，模型可以直接回答。 */
    DIRECT_RESPONSE,
    /** 缺少 Tool Schema 必填输入，需要暂停并询问用户。 */
    INPUT_REQUIRED,
    /** Tool Call 已通过执行前校验，可以执行。 */
    READY,
    /** 写操作草稿已准备好，等待用户批准。 */
    APPROVAL_REQUIRED,
    /** 用户已批准服务端保存的动作快照，可以执行。 */
    APPROVED,
    /** 用户取消了当前待处理动作。 */
    CANCELED,
    /** Tool Call 被协议、权限、风险或运行策略阻止。 */
    BLOCKED
}
