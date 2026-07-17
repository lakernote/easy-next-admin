package com.laker.admin.module.assistant.trace;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次助手请求的简易执行轨迹。
 */
public record AssistantRunTrace(
        long totalDurationMs,
        AssistantTokenUsage tokenUsage,
        AssistantLatencyBreakdown latency,
        List<AssistantTraceStep> steps
) {
    public AssistantRunTrace(long totalDurationMs,
                             AssistantTokenUsage tokenUsage,
                             List<AssistantTraceStep> steps) {
        this(totalDurationMs, tokenUsage, AssistantLatencyBreakdown.from(totalDurationMs, steps), steps);
    }

    public AssistantRunTrace {
        tokenUsage = tokenUsage == null ? AssistantTokenUsage.ZERO : tokenUsage;
        steps = steps == null ? List.of() : List.copyOf(steps);
        latency = latency == null ? AssistantLatencyBreakdown.from(totalDurationMs, steps) : latency;
    }

    public static Builder builder(long startedNanos) {
        return new Builder(startedNanos);
    }

    public static final class Builder {
        private final long startedNanos;
        private final List<AssistantTraceStep> steps = new ArrayList<>();

        private Builder(long startedNanos) {
            this.startedNanos = startedNanos;
        }

        public void addStep(String name,
                            String component,
                            long durationMs,
                            String status,
                            String summary,
                            List<AssistantModelCallRecord> modelCalls,
                            List<AssistantToolCallRecord> toolCalls) {
            addStep(name, component, durationMs, status, summary, null, null, modelCalls, toolCalls);
        }

        public void addStep(String name,
                            String component,
                            long durationMs,
                            String status,
                            String summary,
                            Object inputPayload,
                            Object outputPayload,
                            List<AssistantModelCallRecord> modelCalls,
                            List<AssistantToolCallRecord> toolCalls) {
            List<AssistantModelCallRecord> calls = modelCalls == null ? List.of() : List.copyOf(modelCalls);
            List<AssistantToolCallRecord> executedTools = toolCalls == null ? List.of() : List.copyOf(toolCalls);
            steps.add(new AssistantTraceStep(
                    steps.size() + 1,
                    name,
                    component,
                    status,
                    durationMs,
                    tokenUsage(calls),
                    summary,
                    inputPayload,
                    outputPayload,
                    calls,
                    executedTools
            ));
        }

        public AssistantTraceStep lastStep() {
            if (steps.isEmpty()) {
                return null;
            }
            return steps.get(steps.size() - 1);
        }

        public AssistantRunTrace build(long finishedNanos) {
            long totalDurationMs = toMillis(finishedNanos - startedNanos);
            return new AssistantRunTrace(
                    totalDurationMs,
                    steps.stream()
                            .map(AssistantTraceStep::tokenUsage)
                            .reduce(AssistantTokenUsage.ZERO, AssistantTokenUsage::plus),
                    AssistantLatencyBreakdown.from(totalDurationMs, steps),
                    steps
            );
        }
    }

    public record AssistantTraceStep(
            int sequence,
            String name,
            String component,
            String status,
            long durationMs,
            AssistantTokenUsage tokenUsage,
            String summary,
            Object inputPayload,
            Object outputPayload,
            List<AssistantModelCallRecord> modelCalls,
            List<AssistantToolCallRecord> toolCalls
    ) {
        public AssistantTraceStep {
            tokenUsage = tokenUsage == null ? AssistantTokenUsage.ZERO : tokenUsage;
            modelCalls = modelCalls == null ? List.of() : List.copyOf(modelCalls);
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        }
    }

    public static long toMillis(long nanos) {
        return Math.max(0, Math.round(nanos / 1_000_000.0d));
    }

    private static AssistantTokenUsage tokenUsage(List<AssistantModelCallRecord> modelCalls) {
        return modelCalls.stream()
                .map(AssistantModelCallRecord::tokenUsage)
                .reduce(AssistantTokenUsage.ZERO, AssistantTokenUsage::plus);
    }
}
