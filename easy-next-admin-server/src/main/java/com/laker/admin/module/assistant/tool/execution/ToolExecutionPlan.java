package com.laker.admin.module.assistant.tool.execution;

import com.laker.admin.module.assistant.tool.AssistantToolArgument;
import com.laker.admin.module.assistant.tool.ToolInputField;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 一次 Tool Call 的受控执行计划。
 *
 * <p>模型负责选择 Tool 和生成 Schema 参数；该计划只记录企业边界校验后的下一步、
 * 确认身份和可观测执行步骤，不再重复做意图分类、能力分类或生成伪任务树。</p>
 */
public record ToolExecutionPlan(
        String runId,
        String traceId,
        ToolExecutionStatus status,
        String toolName,
        boolean readOnly,
        List<AssistantToolArgument> filledArguments,
        List<ToolInputField> missingParameters,
        List<String> missingEvidence,
        String reason,
        String actionId,
        String idempotencyKey,
        List<ToolExecutionStep> steps,
        String toolCallId
) {
    public ToolExecutionPlan(String runId,
                         String traceId,
                         ToolExecutionStatus status,
                         String toolName,
                         boolean readOnly,
                         List<AssistantToolArgument> filledArguments,
                         List<ToolInputField> missingParameters,
                         List<String> missingEvidence,
                         String reason) {
        this(runId, traceId, status, toolName, readOnly, filledArguments,
                missingParameters, missingEvidence, reason, null, null, List.of(), "");
    }

    public ToolExecutionPlan(String runId,
                         String traceId,
                         ToolExecutionStatus status,
                         String toolName,
                         boolean readOnly,
                         List<AssistantToolArgument> filledArguments,
                         List<ToolInputField> missingParameters,
                         List<String> missingEvidence,
                         String reason,
                         String actionId,
                         String idempotencyKey) {
        this(runId, traceId, status, toolName, readOnly, filledArguments,
                missingParameters, missingEvidence, reason, actionId, idempotencyKey, List.of(), "");
    }

    public ToolExecutionPlan(String runId,
                         String traceId,
                         ToolExecutionStatus status,
                         String toolName,
                         boolean readOnly,
                         List<AssistantToolArgument> filledArguments,
                         List<ToolInputField> missingParameters,
                         List<String> missingEvidence,
                         String reason,
                         String actionId,
                         String idempotencyKey,
                         List<ToolExecutionStep> steps) {
        this(runId, traceId, status, toolName, readOnly, filledArguments,
                missingParameters, missingEvidence, reason, actionId, idempotencyKey, steps, "");
    }

    public ToolExecutionPlan {
        filledArguments = filledArguments == null ? List.of() : List.copyOf(filledArguments);
        missingParameters = missingParameters == null ? List.of() : List.copyOf(missingParameters);
        missingEvidence = missingEvidence == null ? List.of() : List.copyOf(missingEvidence);
        steps = steps == null || steps.isEmpty()
                ? defaultSteps(status, toolName, filledArguments, missingParameters)
                : List.copyOf(steps);
        toolCallId = toolCallId == null ? "" : toolCallId.trim();
    }

    public Map<String, String> argumentMap() {
        return filledArguments.stream()
                .collect(Collectors.toUnmodifiableMap(
                        AssistantToolArgument::key,
                        AssistantToolArgument::value,
                        (left, right) -> right
                ));
    }

    public ToolExecutionPlan withStatus(ToolExecutionStatus nextStatus, String nextReason) {
        return new ToolExecutionPlan(
                runId, traceId, nextStatus, toolName, readOnly,
                filledArguments, missingParameters, missingEvidence, nextReason,
                actionId, idempotencyKey, List.of(), toolCallId
        );
    }

    public ToolExecutionPlan withActionIdentity(String nextActionId, String nextIdempotencyKey) {
        return new ToolExecutionPlan(
                runId, traceId, status, toolName, readOnly,
                filledArguments, missingParameters, missingEvidence, reason,
                nextActionId, nextIdempotencyKey, steps, toolCallId
        );
    }

    public ToolExecutionPlan withFilledArguments(List<AssistantToolArgument> nextFilledArguments) {
        return new ToolExecutionPlan(
                runId, traceId, status, toolName, readOnly,
                nextFilledArguments, missingParameters, missingEvidence, reason,
                actionId, idempotencyKey, List.of(), toolCallId
        );
    }

    public ToolExecutionPlan withExecutedSteps(List<ToolExecutionStep> nextSteps) {
        return new ToolExecutionPlan(
                runId, traceId, status, toolName, readOnly,
                filledArguments, missingParameters, missingEvidence, reason,
                actionId, idempotencyKey, nextSteps, toolCallId
        );
    }

    public ToolExecutionPlan withToolCallId(String nextToolCallId) {
        return new ToolExecutionPlan(
                runId, traceId, status, toolName, readOnly,
                filledArguments, missingParameters, missingEvidence, reason,
                actionId, idempotencyKey, steps, nextToolCallId
        );
    }

    private static List<ToolExecutionStep> defaultSteps(ToolExecutionStatus status,
                                                        String toolName,
                                                        List<AssistantToolArgument> filledArguments,
                                                        List<ToolInputField> missingParameters) {
        if (status == null) {
            return List.of();
        }
        return switch (status) {
            case DIRECT_RESPONSE -> List.of(step(1, ToolExecutionStepType.FINAL_RESPONSE, null,
                    "生成最终回复", List.of(), List.of("reply")));
            case INPUT_REQUIRED -> List.of(step(1, ToolExecutionStepType.ASK_USER, toolName,
                    "追问 Tool Schema 缺失字段", missingParameterKeys(missingParameters), List.of("user_input")));
            case READY -> List.of(
                    step(1, ToolExecutionStepType.CALL_TOOL, toolName,
                            "执行只读 Tool", argumentKeys(filledArguments), List.of("tool_result")),
                    step(2, ToolExecutionStepType.OBSERVE_RESULT, null,
                            "把 Tool Result 回填给模型", List.of("tool_result"), List.of("observation")),
                    step(3, ToolExecutionStepType.FINAL_RESPONSE, null,
                            "根据真实结果继续推理或回复", List.of("observation"), List.of("reply"))
            );
            case APPROVAL_REQUIRED -> List.of(
                    step(1, ToolExecutionStepType.CREATE_ACTION_DRAFT, toolName,
                            "生成待确认动作草稿", argumentKeys(filledArguments), List.of("action_draft")),
                    step(2, ToolExecutionStepType.WAIT_FOR_CONFIRMATION, null,
                            "等待用户明确确认或取消", List.of("action_draft"), List.of("user_decision"))
            );
            case APPROVED -> List.of(
                    step(1, ToolExecutionStepType.CONFIRM_ACTION, toolName,
                            "按已保存快照执行写操作", argumentKeys(filledArguments), List.of("tool_result")),
                    step(2, ToolExecutionStepType.OBSERVE_RESULT, null,
                            "把真实执行结果回填给模型", List.of("tool_result"), List.of("observation")),
                    step(3, ToolExecutionStepType.FINAL_RESPONSE, null,
                            "告知用户真实执行结果", List.of("observation"), List.of("reply"))
            );
            case CANCELED -> List.of(
                    step(1, ToolExecutionStepType.STOP, toolName,
                            "取消当前待处理动作", List.of(), List.of("canceled")),
                    step(2, ToolExecutionStepType.FINAL_RESPONSE, null,
                            "告知用户已取消", List.of("canceled"), List.of("reply"))
            );
            case BLOCKED -> List.of(step(1, ToolExecutionStepType.STOP, toolName,
                    "本轮无法安全继续", List.of(), List.of("error")));
        };
    }

    private static ToolExecutionStep step(int sequence,
                                          ToolExecutionStepType type,
                                          String toolName,
                                          String description,
                                          List<String> inputKeys,
                                          List<String> outputKeys) {
        return new ToolExecutionStep(
                sequence, type, ToolExecutionStepStatus.PENDING, toolName,
                description, inputKeys, outputKeys, ""
        );
    }

    private static List<String> argumentKeys(List<AssistantToolArgument> arguments) {
        return arguments == null ? List.of() : arguments.stream().map(AssistantToolArgument::key).toList();
    }

    private static List<String> missingParameterKeys(List<ToolInputField> parameters) {
        return parameters == null ? List.of() : parameters.stream().map(ToolInputField::name).toList();
    }
}
