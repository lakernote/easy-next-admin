package com.laker.admin.module.assistant.dto;

import com.laker.admin.module.assistant.context.AssistantRequestContext;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionPlan;
import com.laker.admin.module.assistant.tool.AssistantToolArgument;
import com.laker.admin.module.assistant.tool.AssistantToolVisibilityPlan;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.ToolInputField;
import com.laker.admin.module.assistant.trace.AssistantModelCallRecord;
import com.laker.admin.module.assistant.trace.AssistantLatencyBreakdown;
import com.laker.admin.module.assistant.trace.AssistantModelCallTiming;
import com.laker.admin.module.assistant.trace.AssistantRunTrace;
import com.laker.admin.module.assistant.trace.AssistantTokenUsage;
import com.laker.admin.module.assistant.trace.AssistantToolCallRecord;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 当前阶段的助手入口响应。
 *
 * <p>只返回脱敏后的入口上下文、模型决策、可见工具摘要和受控执行计划，不返回角色、权限、IP 等内部细节。</p>
 */
public record AssistantEntryResponse(
        String phase,
        String runId,
        String traceId,
        Long conversationId,
        String normalizedMessage,
        String reply,
        AssistantVisibleToolSnapshot tools,
        ToolExecutionPlanSnapshot plan,
        AssistantToolExecutionSnapshot execution,
        AssistantTraceSnapshot trace,
        AssistantTimeSnapshot time
) {
    private static final String TURN_READY = "TURN_READY";

    public static AssistantEntryResponse turnReady(AssistantRequestContext context,
                                                   AssistantToolVisibilityPlan toolVisibilityPlan,
                                                   ToolExecutionPlan plan,
                                                   AssistantToolResult toolResult,
                                                   String reply,
                                                   AssistantRunTrace trace) {
        return new AssistantEntryResponse(
                TURN_READY,
                context.runId(),
                context.traceId(),
                context.conversationId(),
                context.normalizedMessage(),
                reply,
                AssistantVisibleToolSnapshot.from(toolVisibilityPlan),
                ToolExecutionPlanSnapshot.from(plan),
                AssistantToolExecutionSnapshot.from(toolResult),
                AssistantTraceSnapshot.from(trace),
                new AssistantTimeSnapshot(
                        context.zoneId().getId(),
                        context.requestInstant(),
                        context.today(),
                        context.now()
                )
        );
    }

    public record AssistantVisibleToolSnapshot(
            List<String> toolNames,
            int toolCount,
            String reason
    ) {
        static AssistantVisibleToolSnapshot from(AssistantToolVisibilityPlan plan) {
            return new AssistantVisibleToolSnapshot(
                    plan.tools().stream()
                            .map(tool -> tool.name())
                            .toList(),
                    plan.tools().size(),
                    plan.reason()
            );
        }
    }

    public record ToolInputFieldSnapshot(
            String key,
            String label,
            String type,
            String format,
            String description,
            boolean required,
            List<String> options
    ) {
        static ToolInputFieldSnapshot from(ToolInputField parameter) {
            return new ToolInputFieldSnapshot(
                    parameter.name(),
                    parameter.displayName(),
                    parameter.type().value(),
                    parameter.format().value(),
                    parameter.description(),
                    parameter.required(),
                    parameter.enumValues()
            );
        }
    }

    public record ToolExecutionPlanSnapshot(
            String status,
            String toolName,
            boolean readOnly,
            List<AssistantToolArgumentSnapshot> filledArguments,
            List<ToolInputFieldSnapshot> missingParameters,
            String reason,
            List<String> missingEvidence,
            String actionId,
            String toolCallId,
            List<ToolExecutionStepSnapshot> steps
    ) {
        static ToolExecutionPlanSnapshot from(ToolExecutionPlan plan) {
            return new ToolExecutionPlanSnapshot(
                    plan.status().name(),
                    plan.toolName(),
                    plan.readOnly(),
                    plan.filledArguments().stream()
                            .map(AssistantToolArgumentSnapshot::from)
                            .toList(),
                    plan.missingParameters().stream()
                            .map(ToolInputFieldSnapshot::from)
                            .toList(),
                    plan.reason(),
                    plan.missingEvidence(),
                    plan.actionId(),
                    plan.toolCallId(),
                    plan.steps().stream()
                            .map(ToolExecutionStepSnapshot::from)
                            .toList()
            );
        }
    }

    public record ToolExecutionStepSnapshot(
            int sequence,
            String type,
            String status,
            String toolName,
            String description,
            List<String> inputKeys,
            List<String> outputKeys,
            String observation
    ) {
        static ToolExecutionStepSnapshot from(com.laker.admin.module.assistant.tool.execution.ToolExecutionStep step) {
            return new ToolExecutionStepSnapshot(
                    step.sequence(),
                    step.type().name(),
                    step.status().name(),
                    step.toolName(),
                    step.description(),
                    step.inputKeys(),
                    step.outputKeys(),
                    step.observation()
            );
        }
    }

    public record AssistantToolArgumentSnapshot(
            String key,
            String label,
            String value,
            String displayValue
    ) {
        static AssistantToolArgumentSnapshot from(AssistantToolArgument value) {
            return new AssistantToolArgumentSnapshot(
                    value.key(),
                    value.label(),
                    value.value(),
                    value.displayValue()
            );
        }
    }

    public record AssistantToolExecutionSnapshot(
            String status,
            String toolName,
            String artifactType,
            String summary,
            Object data
    ) {
        static AssistantToolExecutionSnapshot from(AssistantToolResult result) {
            if (result == null) {
                return new AssistantToolExecutionSnapshot("SKIPPED", null, null, "当前计划不需要执行工具", null);
            }
            return new AssistantToolExecutionSnapshot(
                    result.status().name(),
                    result.toolName(),
                    result.artifactType(),
                    result.summary(),
                    result.data()
            );
        }
    }

    public record AssistantTraceSnapshot(
            long totalDurationMs,
            AssistantLatencyBreakdownSnapshot latency,
            AssistantTokenUsageSnapshot tokenUsage,
            List<AssistantTraceStepSnapshot> steps
    ) {
        static AssistantTraceSnapshot from(AssistantRunTrace trace) {
            if (trace == null) {
                return new AssistantTraceSnapshot(
                        0,
                        AssistantLatencyBreakdownSnapshot.from(null),
                        AssistantTokenUsageSnapshot.from(AssistantTokenUsage.ZERO),
                        List.of()
                );
            }
            return new AssistantTraceSnapshot(
                    trace.totalDurationMs(),
                    AssistantLatencyBreakdownSnapshot.from(trace.latency()),
                    AssistantTokenUsageSnapshot.from(trace.tokenUsage()),
                    trace.steps().stream()
                            .map(AssistantTraceStepSnapshot::from)
                            .toList()
            );
        }
    }

    public record AssistantLatencyBreakdownSnapshot(
            long totalDurationMs,
            long modelDurationMs,
            long modelRequestPreparationDurationMs,
            long modelProviderRoundTripDurationMs,
            long modelResponseProcessingDurationMs,
            long toolDurationMs,
            long runtimeDurationMs,
            int modelCallCount,
            int toolCallCount
    ) {
        static AssistantLatencyBreakdownSnapshot from(AssistantLatencyBreakdown latency) {
            AssistantLatencyBreakdown value = latency == null
                    ? new AssistantLatencyBreakdown(0, 0, 0, 0, 0, 0, 0, 0, 0)
                    : latency;
            return new AssistantLatencyBreakdownSnapshot(
                    value.totalDurationMs(),
                    value.modelDurationMs(),
                    value.modelRequestPreparationDurationMs(),
                    value.modelProviderRoundTripDurationMs(),
                    value.modelResponseProcessingDurationMs(),
                    value.toolDurationMs(),
                    value.runtimeDurationMs(),
                    value.modelCallCount(),
                    value.toolCallCount()
            );
        }
    }

    public record AssistantTraceStepSnapshot(
            int sequence,
            String name,
            String component,
            String title,
            String purpose,
            String status,
            long durationMs,
            AssistantTokenUsageSnapshot tokenUsage,
            String summary,
            Object inputPayload,
            Object outputPayload,
            List<AssistantModelCallSnapshot> modelCalls,
            List<AssistantToolCallSnapshot> toolCalls
    ) {
        static AssistantTraceStepSnapshot from(AssistantRunTrace.AssistantTraceStep step) {
            return new AssistantTraceStepSnapshot(
                    step.sequence(),
                    step.name(),
                    step.component(),
                    stepTitle(step.name()),
                    stepPurpose(step.name()),
                    step.status(),
                    step.durationMs(),
                    AssistantTokenUsageSnapshot.from(step.tokenUsage()),
                    step.summary(),
                    step.inputPayload(),
                    step.outputPayload(),
                    step.modelCalls().stream()
                            .map(AssistantModelCallSnapshot::from)
                            .toList(),
                    step.toolCalls().stream()
                            .map(AssistantToolCallSnapshot::from)
                            .toList()
            );
        }

        private static String stepTitle(String name) {
            return switch (name) {
                case "context_build" -> "构建上下文";
                case "conversation_turn_lock" -> "获取会话 Turn 锁";
                case "conversation_state" -> "读取会话状态";
                case "tool_visibility" -> "筛选可见工具";
                case "agent_run" -> "企业助手主循环";
                case "conversation_state_write" -> "写回会话状态";
                default -> name;
            };
        }

        private static String stepPurpose(String name) {
            return switch (name) {
                case "context_build" -> "固定本轮请求、用户身份摘要、访问控制快照、时间和原始消息，避免后续组件各自读取环境导致不一致。";
                case "conversation_turn_lock" -> "非阻塞串行化同一用户会话，避免并发覆盖版本化 Conversation State。";
                case "conversation_state" -> "原子读取 Chat Memory、Pending Action 和 Last Result，供同一个 Tool Calling 模型理解多轮上下文。";
                case "tool_visibility" -> "根据工具注册表和访问控制快照筛出本轮允许模型看到的真实工具。";
                case "agent_run" -> "同一个模型直接选择真实 Tool、生成参数，并消费 Tool Message 中的真实结果继续推理；模型外执行权限、参数、风险、去重和调用预算校验。";
                case "conversation_state_write" -> "在释放会话锁前按主循环返回的 UNCHANGED / UPSERT / CLEAR 显式写回本轮消息、待处理动作和执行状态，避免最后一个 Tool 覆盖前序确认事实。";
                default -> "扩展步骤。";
            };
        }
    }

    public record AssistantModelCallSnapshot(
            String name,
            String model,
            String status,
            AssistantModelCallTimingSnapshot timing,
            AssistantTokenUsageSnapshot tokenUsage,
            Object requestPayload,
            Object responsePayload,
            String errorMessage
    ) {
        static AssistantModelCallSnapshot from(AssistantModelCallRecord call) {
            return new AssistantModelCallSnapshot(
                    call.name(),
                    call.model(),
                    call.status(),
                    AssistantModelCallTimingSnapshot.from(call.timing()),
                    AssistantTokenUsageSnapshot.from(call.tokenUsage()),
                    call.requestPayload(),
                    call.responsePayload(),
                    call.errorMessage()
            );
        }
    }

    public record AssistantModelCallTimingSnapshot(
            String mode,
            long totalDurationMs,
            long requestPreparationDurationMs,
            long providerRoundTripDurationMs,
            long responseProcessingDurationMs,
            Long timeToFirstTokenDurationMs,
            Long generationDurationMs
    ) {
        static AssistantModelCallTimingSnapshot from(AssistantModelCallTiming timing) {
            AssistantModelCallTiming value = timing == null ? AssistantModelCallTiming.ZERO : timing;
            return new AssistantModelCallTimingSnapshot(
                    value.mode().name(),
                    value.totalDurationMs(),
                    value.requestPreparationDurationMs(),
                    value.providerRoundTripDurationMs(),
                    value.responseProcessingDurationMs(),
                    value.timeToFirstTokenDurationMs(),
                    value.generationDurationMs()
            );
        }
    }

    public record AssistantToolCallSnapshot(
            String toolCallId,
            String name,
            String phase,
            String status,
            long durationMs
    ) {
        static AssistantToolCallSnapshot from(AssistantToolCallRecord call) {
            return new AssistantToolCallSnapshot(
                    call.toolCallId(),
                    call.name(),
                    call.phase().name(),
                    call.status(),
                    call.durationMs()
            );
        }
    }

    public record AssistantTokenUsageSnapshot(
            int promptTokens,
            int completionTokens,
            int totalTokens,
            int cachedPromptTokens,
            int reasoningTokens
    ) {
        static AssistantTokenUsageSnapshot from(AssistantTokenUsage usage) {
            AssistantTokenUsage value = usage == null ? AssistantTokenUsage.ZERO : usage;
            return new AssistantTokenUsageSnapshot(
                    value.promptTokens(),
                    value.completionTokens(),
                    value.totalTokens(),
                    value.cachedPromptTokens(),
                    value.reasoningTokens()
            );
        }
    }

    public record AssistantTimeSnapshot(
            String zoneId,
            Instant requestInstant,
            LocalDate today,
            LocalDateTime now
    ) {
    }
}
