package com.laker.admin.module.assistant.trace;

import java.util.List;

/**
 * 单轮 Agent 的可归因耗时汇总。
 */
public record AssistantLatencyBreakdown(
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
    public static AssistantLatencyBreakdown from(long totalDurationMs,
                                                 List<AssistantRunTrace.AssistantTraceStep> steps) {
        List<AssistantRunTrace.AssistantTraceStep> safeSteps = steps == null ? List.of() : steps;
        List<AssistantModelCallRecord> modelCalls = safeSteps.stream()
                .flatMap(step -> step.modelCalls().stream())
                .toList();
        List<AssistantToolCallRecord> toolCalls = safeSteps.stream()
                .flatMap(step -> step.toolCalls().stream())
                .toList();
        long modelDuration = modelCalls.stream()
                .map(AssistantModelCallRecord::timing)
                .mapToLong(AssistantModelCallTiming::totalDurationMs)
                .sum();
        long requestPreparationDuration = modelCalls.stream()
                .map(AssistantModelCallRecord::timing)
                .mapToLong(AssistantModelCallTiming::requestPreparationDurationMs)
                .sum();
        long providerRoundTripDuration = modelCalls.stream()
                .map(AssistantModelCallRecord::timing)
                .mapToLong(AssistantModelCallTiming::providerRoundTripDurationMs)
                .sum();
        long responseProcessingDuration = modelCalls.stream()
                .map(AssistantModelCallRecord::timing)
                .mapToLong(AssistantModelCallTiming::responseProcessingDurationMs)
                .sum();
        long toolDuration = toolCalls.stream()
                .mapToLong(AssistantToolCallRecord::durationMs)
                .sum();
        long safeTotalDuration = Math.max(0, totalDurationMs);
        return new AssistantLatencyBreakdown(
                safeTotalDuration,
                modelDuration,
                requestPreparationDuration,
                providerRoundTripDuration,
                responseProcessingDuration,
                toolDuration,
                Math.max(0, safeTotalDuration - modelDuration - toolDuration),
                modelCalls.size(),
                toolCalls.size()
        );
    }
}
