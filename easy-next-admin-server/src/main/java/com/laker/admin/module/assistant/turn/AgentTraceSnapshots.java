package com.laker.admin.module.assistant.turn;

import com.laker.admin.module.assistant.context.AssistantRequestContext;
import com.laker.admin.module.assistant.state.AssistantConversationState;
import com.laker.admin.module.assistant.state.AssistantPendingAction;
import com.laker.admin.module.assistant.tool.AssistantToolArgument;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.AssistantToolVisibilityPlan;
import com.laker.admin.module.assistant.tool.AssistantVisibleTool;
import com.laker.admin.module.assistant.tool.ToolInputField;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionPlan;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把运行对象转换成可脱敏、可分层记录的 Trace 快照。
 *
 * <p>该类只服务调试链路，不参与 Agent 决策。把快照代码从 {@link AgentTurnService}
 * 移出后，单轮入口可以专注于上下文、会话锁、AgentLoop 和状态写回。</p>
 */
final class AgentTraceSnapshots {
    private AgentTraceSnapshots() {
    }

    static Map<String, Object> contextMetadata(AssistantRequestContext context) {
        return map(
                "runId", context.runId(),
                "traceId", context.traceId(),
                "conversationId", context.conversationId(),
                "user", context.user() == null ? Map.of() : map(
                        "userId", context.user().userId(),
                        "deptId", context.user().deptId(),
                        "permissionCount", context.user().permissions() == null
                                ? 0
                                : context.user().permissions().size(),
                        "superAdmin", context.user().superAdmin()
                ),
                "zoneId", context.zoneId(),
                "requestInstant", context.requestInstant(),
                "today", context.today(),
                "now", context.now()
        );
    }

    static Map<String, Object> contextContent(AssistantRequestContext context) {
        return map(
                "normalizedMessage", context.normalizedMessage(),
                "user", context.user() == null ? Map.of() : map(
                        "userName", context.user().userName(),
                        "nickName", context.user().nickName(),
                        "deptName", context.user().deptName(),
                        "roles", context.user().roles(),
                        "roleNames", context.user().roleNames()
                )
        );
    }

    static Map<String, Object> conversationMetadata(AssistantConversationState state) {
        AssistantPendingAction pendingAction = state == null ? null : state.pendingAction();
        return map(
                "version", state == null ? 0 : state.version(),
                "recentTurnCount", state == null ? 0 : state.chatMemory().recentTurns().size(),
                "hasPendingAction", pendingAction != null,
                "pendingToolName", pendingAction == null ? "" : pendingAction.toolName(),
                "pendingStatus", pendingAction == null ? "" : pendingAction.status(),
                "hasLastResult", state != null && state.lastResult() != null,
                "lastToolName", state == null || state.lastResult() == null
                        ? ""
                        : state.lastResult().toolName()
        );
    }

    static Map<String, Object> conversation(AssistantConversationState state) {
        AssistantPendingAction pendingAction = state == null ? null : state.pendingAction();
        return map(
                "version", state == null ? 0 : state.version(),
                "recentTurns", state == null ? List.of() : state.chatMemory().recentTurns(),
                "pendingAction", pendingAction(pendingAction),
                "lastResult", state == null || state.lastResult() == null ? Map.of() : state.lastResult()
        );
    }

    static Map<String, Object> toolVisibilityMetadata(AssistantToolVisibilityPlan plan) {
        return map(
                "registeredToolNames", plan.registeredToolNames(),
                "visibleToolNames", plan.tools().stream().map(AssistantVisibleTool::name).toList(),
                "toolCount", plan.tools().size(),
                "reason", plan.reason(),
                "missingEvidence", plan.missingEvidence()
        );
    }

    static Map<String, Object> plan(ToolExecutionPlan plan) {
        if (plan == null) {
            return Map.of();
        }
        return map(
                "planStatus", plan.status(),
                "toolName", plan.toolName(),
                "toolCallId", plan.toolCallId(),
                "readOnly", plan.readOnly(),
                "actionId", plan.actionId(),
                "steps", plan.steps(),
                "filledArguments", plan.filledArguments().stream().map(AgentTraceSnapshots::argument).toList(),
                "missingParameters", plan.missingParameters().stream().map(AgentTraceSnapshots::parameter).toList(),
                "missingEvidence", plan.missingEvidence(),
                "reason", plan.reason()
        );
    }

    static Map<String, Object> planMetadata(ToolExecutionPlan plan) {
        if (plan == null) {
            return Map.of();
        }
        return map(
                "planStatus", plan.status(),
                "toolName", plan.toolName(),
                "toolCallId", plan.toolCallId(),
                "readOnly", plan.readOnly(),
                "actionId", plan.actionId(),
                "filledArgumentKeys", plan.filledArguments().stream().map(AssistantToolArgument::key).toList(),
                "missingParameterNames", plan.missingParameters().stream().map(ToolInputField::name).toList(),
                "missingEvidence", plan.missingEvidence(),
                "reason", plan.reason()
        );
    }

    static Map<String, Object> toolResult(AssistantToolResult result) {
        if (result == null) {
            return map("status", "SKIPPED");
        }
        return map(
                "status", result.status(),
                "toolName", result.toolName(),
                "artifactType", result.artifactType(),
                "summary", result.summary(),
                "data", result.data()
        );
    }

    static Map<String, Object> toolResultMetadata(AssistantToolResult result) {
        if (result == null) {
            return map("status", "SKIPPED");
        }
        return map(
                "status", result.status(),
                "toolName", result.toolName(),
                "artifactType", result.artifactType(),
                "hasData", result.data() != null
        );
    }

    static Map<String, Object> map(Object... entries) {
        Map<String, Object> value = new LinkedHashMap<>();
        for (int index = 0; index + 1 < entries.length; index += 2) {
            value.put(String.valueOf(entries[index]), entries[index + 1] == null ? "" : entries[index + 1]);
        }
        return value;
    }

    private static Map<String, Object> pendingAction(AssistantPendingAction pendingAction) {
        if (pendingAction == null) {
            return Map.of();
        }
        return map(
                "toolName", pendingAction.toolName(),
                "status", pendingAction.status(),
                "actionId", pendingAction.actionId(),
                "filledArguments", pendingAction.filledArguments().stream()
                        .map(AgentTraceSnapshots::argument)
                        .toList(),
                "missingParameters", pendingAction.missingParameters().stream()
                        .map(AgentTraceSnapshots::parameter)
                        .toList(),
                "updatedAt", pendingAction.updatedAt()
        );
    }

    private static Map<String, Object> argument(AssistantToolArgument argument) {
        return map(
                "key", argument.key(),
                "label", argument.label(),
                "value", argument.value(),
                "displayValue", argument.displayValue()
        );
    }

    private static Map<String, Object> parameter(ToolInputField parameter) {
        return map(
                "key", parameter.name(),
                "label", parameter.displayName(),
                "type", parameter.type().value(),
                "format", parameter.format().value(),
                "description", parameter.description(),
                "required", parameter.required(),
                "options", parameter.enumValues()
        );
    }
}
