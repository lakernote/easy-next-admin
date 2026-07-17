package com.laker.admin.module.assistant.runtime;

import com.laker.admin.module.assistant.tool.execution.ToolExecutionPlan;
import com.laker.admin.module.assistant.state.AssistantPendingActionUpdate;
import com.laker.admin.module.assistant.tool.AssistantToolResult;

import java.util.List;

/**
 * 企业助手主循环处理完一轮用户消息后的最终结果；上层用它生成 HTTP 响应并原子写回会话状态。
 *
 * <p>下面是查询一个待办后的学习用精简 JSON。{@code plan} 实际还包含可观测步骤、证据和 Tool Call ID：</p>
 * <pre>{@code
 * {
 *   "plan": {
 *     "status": "READY",
 *     "toolName": "workflow.task.list_my_pending",
 *     "readOnly": true,
 *     "reason": "只读 Tool 参数完整，可以执行"
 *   },
 *   "toolResult": {
 *     "toolName": "workflow.task.list_my_pending",
 *     "status": "EXECUTED",
 *     "artifactType": "TASK_LIST",
 *     "summary": "找到 1 个待办任务",
 *     "data": {"total": 1, "items": []}
 *   },
 *   "reply": "您当前有 1 个待办任务。",
 *   "modelCallCount": 3,
 *   "toolCallCount": 1,
 *   "executedToolNames": ["workflow.task.list_my_pending"],
 *   "pendingActionUpdate": {"type": "UNCHANGED", "action": null}
 * }
 * }</pre>
 *
 * <ul>
 *   <li>{@code plan}：最后一个受控 Tool 执行计划，表达本轮停在直答、补参、确认、完成或阻断。</li>
 *   <li>{@code toolResult}：本轮 Tool observations 的脱敏聚合；多 Tool 时 artifactType 为 MULTI_TOOL_RESULT。</li>
 *   <li>{@code reply}：主模型的最终文本，或 Runtime 根据结构化 Tool 结果生成的确定性文本。</li>
 *   <li>{@code modelCallCount/toolCallCount/executedToolNames}：统一预算与诊断数据，不进入 Chat Memory。</li>
 *   <li>{@code pendingActionUpdate}：显式告诉状态写入器保留、新增/替换或清除待处理动作。</li>
 * </ul>
 */
public record AgentResult(
        ToolExecutionPlan plan,
        AssistantToolResult toolResult,
        String reply,
        int modelCallCount,
        int toolCallCount,
        List<String> executedToolNames,
        AssistantPendingActionUpdate pendingActionUpdate
) {
    public AgentResult {
        reply = reply == null ? "" : reply.trim();
        executedToolNames = executedToolNames == null ? List.of() : List.copyOf(executedToolNames);
        pendingActionUpdate = pendingActionUpdate == null
                ? AssistantPendingActionUpdate.unchanged()
                : pendingActionUpdate;
    }
}
