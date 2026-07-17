/**
 * 企业助手入口的标准 Tool Calling Mini Agent。
 *
 * <p>当前阶段把一次用户消息推进到“工具执行或用户可见回复”，写操作仍只生成待确认动作草稿，不直接写业务单据。
 * 普通请求入口顺序为：</p>
 *
 * <ol>
 *     <li>{@code AssistantContextBuilder}: 输入登录用户、原始消息、会话 ID，输出 {@code AssistantRequestContext}。</li>
 *     <li>{@code AssistantConversationStateStore}: 原子读取/保存版本化 Chat Memory、Pending Action 和 Last Result。</li>
 *     <li>{@code ToolVisibilityPolicy}: 按当前用户权限构建本轮可见的真实 Tool Schema。</li>
 *     <li>{@code ModelMessageBuilder}: 构建稳定 System Prompt、逐条历史、本轮服务端状态和当前 user 消息。</li>
 *     <li>{@code AgentLoop}: 只保留 {@code model -> tool batch -> observation -> final} 最小流程。</li>
 *     <li>{@code ToolCallProcessor}: 处理 Tool 批次协议、预算、去重和紧凑 observation。</li>
 *     <li>{@code BusinessToolCallHandler}: 把业务 Tool 参数转换为执行计划。</li>
 *     <li>{@code PendingActionHandler}: 只确认或取消服务端已保存的写操作草稿。</li>
 *     <li>{@code AssistantConversationStateWriter}: 根据显式 {@code AssistantPendingActionUpdate} 写入最终消息、Pending Action 和 Last Result。</li>
 * </ol>
 *
 * <p>普通请求不执行前置意图分类，模型直接在本轮可见 ToolDefinition 中选择工具。
 * 自然语言不做正则或二次模型审查；确定性安全边界集中在 Tool Schema、权限、确认、幂等和领域服务。
 * 写操作只生成待确认草稿；用户确认后的恢复链继续使用已保存的结构化参数、动作 ID 和幂等键。一个成功 Tool 批次后关闭 Tool 面，
 * 参数协议错误才允许在统一预算内修复。</p>
 */
package com.laker.admin.module.assistant;
