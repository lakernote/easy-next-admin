package com.laker.admin.module.assistant.runtime;

/**
 * Agent Loop 停止迭代的直接原因。
 *
 * <p>它与业务计划状态、Tool 执行状态相互独立：例如 Tool 可以执行失败，但 Agent 仍以
 * {@link #FINAL_RESPONSE} 正常结束并向用户解释；等待补参和等待确认则是两个明确暂停点。</p>
 */
public enum AgentTerminationReason {
    /** 模型或 Runtime 已生成本轮最终回复。 */
    FINAL_RESPONSE,
    /** Tool 缺少必填输入，暂停并等待用户补充。 */
    WAITING_FOR_INPUT,
    /** 写操作草稿已保存，暂停并等待用户确认。 */
    WAITING_FOR_CONFIRMATION,
    /** 权限、风险或 Tool 调用协议阻止本轮继续执行。 */
    BLOCKED,
    /** Tool 调用达到本轮安全预算。 */
    TOOL_CALL_LIMIT,
    /** 模型重复请求相同 Tool 和参数，Runtime 停止重复执行。 */
    DUPLICATE_TOOL_CALL,
    /** 模型调用达到本轮安全预算。 */
    MODEL_CALL_LIMIT,
    /** 模型网关或模型调用发生异常。 */
    MODEL_ERROR,
    /** 模型输出因长度上限被截断。 */
    MODEL_MAX_TOKENS,
    /** 模型输出被供应商安全策略过滤。 */
    MODEL_CONTENT_FILTERED,
    /** 模型明确拒绝当前请求。 */
    MODEL_REFUSED,
    /** 模型要求暂停，但当前 Runtime 不自动续接。 */
    MODEL_PAUSED,
    /** 模型响应结构与结束状态不一致。 */
    MODEL_PROTOCOL_ERROR
}
