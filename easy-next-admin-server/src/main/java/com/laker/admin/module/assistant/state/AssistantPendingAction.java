package com.laker.admin.module.assistant.state;

import com.laker.admin.module.assistant.context.AssistantRequestContext;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionPlan;
import com.laker.admin.module.assistant.tool.AssistantToolArgument;
import com.laker.admin.module.assistant.tool.ToolInputField;
import com.laker.admin.infrastructure.security.support.EasySecurityToken;

import java.time.Instant;
import java.util.List;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * 会话中等待用户继续补全的动作草稿。
 *
 * <p>这里只保存企业边界已经认可的脱敏状态：选中的工具、已填字段和缺失字段。
 * 它不保存原始 Prompt、权限明细或真实业务写入结果，避免会话状态变成不可控的内部上下文。</p>
 *
 * <pre>{@code
 * {
 *   "sourceRunId": "run_01",
 *   "sourceTraceId": "trace_01",
 *   "toolName": "workflow.leave.prepare",
 *   "originatingUserMessage": "帮我请假，确认后再查我的待办",
 *   "readOnly": false,
 *   "status": "COLLECTING",
 *   "actionId": "act_123",
 *   "idempotencyKey": "assistant:action:act_123",
 *   "filledArguments": [
 *     {"key": "leaveType", "label": "请假类型", "value": "病假", "displayValue": "病假"}
 *   ],
 *   "missingParameters": [
 *     {"name": "reason", "displayName": "请假原因", "required": true}
 *   ],
 *   "updatedAt": "2026-07-14T02:30:00Z"
 * }
 * }</pre>
 */
public record AssistantPendingAction(
        String sourceRunId,
        String sourceTraceId,
        String toolName,
        String originatingUserMessage,
        boolean readOnly,
        AssistantPendingActionStatus status,
        String actionId,
        String idempotencyKey,
        List<AssistantToolArgument> filledArguments,
        List<ToolInputField> missingParameters,
        Instant updatedAt
) {
    public AssistantPendingAction {
        status = status == null ? AssistantPendingActionStatus.COLLECTING : status;
        originatingUserMessage = originatingUserMessage == null ? "" : originatingUserMessage.trim();
        filledArguments = filledArguments == null ? List.of() : List.copyOf(filledArguments);
        missingParameters = missingParameters == null ? List.of() : List.copyOf(missingParameters);
    }

    public static AssistantPendingAction from(AssistantRequestContext context, ToolExecutionPlan plan) {
        return new AssistantPendingAction(
                context.runId(),
                context.traceId(),
                plan.toolName(),
                context.normalizedMessage(),
                plan.readOnly(),
                statusFrom(plan),
                actionIdFor(context, plan),
                idempotencyKeyFor(context, plan),
                plan.filledArguments(),
                plan.missingParameters(),
                context.requestInstant()
        );
    }

    private static AssistantPendingActionStatus statusFrom(ToolExecutionPlan plan) {
        return switch (plan.status()) {
            case INPUT_REQUIRED -> AssistantPendingActionStatus.COLLECTING;
            case APPROVAL_REQUIRED -> AssistantPendingActionStatus.WAITING_FOR_CONFIRMATION;
            case APPROVED -> AssistantPendingActionStatus.CONFIRMED;
            case CANCELED -> AssistantPendingActionStatus.CANCELED;
            default -> AssistantPendingActionStatus.COLLECTING;
        };
    }

    public static String actionIdFor(AssistantRequestContext context, ToolExecutionPlan plan) {
        if (plan.actionId() != null && !plan.actionId().isBlank()) {
            return plan.actionId();
        }
        String raw = context.runId() + "|" + plan.toolName() + "|" + argsFingerprint(plan);
        return "act_" + EasySecurityToken.sha256(raw).substring(0, 24);
    }

    public static String idempotencyKeyFor(AssistantRequestContext context, ToolExecutionPlan plan) {
        if (plan.idempotencyKey() != null && !plan.idempotencyKey().isBlank()) {
            return plan.idempotencyKey();
        }
        return "assistant:action:" + actionIdFor(context, plan);
    }

    private static String argsFingerprint(ToolExecutionPlan plan) {
        return plan.filledArguments().stream()
                .sorted(Comparator.comparing(AssistantToolArgument::key))
                .map(argument -> argument.key() + "=" + argument.value())
                .collect(Collectors.joining("&"));
    }
}
