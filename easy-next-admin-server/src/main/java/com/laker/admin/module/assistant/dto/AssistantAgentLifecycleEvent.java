package com.laker.admin.module.assistant.dto;

import com.laker.admin.module.assistant.context.AssistantRequestContext;
import com.laker.admin.module.assistant.runtime.model.ToolCallingRequest;
import com.laker.admin.module.assistant.runtime.model.ToolCallingResponse;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionPlan;
import com.laker.admin.module.assistant.state.AssistantChatMemory;
import com.laker.admin.module.assistant.state.AssistantConversationState;
import com.laker.admin.module.assistant.state.AssistantLastResult;
import com.laker.admin.module.assistant.state.AssistantPendingAction;
import com.laker.admin.module.assistant.tool.AssistantToolArgumentResolution;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.AssistantToolSelection;
import com.laker.admin.module.assistant.tool.AssistantToolVisibilityPlan;
import com.laker.admin.module.assistant.trace.AssistantRunTrace;

import java.util.List;
import java.util.Map;

/**
 * 服务端内部 Agent 生命周期事件。
 *
 * <p>同步调试、服务端观测和用户流式适配器都可观察它；该类型不直接作为普通用户 API 合同。
 * API key、Authorization、权限列表、IP 等敏感上下文不进入该结构。</p>
 */
public record AssistantAgentLifecycleEvent(
        AssistantAgentLifecycleEventType event,
        AssistantAgentLifecyclePhase phase,
        String runId,
        String traceId,
        AssistantEntryResponse.AssistantTraceStepSnapshot step,
        Object payload
) {
    public static AssistantAgentLifecycleEvent contextBuilt(AssistantRequestContext context,
                                                    AssistantRunTrace.AssistantTraceStep step) {
        return event(AssistantAgentLifecycleEventType.CONTEXT_BUILT, context, step, Map.of(
                "conversationId", context.conversationId() == null ? "" : context.conversationId(),
                "normalizedMessage", context.normalizedMessage(),
                "time", new AssistantEntryResponse.AssistantTimeSnapshot(
                        context.zoneId().getId(),
                        context.requestInstant(),
                        context.today(),
                        context.now()
                )
        ));
    }

    public static AssistantAgentLifecycleEvent conversationTurnAcquired(AssistantRequestContext context,
                                                                 AssistantRunTrace.AssistantTraceStep step) {
        return event(AssistantAgentLifecycleEventType.CONVERSATION_TURN_ACQUIRED, context, step, Map.of(
                "conversationId", context.conversationId() == null ? "" : context.conversationId(),
                "acquired", true
        ));
    }

    public static AssistantAgentLifecycleEvent conversationStateLoaded(AssistantRequestContext context,
                                                               AssistantConversationState conversationState,
                                                               AssistantRunTrace.AssistantTraceStep step) {
        AssistantPendingAction pendingAction = conversationState == null ? null : conversationState.pendingAction();
        if (pendingAction == null) {
            return event(AssistantAgentLifecycleEventType.CONVERSATION_STATE_LOADED, context, step, Map.of(
                    "version", conversationState == null ? 0 : conversationState.version(),
                    "chatMemory", chatMemoryPayload(conversationState == null ? null : conversationState.chatMemory()),
                    "pendingState", Map.of(
                            "hasPendingAction", false,
                            "reason", "当前会话没有待补全动作"
                    ),
                    "lastResult", lastResultPayload(conversationState == null ? null : conversationState.lastResult())
            ));
        }
        return event(AssistantAgentLifecycleEventType.CONVERSATION_STATE_LOADED, context, step, Map.of(
                "version", conversationState.version(),
                "chatMemory", chatMemoryPayload(conversationState.chatMemory()),
                "pendingState", Map.ofEntries(
                        Map.entry("hasPendingAction", true),
                        Map.entry("sourceRunId", pendingAction.sourceRunId()),
                        Map.entry("sourceTraceId", pendingAction.sourceTraceId()),
                        Map.entry("toolName", text(pendingAction.toolName())),
                        Map.entry("readOnly", pendingAction.readOnly()),
                        Map.entry("status", pendingAction.status().name()),
                        Map.entry("actionId", text(pendingAction.actionId())),
                        Map.entry("filledArguments", pendingAction.filledArguments().stream()
                                .map(AssistantEntryResponse.AssistantToolArgumentSnapshot::from)
                                .toList()),
                        Map.entry("missingParameters", pendingAction.missingParameters().stream()
                                .map(AssistantEntryResponse.ToolInputFieldSnapshot::from)
                                .toList()),
                        Map.entry("updatedAt", pendingAction.updatedAt())
                ),
                "lastResult", lastResultPayload(conversationState.lastResult())
        ));
    }

    public static AssistantAgentLifecycleEvent decisionReady(AssistantRequestContext context,
                                                     ToolExecutionPlan plan,
                                                     AssistantRunTrace.AssistantTraceStep step) {
        return event(AssistantAgentLifecycleEventType.DECISION_READY, context, step, Map.of(
                "status", plan.status().name(),
                "toolName", text(plan.toolName()),
                "reason", text(plan.reason()),
                "missingEvidence", plan.missingEvidence()
        ));
    }

    public static AssistantAgentLifecycleEvent toolVisibilityReady(AssistantRequestContext context,
                                                           AssistantToolVisibilityPlan plan,
                                                           AssistantRunTrace.AssistantTraceStep step) {
        return event(
                AssistantAgentLifecycleEventType.TOOL_VISIBILITY_READY,
                context,
                step,
                AssistantEntryResponse.AssistantVisibleToolSnapshot.from(plan)
        );
    }

    public static AssistantAgentLifecycleEvent modelCallStarted(AssistantRequestContext context,
                                                                int step,
                                                                ToolCallingRequest request) {
        return event(AssistantAgentLifecycleEventType.MODEL_CALL_STARTED, context, null, Map.of(
                "step", step,
                "operationName", request.operationName(),
                "messageCount", request.messages().size(),
                "toolCount", request.tools().size()
        ));
    }

    public static AssistantAgentLifecycleEvent modelCallFinished(AssistantRequestContext context,
                                                                 int step,
                                                                 ToolCallingResponse response) {
        return event(AssistantAgentLifecycleEventType.MODEL_CALL_FINISHED, context, null, Map.of(
                "step", step,
                "hasToolCalls", response.hasToolCalls(),
                "toolCallCount", response.toolCalls().size(),
                "finishReason", response.finishReason().name(),
                "contentLength", response.content() == null ? 0 : response.content().length()
        ));
    }

    public static AssistantAgentLifecycleEvent toolSelectionReady(AssistantRequestContext context,
                                                          AssistantToolSelection selection,
                                                          AssistantRunTrace.AssistantTraceStep step) {
        return event(AssistantAgentLifecycleEventType.TOOL_SELECTION_READY, context, step, Map.of(
                "toolName", selection.toolName() == null ? "" : selection.toolName(),
                "availableToolNames", selection.availableTools().stream()
                        .map(tool -> tool.name())
                        .toList(),
                "reason", text(selection.reason()),
                "missingEvidence", selection.missingEvidence()
        ));
    }

    public static AssistantAgentLifecycleEvent toolArgumentsReady(AssistantRequestContext context,
                                                          AssistantToolArgumentResolution resolution,
                                                          AssistantRunTrace.AssistantTraceStep step) {
        return event(AssistantAgentLifecycleEventType.TOOL_ARGUMENTS_READY, context, step, Map.of(
                "toolName", resolution.toolName() == null ? "" : resolution.toolName(),
                "complete", resolution.complete(),
                "filledArguments", resolution.arguments().stream()
                        .map(AssistantEntryResponse.AssistantToolArgumentSnapshot::from)
                        .toList(),
                "missingParameters", resolution.missingRequiredParameters().stream()
                        .map(AssistantEntryResponse.ToolInputFieldSnapshot::from)
                        .toList()
        ));
    }

    public static AssistantAgentLifecycleEvent toolArgumentsValidated(AssistantRequestContext context,
                                                              AssistantToolArgumentResolution resolution,
                                                              AssistantRunTrace.AssistantTraceStep step) {
        return event(AssistantAgentLifecycleEventType.TOOL_ARGUMENTS_VALIDATED, context, step, Map.of(
                "toolName", resolution.toolName() == null ? "" : resolution.toolName(),
                "complete", resolution.complete(),
                "filledArguments", resolution.arguments().stream()
                        .map(AssistantEntryResponse.AssistantToolArgumentSnapshot::from)
                        .toList(),
                "missingOrInvalidParameters", resolution.missingRequiredParameters().stream()
                        .map(AssistantEntryResponse.ToolInputFieldSnapshot::from)
                        .toList()
        ));
    }

    public static AssistantAgentLifecycleEvent planReady(AssistantRequestContext context,
                                                 ToolExecutionPlan plan,
                                                 AssistantRunTrace.AssistantTraceStep step) {
        return event(AssistantAgentLifecycleEventType.PLAN_READY, context, step,
                AssistantEntryResponse.ToolExecutionPlanSnapshot.from(plan));
    }

    public static AssistantAgentLifecycleEvent toolFinished(AssistantRequestContext context,
                                                    AssistantToolResult result,
                                                    AssistantRunTrace.AssistantTraceStep step) {
        return event(AssistantAgentLifecycleEventType.TOOL_FINISHED, context, step,
                AssistantEntryResponse.AssistantToolExecutionSnapshot.from(result));
    }

    public static AssistantAgentLifecycleEvent responseReady(AssistantRequestContext context,
                                                     String reply,
                                                     AssistantRunTrace.AssistantTraceStep step) {
        return event(AssistantAgentLifecycleEventType.RESPONSE_READY, context, step,
                Map.of("reply", reply == null ? "" : reply));
    }

    public static AssistantAgentLifecycleEvent conversationStateWritten(AssistantRequestContext context,
                                                                 AssistantRunTrace.AssistantTraceStep step) {
        return event(AssistantAgentLifecycleEventType.CONVERSATION_STATE_WRITTEN, context, step, Map.of(
                "conversationId", context.conversationId() == null ? "" : context.conversationId(),
                "written", true
        ));
    }

    public static AssistantAgentLifecycleEvent done(AssistantEntryResponse response) {
        return new AssistantAgentLifecycleEvent(
                AssistantAgentLifecycleEventType.DONE,
                AssistantAgentLifecyclePhase.DONE,
                response.runId(),
                response.traceId(),
                null,
                response
        );
    }

    public static AssistantAgentLifecycleEvent failed(String runId, String traceId, String message) {
        return new AssistantAgentLifecycleEvent(
                AssistantAgentLifecycleEventType.ERROR,
                AssistantAgentLifecyclePhase.FAILED,
                runId,
                traceId,
                null,
                Map.of("message", message == null ? "助手执行失败" : message)
        );
    }

    public static List<AssistantAgentLifecycleEventType> eventTypes() {
        return List.of(AssistantAgentLifecycleEventType.values());
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static Map<String, Object> chatMemoryPayload(AssistantChatMemory chatMemory) {
        AssistantChatMemory memory = chatMemory == null ? AssistantChatMemory.empty() : chatMemory;
        return Map.of(
                "recentTurnCount", memory.recentTurns().size(),
                "recentTurns", memory.recentTurns()
        );
    }

    private static Map<String, Object> lastResultPayload(AssistantLastResult lastResult) {
        if (lastResult == null) {
            return Map.of(
                    "hasLastResult", false,
                    "reason", "当前会话没有可引用的最近业务结果"
            );
        }
        return Map.ofEntries(
                Map.entry("hasLastResult", true),
                Map.entry("toolName", text(lastResult.toolName())),
                Map.entry("artifactType", text(lastResult.artifactType())),
                Map.entry("summary", text(lastResult.summary())),
                Map.entry("references", lastResult.references()),
                Map.entry("updatedAt", lastResult.updatedAt())
        );
    }

    private static AssistantAgentLifecycleEvent event(AssistantAgentLifecycleEventType event,
                                              AssistantRequestContext context,
                                              AssistantRunTrace.AssistantTraceStep step,
                                              Object payload) {
        return new AssistantAgentLifecycleEvent(
                event,
                AssistantAgentLifecyclePhase.RUNNING,
                context.runId(),
                context.traceId(),
                step == null ? null : AssistantEntryResponse.AssistantTraceStepSnapshot.from(step),
                payload
        );
    }
}
