package com.laker.admin.module.assistant.runtime;

import com.laker.admin.infrastructure.json.EasyJsonCodec;
import com.laker.admin.module.assistant.context.AssistantRequestContext;
import com.laker.admin.module.assistant.runtime.model.ToolCall;
import com.laker.admin.module.assistant.runtime.model.ToolCallingMessage;
import com.laker.admin.module.assistant.state.AssistantConversationState;
import com.laker.admin.module.assistant.state.AssistantPendingActionUpdateType;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.AssistantToolRunStatus;
import com.laker.admin.module.assistant.tool.AssistantToolSelection;
import com.laker.admin.module.assistant.tool.AssistantVisibleTool;
import com.laker.admin.module.assistant.tool.ToolDefinition;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionPlan;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Tool Calling 的确定性边界。模型只提出调用；这里负责 Schema、权限、确认、幂等和 observation。
 */
@Component
public class ToolCallProcessor {
    private static final String TOOL_ARGUMENT_ERROR_ARTIFACT = "TOOL_ARGUMENT_ERROR";

    private final BusinessToolCallHandler businessToolCallHandler;
    private final PendingActionHandler pendingActionHandler;
    private final EasyJsonCodec jsonCodec;
    private final ToolObservationFormatter observationFormatter;

    ToolCallProcessor(BusinessToolCallHandler businessToolCallHandler,
                      PendingActionHandler pendingActionHandler,
                      EasyJsonCodec jsonCodec,
                      ToolObservationFormatter observationFormatter) {
        this.businessToolCallHandler = businessToolCallHandler;
        this.pendingActionHandler = pendingActionHandler;
        this.jsonCodec = jsonCodec;
        this.observationFormatter = observationFormatter;
    }

    List<ToolCall> normalizeRequestedToolCalls(List<ToolCall> requestedToolCalls, int modelCallNumber) {
        return normalizeToolCallIds(requestedToolCalls, modelCallNumber);
    }

    Optional<AgentResult> executeRequestedToolCalls(AssistantRequestContext context,
                                                    AssistantConversationState conversationState,
                                                    AgentLoopState runState,
                                                    AgentLoopListener listener,
                                                    List<ToolCall> requestedToolCalls,
                                                    int maxToolCalls) {
        if (countWriteTools(requestedToolCalls, runState.activeToolDefinitions()) > 1) {
            return Optional.of(rejectUnsafeWriteBatch(
                    context,
                    runState,
                    listener,
                    requestedToolCalls,
                    maxToolCalls
            ));
        }

        for (ToolCall requestedToolCall : scheduleReadOnlyCallsBeforeWrite(
                requestedToolCalls,
                runState.activeToolDefinitions()
        )) {
            Optional<ToolCallOutcome> execution = executeRequestedToolCall(
                    context,
                    conversationState,
                    runState,
                    listener,
                    requestedToolCall,
                    maxToolCalls
            );
            if (execution.isEmpty()) {
                continue;
            }
            ToolCallOutcome executionOutcome = execution.get();
            recordExecutionAndAppendObservation(
                    context,
                    runState,
                    listener,
                    requestedToolCall,
                    executionOutcome
            );
            updateToolSurface(runState, executionOutcome);
            if (executionOutcome.waitsForUser()) {
                return Optional.of(AgentResultFactory.fallback(runState));
            }
        }
        closeToolSurfaceAfterCompletedRound(runState);
        return Optional.empty();
    }

    /**
     * 一个成功进入执行计划的 Tool 批次就是本轮唯一的 Tool round。
     *
     * <p>模型应在同一次响应里提交全部相互独立的只读调用；批次完成后只根据 observation
     * 生成最终答复，避免“查列表 -> 查详情 -> 再补查业务单”式无边界扩查。参数格式错误会形成
     * BLOCKED 计划并保留 Tool 面，让模型仍有一次修复机会。</p>
     */
    private static void closeToolSurfaceAfterCompletedRound(AgentLoopState runState) {
        ToolExecutionPlan plan = runState.currentPlan();
        if (plan != null && plan.status() != ToolExecutionStatus.BLOCKED) {
            runState.replaceActiveToolDefinitions(List.of());
        }
    }

    AgentResult rejectCallsWithoutToolSurface(AssistantRequestContext context,
                                               AgentLoopState runState,
                                               List<ToolCall> calls,
                                               AgentLoopListener listener) {
        if (runState.currentPlan() == null) {
            String requestedTool = calls.get(0).name();
            runState.updateCurrentPlan(AgentResultFactory.blockedPlan(
                    context,
                    requestedTool,
                    "模型请求了本轮不可见工具",
                    "tool.not_visible"
            ));
            AssistantToolResult result = AssistantToolResult.failed(requestedTool, "当前没有可用或有权限的 Tool");
            runState.addToolResult(result);
            AgentLoopEvents.toolFinished(context, listener, result);
        }
        return AgentResultFactory.fallback(runState);
    }

    private Optional<ToolCallOutcome> executeRequestedToolCall(AssistantRequestContext context,
                                                               AssistantConversationState conversationState,
                                                               AgentLoopState runState,
                                                               AgentLoopListener listener,
                                                               ToolCall requestedToolCall,
                                                               int maxToolCalls) {
        if (!runState.hasToolCallBudget(maxToolCalls)) {
            recordProtocolFailure(
                    context,
                    runState,
                    listener,
                    requestedToolCall,
                    AssistantToolResult.failed(
                            requestedToolCall.name(),
                            "本轮工具调用已达到安全预算，不能继续执行"
                    ),
                    false,
                    !runState.hasSuccessfulToolResult()
            );
            runState.replaceActiveToolDefinitions(List.of());
            return Optional.empty();
        }
        if (!containsDefinition(runState.activeToolDefinitions(), requestedToolCall.name())) {
            AssistantToolResult result = AssistantToolResult.failed(
                    requestedToolCall.name(),
                    "当前阶段不允许调用该 Tool；请基于已有结果回复"
            );
            recordRejectedToolCall(context, runState, listener, requestedToolCall, result);
            if (runState.currentPlan() == null) {
                runState.updateCurrentPlan(AgentResultFactory.blockedPlan(
                        context,
                        requestedToolCall.name(),
                        "模型请求了本轮不可见工具",
                        "tool.not_visible"
                ));
            }
            runState.replaceActiveToolDefinitions(List.of());
            return Optional.empty();
        }

        notifyModelSelectedVisibleTool(context, listener, runState.visibleTools(), requestedToolCall.name());
        if (requestedToolCall.hasArgumentError()) {
            AssistantToolResult result = new AssistantToolResult(
                    requestedToolCall.name(),
                    AssistantToolRunStatus.FAILED,
                    TOOL_ARGUMENT_ERROR_ARTIFACT,
                    "Tool 参数不是合法 JSON 对象，请按输入 Schema 重新生成参数",
                    Map.of("retryable", true)
            );
            recordRejectedToolCall(context, runState, listener, requestedToolCall, result);
            if (runState.currentPlan() == null) {
                runState.updateCurrentPlan(AgentResultFactory.blockedPlan(
                        context,
                        requestedToolCall.name(),
                        "模型返回的 Tool 参数无法解析",
                        "tool.arguments.invalid"
                ));
            }
            return Optional.empty();
        }
        if (!runState.markToolCallAttempted(signature(requestedToolCall))) {
            recordProtocolFailure(
                    context,
                    runState,
                    listener,
                    requestedToolCall,
                    AssistantToolResult.failed(
                            requestedToolCall.name(),
                            "相同工具和参数已调用过，请使用已有结果回复"
                    ),
                    true,
                    false
            );
            runState.replaceActiveToolDefinitions(List.of());
            return Optional.empty();
        }

        ToolCallOutcome execution = executeAllowedToolCall(
                context,
                conversationState,
                runState,
                requestedToolCall,
                listener
        );
        return Optional.of(execution.resolveConversationUpdate(context));
    }

    private ToolCallOutcome executeAllowedToolCall(AssistantRequestContext context,
                                                   AssistantConversationState conversationState,
                                                   AgentLoopState runState,
                                                   ToolCall requestedToolCall,
                                                   AgentLoopListener listener) {
        if (pendingActionHandler.supports(requestedToolCall.name())) {
            return pendingActionHandler.execute(
                    context,
                    conversationState,
                    runState.visibleTools(),
                    listener,
                    requestedToolCall
            );
        }

        return businessToolCallHandler.execute(
                context,
                conversationState,
                runState,
                requestedToolCall,
                listener
        );
    }

    private AgentResult rejectUnsafeWriteBatch(AssistantRequestContext context,
                                               AgentLoopState runState,
                                               AgentLoopListener listener,
                                               List<ToolCall> calls,
                                               int maxToolCalls) {
        String reason = "一次模型响应不能包含多个写操作";
        runState.updateCurrentPlan(AgentResultFactory.blockedPlan(
                context,
                firstWriteToolName(calls, runState.activeToolDefinitions()),
                reason,
                "tool.write_batch.rejected"
        ));
        for (ToolCall call : calls) {
            notifyModelSelectedVisibleTool(context, listener, runState.visibleTools(), call.name());
            AssistantToolResult rejected = AssistantToolResult.failed(
                    call.name(),
                    "同一响应包含多个写操作，已按企业安全策略整体拒绝；请一次只处理一个写操作"
            );
            recordProtocolFailure(
                    context,
                    runState,
                    listener,
                    call,
                    rejected,
                    runState.hasToolCallBudget(maxToolCalls),
                    !runState.hasSuccessfulToolResult()
            );
        }
        return AgentResultFactory.fromRunState(
                runState,
                "这个请求包含多个并行写操作。为避免误操作，本轮没有执行任何一项；请一次只处理一个写操作。"
        );
    }

    private void recordExecutionAndAppendObservation(AssistantRequestContext context,
                                                     AgentLoopState runState,
                                                     AgentLoopListener listener,
                                                     ToolCall requestedToolCall,
                                                     ToolCallOutcome step) {
        runState.removeToolResults(requestedToolCall.name(), TOOL_ARGUMENT_ERROR_ARTIFACT);
        runState.incrementToolCallCount();
        runState.applyToolCallResult(
                requestedToolCall.name(),
                step.plan(),
                step.result(),
                step.definition(),
                step.pendingActionUpdate()
        );
        AgentLoopEvents.toolFinished(context, listener, step.result());
        runState.addMessage(ToolCallingMessage.tool(
                requestedToolCall.id(),
                observationFormatter.format(step.result(), step.plan(), step.definition())
        ));
    }

    private void updateToolSurface(AgentLoopState runState, ToolCallOutcome step) {
        switch (step.plan().status()) {
            case READY -> {
                // 保持当前工具面。
            }
            case CANCELED -> runState.replaceActiveToolDefinitions(runState.businessToolDefinitions());
            case APPROVED -> runState.replaceActiveToolDefinitions(
                    step.confirmedActionCompleted()
                            ? readOnlyDefinitions(runState.businessToolDefinitions())
                            : List.of()
            );
            case DIRECT_RESPONSE, BLOCKED -> runState.replaceActiveToolDefinitions(List.of());
            case INPUT_REQUIRED, APPROVAL_REQUIRED -> {
                // executeRequestedToolCalls 立即返回，让上层保存待处理动作并等待用户。
            }
        }
        if (step.pendingActionUpdate().type() == AssistantPendingActionUpdateType.CLEAR
                && step.plan().status() == ToolExecutionStatus.READY) {
            runState.replaceActiveToolDefinitions(runState.businessToolDefinitions());
        }
    }

    private void recordRejectedToolCall(AssistantRequestContext context,
                                        AgentLoopState runState,
                                        AgentLoopListener listener,
                                        ToolCall call,
                                        AssistantToolResult result) {
        recordProtocolFailure(
                context,
                runState,
                listener,
                call,
                result,
                true,
                !runState.hasSuccessfulToolResult()
        );
    }

    private void recordProtocolFailure(AssistantRequestContext context,
                                       AgentLoopState runState,
                                       AgentLoopListener listener,
                                       ToolCall call,
                                       AssistantToolResult result,
                                       boolean countToolCall,
                                       boolean includeInFinalResult) {
        if (countToolCall) {
            runState.incrementToolCallCount();
        }
        if (includeInFinalResult) {
            runState.addToolResult(result);
        }
        AgentLoopEvents.toolFinished(context, listener, result);
        runState.addMessage(ToolCallingMessage.tool(
                call.id(),
                observationFormatter.format(result, null, null)
        ));
    }

    private static List<ToolCall> normalizeToolCallIds(List<ToolCall> calls, int modelCallNumber) {
        List<ToolCall> normalized = new ArrayList<>();
        for (int index = 0; index < calls.size(); index++) {
            ToolCall call = calls.get(index);
            String id = StringUtils.hasText(call.id()) ? call.id() : "call_" + modelCallNumber + "_" + (index + 1);
            normalized.add(new ToolCall(id, call.name(), call.arguments(), call.argumentError()));
        }
        return List.copyOf(normalized);
    }

    private String signature(ToolCall call) {
        return call.name() + ":" + jsonCodec.toJson(new TreeMap<>(call.arguments()));
    }

    private static List<ToolCall> scheduleReadOnlyCallsBeforeWrite(List<ToolCall> calls,
                                                                   List<ToolDefinition> definitions) {
        if (calls.size() < 2 || countWriteTools(calls, definitions) == 0) {
            return calls;
        }
        List<ToolCall> scheduled = new ArrayList<>(calls.size());
        calls.stream().filter(call -> !isWriteTool(call.name(), definitions)).forEach(scheduled::add);
        calls.stream().filter(call -> isWriteTool(call.name(), definitions)).forEach(scheduled::add);
        return List.copyOf(scheduled);
    }

    private static int countWriteTools(List<ToolCall> calls, List<ToolDefinition> definitions) {
        return Math.toIntExact(calls.stream()
                .map(ToolCall::name)
                .filter(name -> isWriteTool(name, definitions))
                .count());
    }

    private static boolean isWriteTool(String name, List<ToolDefinition> definitions) {
        return definitions.stream()
                .filter(definition -> definition.name().equals(name))
                .anyMatch(definition -> !definition.readOnly());
    }

    private static boolean containsDefinition(List<ToolDefinition> definitions, String name) {
        return definitions.stream().anyMatch(definition -> definition.name().equals(name));
    }

    private static String firstWriteToolName(List<ToolCall> calls, List<ToolDefinition> definitions) {
        return calls.stream()
                .map(ToolCall::name)
                .filter(name -> isWriteTool(name, definitions))
                .findFirst()
                .orElse(null);
    }

    private static List<ToolDefinition> readOnlyDefinitions(List<ToolDefinition> definitions) {
        return definitions.stream().filter(ToolDefinition::readOnly).toList();
    }

    private static void notifyModelSelectedVisibleTool(AssistantRequestContext context,
                                                       AgentLoopListener listener,
                                                       List<AssistantVisibleTool> visibleTools,
                                                       String toolName) {
        if (ToolSetBuilder.isControlTool(toolName)) {
            return;
        }
        visibleTools.stream()
                .filter(tool -> tool.name().equals(toolName))
                .findFirst()
                .ifPresent(tool -> AgentLoopEvents.toolSelected(
                        context,
                        listener,
                        AssistantToolSelection.selected(tool, visibleTools, "模型通过原生 Tool Call 选择真实企业工具")
                ));
    }
}
