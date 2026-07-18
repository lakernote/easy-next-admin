package com.laker.admin.module.assistant.runtime;

import com.laker.admin.module.assistant.tool.execution.ToolExecutionPlan;
import com.laker.admin.module.assistant.runtime.model.ToolCallingMessage;
import com.laker.admin.module.assistant.state.AssistantPendingActionUpdate;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.AssistantToolRunStatus;
import com.laker.admin.module.assistant.tool.AssistantVisibleTool;
import com.laker.admin.module.assistant.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 一次用户回合中，仅供 {@link AgentLoop} 使用的可变运行状态。
 *
 * <p>它不是持久化的 {@code AssistantConversationState}。把计数器、消息、observation 和当前工具面
 * 收敛到一个对象后，AgentLoop 主干只需要表达“迭代、继续、完成”，不会被十几个局部变量淹没。</p>
 */
final class AgentLoopState {
    private final List<AssistantVisibleTool> visibleTools;
    private final List<ToolDefinition> businessToolDefinitions;
    private final List<ToolCallingMessage> messageHistory;
    private final List<AssistantToolResult> toolResults = new ArrayList<>();
    private final List<String> executedToolNames = new ArrayList<>();
    private final Set<String> attemptedToolCallSignatures = new HashSet<>();

    private List<ToolDefinition> activeToolDefinitions;
    private AssistantPendingActionUpdate pendingActionUpdate = AssistantPendingActionUpdate.unchanged();
    private ToolExecutionPlan currentPlan;
    private ToolDefinition lastToolDefinition;
    private AgentTerminationReason terminationReasonHint;
    private int modelCallCount;
    private int toolCallCount;
    private boolean modelCallRetryAttempted;

    private AgentLoopState(List<AssistantVisibleTool> visibleTools,
                           List<ToolDefinition> businessToolDefinitions,
                           List<ToolDefinition> activeToolDefinitions,
                           List<ToolCallingMessage> initialMessageHistory) {
        this.visibleTools = List.copyOf(Objects.requireNonNull(visibleTools, "visibleTools must not be null"));
        this.businessToolDefinitions = List.copyOf(
                Objects.requireNonNull(
                        businessToolDefinitions,
                        "businessToolDefinitions must not be null"
                )
        );
        this.activeToolDefinitions = List.copyOf(
                Objects.requireNonNull(
                        activeToolDefinitions,
                        "activeToolDefinitions must not be null"
                )
        );
        this.messageHistory = new ArrayList<>(
                Objects.requireNonNull(initialMessageHistory, "initialMessageHistory must not be null")
        );
    }

    static AgentLoopState start(List<AssistantVisibleTool> visibleTools,
                                List<ToolDefinition> businessToolDefinitions,
                                List<ToolDefinition> activeToolDefinitions,
                                List<ToolCallingMessage> initialMessageHistory) {
        return new AgentLoopState(
                visibleTools,
                businessToolDefinitions,
                activeToolDefinitions,
                initialMessageHistory
        );
    }

    int recordModelCall() {
        return ++modelCallCount;
    }

    boolean hasToolCallBudget(int maxToolCalls) {
        return toolCallCount < maxToolCalls;
    }

    void incrementToolCallCount() {
        toolCallCount++;
    }

    boolean markToolCallAttempted(String signature) {
        return attemptedToolCallSignatures.add(
                Objects.requireNonNull(signature, "signature must not be null")
        );
    }

    boolean hasSuccessfulToolResult() {
        return toolResults.stream().anyMatch(result -> result.status() == AssistantToolRunStatus.EXECUTED
                || result.status() == AssistantToolRunStatus.PARTIAL_SUCCESS);
    }

    void addMessage(ToolCallingMessage message) {
        messageHistory.add(Objects.requireNonNull(message, "message must not be null"));
    }

    void addToolResult(AssistantToolResult result) {
        toolResults.add(Objects.requireNonNull(result, "result must not be null"));
    }

    void removeToolResults(String toolName, String artifactType) {
        toolResults.removeIf(result -> toolName.equals(result.toolName())
                && artifactType.equals(result.artifactType()));
    }

    void applyToolCallResult(String toolName,
                             ToolExecutionPlan plan,
                             AssistantToolResult result,
                             ToolDefinition definition,
                             AssistantPendingActionUpdate update) {
        currentPlan = Objects.requireNonNull(plan, "plan must not be null");
        lastToolDefinition = definition;
        pendingActionUpdate = pendingActionUpdate.then(
                Objects.requireNonNull(update, "update must not be null")
        );
        executedToolNames.add(toolName);
        toolResults.add(Objects.requireNonNull(result, "result must not be null"));
    }

    List<AssistantVisibleTool> visibleTools() {
        return visibleTools;
    }

    List<ToolDefinition> businessToolDefinitions() {
        return businessToolDefinitions;
    }

    List<ToolDefinition> activeToolDefinitions() {
        return activeToolDefinitions;
    }

    void replaceActiveToolDefinitions(List<ToolDefinition> definitions) {
        activeToolDefinitions = List.copyOf(
                Objects.requireNonNull(definitions, "definitions must not be null")
        );
    }

    List<ToolCallingMessage> messageHistory() {
        return List.copyOf(messageHistory);
    }

    List<AssistantToolResult> toolResults() {
        return List.copyOf(toolResults);
    }

    List<String> executedToolNames() {
        return List.copyOf(executedToolNames);
    }

    AssistantPendingActionUpdate pendingActionUpdate() {
        return pendingActionUpdate;
    }

    ToolExecutionPlan currentPlan() {
        return currentPlan;
    }

    void updateCurrentPlan(ToolExecutionPlan plan) {
        this.currentPlan = plan;
    }

    void markTerminationReason(AgentTerminationReason reason) {
        this.terminationReasonHint = Objects.requireNonNull(reason, "reason must not be null");
    }

    AgentTerminationReason finalResponseTerminationReason() {
        return terminationReasonHint == null
                ? AgentTerminationReason.FINAL_RESPONSE
                : terminationReasonHint;
    }

    ToolDefinition lastToolDefinition() {
        return lastToolDefinition;
    }

    int modelCallCount() {
        return modelCallCount;
    }

    int toolCallCount() {
        return toolCallCount;
    }

    /**
     * 为一次模型调用故障预留一次受统一预算约束的重试。
     *
     * <p>这既覆盖首次直答的瞬时网关故障，也覆盖 Tool observation 产生后的最终合成故障。
     * 整个 Agent Run 最多重试一次，避免网关持续故障时形成隐式重试风暴。已执行 Tool 不会重放：
     * observation 已进入消息历史，同一 Tool Call 签名也受 {@code attemptedToolCallSignatures} 去重。</p>
     */
    boolean tryMarkModelCallRetry() {
        if (modelCallRetryAttempted) {
            return false;
        }
        modelCallRetryAttempted = true;
        return true;
    }

}
