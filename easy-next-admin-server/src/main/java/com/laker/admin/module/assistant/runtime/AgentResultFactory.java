package com.laker.admin.module.assistant.runtime;

import com.laker.admin.module.assistant.context.AssistantRequestContext;
import com.laker.admin.module.assistant.runtime.model.ModelFinishReason;
import com.laker.admin.module.assistant.state.AssistantPendingActionUpdate;
import com.laker.admin.module.assistant.tool.AssistantToolArgument;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.AssistantToolRunStatus;
import com.laker.admin.module.assistant.tool.ToolInputField;
import com.laker.admin.module.assistant.tool.ToolSchemaFormat;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionPlan;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionStatus;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionStepStatus;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionStepType;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 统一创建 Agent Run 的计划、聚合结果和确定性回退回复。
 *
 * <p>AgentLoop 只负责模型迭代和状态转移；如何把运行状态投影成最终结果集中在这里，
 * 避免主循环被 DTO 组装与 fallback 文案淹没。</p>
 */
final class AgentResultFactory {
    private AgentResultFactory() {
    }

    static AgentResult fromRunState(AgentLoopState runState,
                                    String reply,
                                    AgentTerminationReason terminationReason) {
        return create(
                runState.currentPlan(),
                runState.toolResults(),
                reply,
                terminationReason,
                runState.modelCallCount(),
                runState.toolCallCount(),
                runState.executedToolNames(),
                runState.pendingActionUpdate()
        );
    }

    static AgentResult finalAnswer(AssistantRequestContext context,
                                   AgentLoopState runState,
                                   String content) {
        ToolExecutionPlan plan = runState.currentPlan();
        String reply = StringUtils.hasText(content)
                ? content.trim()
                : fallbackReply(plan, aggregateToolResults(runState.toolResults()));
        if (plan == null) {
            runState.updateCurrentPlan(directResponsePlan(context));
        }
        return fromRunState(runState, reply, runState.finalResponseTerminationReason());
    }

    static AgentResult waitingForUser(AgentLoopState runState) {
        AgentTerminationReason terminationReason = runState.currentPlan() != null
                && runState.currentPlan().status() == ToolExecutionStatus.APPROVAL_REQUIRED
                ? AgentTerminationReason.WAITING_FOR_CONFIRMATION
                : AgentTerminationReason.WAITING_FOR_INPUT;
        return fromRunState(
                runState,
                fallbackReply(runState.currentPlan(), aggregateToolResults(runState.toolResults())),
                terminationReason
        );
    }

    static AgentResult fallback(AgentLoopState runState, AgentTerminationReason terminationReason) {
        return fromRunState(
                runState,
                fallbackReply(runState.currentPlan(), aggregateToolResults(runState.toolResults())),
                terminationReason
        );
    }

    static AgentResult modelFailure(AssistantRequestContext context,
                                    AgentLoopState runState) {
        if (runState.currentPlan() != null) {
            return fallback(runState, AgentTerminationReason.MODEL_ERROR);
        }
        return modelUnavailable(
                context,
                runState.modelCallCount(),
                runState.toolCallCount(),
                runState.executedToolNames()
        );
    }

    static AgentResult abnormalFinish(AssistantRequestContext context,
                                      AgentLoopState runState,
                                      ModelFinishReason finishReason) {
        AgentTerminationReason terminationReason = terminationReason(finishReason);
        if (runState.currentPlan() != null) {
            return fallback(runState, terminationReason);
        }
        String evidence = switch (finishReason) {
            case MAX_TOKENS -> "model.response.truncated";
            case CONTENT_FILTER -> "model.response.filtered";
            case REFUSAL -> "model.response.refused";
            case PAUSED -> "model.response.paused";
            case ERROR -> "model.response.error";
            case UNKNOWN, STOP, TOOL_CALLS, UNSPECIFIED -> "model.response.invalid_finish_reason";
        };
        String reason = switch (finishReason) {
            case MAX_TOKENS -> "模型回复达到长度上限，响应可能不完整";
            case CONTENT_FILTER -> "模型响应被供应商安全策略过滤";
            case REFUSAL -> "模型拒绝完成当前请求";
            case PAUSED -> "模型要求暂停并续接，当前 Runtime 未自动续接";
            case ERROR -> "模型报告生成或协议错误";
            case UNKNOWN, STOP, TOOL_CALLS, UNSPECIFIED -> "模型返回了不一致的结束状态";
        };
        runState.updateCurrentPlan(blockedPlan(context, null, reason, evidence));
        String reply = finishReason == ModelFinishReason.MAX_TOKENS
                ? "模型回复因长度限制未完整生成，请缩短问题后重试。"
                : "模型未能可靠完成本次回复，请调整问题或稍后重试。";
        return fromRunState(runState, reply, terminationReason);
    }

    static AgentResult modelCallLimit(AssistantRequestContext context,
                                      AgentLoopState runState) {
        if (runState.currentPlan() == null) {
            runState.updateCurrentPlan(blockedPlan(
                    context,
                    null,
                    "模型调用达到本轮安全预算",
                    "model.call_limit"
            ));
        }
        return fallback(runState, AgentTerminationReason.MODEL_CALL_LIMIT);
    }

    static AgentResult create(ToolExecutionPlan plan,
                              List<AssistantToolResult> toolResults,
                              String reply,
                              AgentTerminationReason terminationReason,
                              int modelCallCount,
                              int toolCallCount,
                              List<String> executedToolNames,
                              AssistantPendingActionUpdate pendingActionUpdate) {
        return new AgentResult(
                completeFinalResponseStep(plan, reply),
                aggregateToolResults(toolResults),
                reply,
                terminationReason,
                modelCallCount,
                toolCallCount,
                executedToolNames,
                pendingActionUpdate
        );
    }

    static AgentResult modelUnavailable(AssistantRequestContext context,
                                        int modelCalls,
                                        int toolCalls,
                                        List<String> calledToolNames) {
        ToolExecutionPlan plan = new ToolExecutionPlan(
                context.runId(),
                context.traceId(),
                ToolExecutionStatus.BLOCKED,
                null,
                false,
                List.of(),
                List.of(),
                List.of("model.tool_call"),
                "暂时无法完成可靠推理"
        );
        return new AgentResult(
                plan,
                AssistantToolResult.skipped("Tool Calling 模型当前不可用"),
                "暂时无法完成可靠回复，请稍后重试。",
                AgentTerminationReason.MODEL_ERROR,
                modelCalls,
                toolCalls,
                calledToolNames,
                AssistantPendingActionUpdate.unchanged()
        );
    }

    static ToolExecutionPlan directResponsePlan(AssistantRequestContext context) {
        return new ToolExecutionPlan(
                context.runId(),
                context.traceId(),
                ToolExecutionStatus.DIRECT_RESPONSE,
                null,
                true,
                List.of(),
                List.of(),
                List.of(),
                "模型判断当前请求不需要工具"
        );
    }

    static ToolExecutionPlan blockedPlan(AssistantRequestContext context,
                                         String toolName,
                                         String reason,
                                         String evidence) {
        return new ToolExecutionPlan(
                context.runId(),
                context.traceId(),
                ToolExecutionStatus.BLOCKED,
                toolName,
                false,
                List.of(),
                List.of(),
                List.of(evidence),
                reason
        );
    }

    private static AgentTerminationReason terminationReason(ModelFinishReason finishReason) {
        return switch (finishReason) {
            case MAX_TOKENS -> AgentTerminationReason.MODEL_MAX_TOKENS;
            case CONTENT_FILTER -> AgentTerminationReason.MODEL_CONTENT_FILTERED;
            case REFUSAL -> AgentTerminationReason.MODEL_REFUSED;
            case PAUSED -> AgentTerminationReason.MODEL_PAUSED;
            case ERROR -> AgentTerminationReason.MODEL_ERROR;
            case UNKNOWN, STOP, TOOL_CALLS, UNSPECIFIED -> AgentTerminationReason.MODEL_PROTOCOL_ERROR;
        };
    }

    static AssistantToolResult aggregateToolResults(List<AssistantToolResult> results) {
        if (results == null || results.isEmpty()) {
            return AssistantToolResult.skipped("当前回复没有执行工具");
        }
        if (results.size() == 1) {
            return results.get(0);
        }
        String summary = userVisibleSummaryResults(results).stream()
                .map(AssistantToolResult::summary)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.joining("；"));
        return new AssistantToolResult(
                "system.multiple_tools",
                aggregateToolStatus(results),
                "MULTI_TOOL_RESULT",
                summary,
                Map.of("results", List.copyOf(results))
        );
    }

    /**
     * 同一个 Tool 用候选标识重试并在后续成功时，不把已经恢复的中间失败继续拼进用户文案。
     * 完整结果和 PARTIAL_SUCCESS 状态仍保留在聚合 data / Trace 中，便于调试与审计。
     */
    private static List<AssistantToolResult> userVisibleSummaryResults(List<AssistantToolResult> results) {
        return java.util.stream.IntStream.range(0, results.size())
                .filter(index -> !isSupersededFailure(results, index))
                .mapToObj(results::get)
                .toList();
    }

    private static boolean isSupersededFailure(List<AssistantToolResult> results, int index) {
        AssistantToolResult candidate = results.get(index);
        if (candidate.status() != AssistantToolRunStatus.FAILED
                || !StringUtils.hasText(candidate.toolName())) {
            return false;
        }
        return results.subList(index + 1, results.size()).stream()
                .anyMatch(later -> candidate.toolName().equals(later.toolName())
                        && (later.status() == AssistantToolRunStatus.EXECUTED
                        || later.status() == AssistantToolRunStatus.PARTIAL_SUCCESS));
    }

    static String fallbackReply(ToolExecutionPlan plan, AssistantToolResult result) {
        if (plan == null) {
            return "暂时无法生成可靠回复，请稍后重试。";
        }
        if (plan.status() == ToolExecutionStatus.INPUT_REQUIRED && !plan.missingParameters().isEmpty()) {
            String missing = plan.missingParameters().stream()
                    .map(AgentResultFactory::formatMissingParameter)
                    .collect(Collectors.joining("、"));
            return "还需要补充：" + missing + "。";
        }
        if (result != null && (result.status() == AssistantToolRunStatus.FAILED
                || result.status() == AssistantToolRunStatus.INDETERMINATE)) {
            return result.summary();
        }
        if (plan.status() == ToolExecutionStatus.APPROVAL_REQUIRED) {
            String arguments = plan.filledArguments().stream()
                    .map(AgentResultFactory::formatArgument)
                    .collect(Collectors.joining("，"));
            String summary = result != null && StringUtils.hasText(result.summary())
                    ? result.summary().replace("。", "")
                    : "已生成待确认动作草稿";
            return summary + (arguments.isBlank() ? "" : "：" + arguments) + "。请确认后我再提交。";
        }
        if (result != null && StringUtils.hasText(result.summary())) {
            return result.summary();
        }
        return StringUtils.hasText(plan.reason()) ? plan.reason() : "当前请求还不能可靠完成。";
    }

    static String formatArgument(AssistantToolArgument argument) {
        String value = StringUtils.hasText(argument.displayValue()) ? argument.displayValue() : argument.value();
        return argument.label() + "：" + value;
    }

    private static ToolExecutionPlan completeFinalResponseStep(ToolExecutionPlan plan, String reply) {
        if (plan == null || !StringUtils.hasText(reply)) {
            return plan;
        }
        return plan.withExecutedSteps(plan.steps().stream()
                .map(step -> step.type() == ToolExecutionStepType.FINAL_RESPONSE
                        && step.status() == ToolExecutionStepStatus.PENDING
                        ? step.withStatus(ToolExecutionStepStatus.SUCCEEDED, "已输出最终回复")
                        : step)
                .toList());
    }

    private static AssistantToolRunStatus aggregateToolStatus(List<AssistantToolResult> results) {
        boolean hasExecuted = results.stream()
                .anyMatch(result -> result.status() == AssistantToolRunStatus.EXECUTED
                        || result.status() == AssistantToolRunStatus.PARTIAL_SUCCESS);
        boolean hasNotExecuted = results.stream()
                .anyMatch(result -> result.status() != AssistantToolRunStatus.EXECUTED
                        && result.status() != AssistantToolRunStatus.PARTIAL_SUCCESS);
        if (hasExecuted && hasNotExecuted) {
            return AssistantToolRunStatus.PARTIAL_SUCCESS;
        }
        if (hasExecuted) {
            return AssistantToolRunStatus.EXECUTED;
        }
        if (results.stream().anyMatch(result -> result.status() == AssistantToolRunStatus.FAILED)) {
            return AssistantToolRunStatus.FAILED;
        }
        if (results.stream().anyMatch(result -> result.status() == AssistantToolRunStatus.INDETERMINATE)) {
            return AssistantToolRunStatus.INDETERMINATE;
        }
        return AssistantToolRunStatus.SKIPPED;
    }

    private static String formatMissingParameter(ToolInputField parameter) {
        if (!parameter.enumValues().isEmpty()) {
            return parameter.displayName() + "（可选：" + String.join("、", parameter.enumDisplayValues()) + "）";
        }
        if (parameter.format() != ToolSchemaFormat.NONE) {
            // 用户可以继续使用“明天下午”等自然语言，下一轮模型会规范化为协议格式。
            return parameter.displayName();
        }
        return StringUtils.hasText(parameter.description())
                ? parameter.displayName() + "（" + parameter.description() + "）"
                : parameter.displayName();
    }
}
