package com.laker.admin.module.assistant.tool;

import java.util.List;

/**
 * ToolVisibilityPolicy 的输出快照。
 *
 * <p>{@code registeredToolNames} 是注册表快照；{@code tools} 是经过权限过滤后真正可发给模型的工具。</p>
 *
 * <pre>{@code
 * {
 *   "registeredToolNames": ["workflow.task.list_my_pending", "workflow.task.approve"],
 *   "tools": [
 *     {
 *       "name": "workflow.task.list_my_pending",
 *       "displayName": "查询我的待办",
 *       "description": "查询当前登录用户真实待办",
 *       "readOnly": true,
 *       "inputSchema": []
 *     }
 *   ],
 *   "missingEvidence": [],
 *   "reason": "已按当前用户权限提供可执行企业 Tool"
 * }
 * }</pre>
 *
 * <p>{@code registeredToolNames} 仅用于诊断“注册了但不可见”；模型只会收到 {@code tools}。
 * {@code missingEvidence} 记录无法构建 Tool 面的确定性原因，不是让模型补猜权限。</p>
 */
public record AssistantToolVisibilityPlan(
        List<String> registeredToolNames,
        List<AssistantVisibleTool> tools,
        List<String> missingEvidence,
        String reason
) {
    public AssistantToolVisibilityPlan {
        registeredToolNames = registeredToolNames == null ? List.of() : List.copyOf(registeredToolNames);
        tools = tools == null ? List.of() : List.copyOf(tools);
        missingEvidence = missingEvidence == null ? List.of() : List.copyOf(missingEvidence);
        reason = reason == null ? "" : reason.trim();
    }
}
