package com.laker.admin.module.assistant.trace;

/**
 * 一次模型调用的耗时拆分。
 *
 * <p>{@code providerRoundTripDurationMs} 包含客户端到模型网关的网络、供应商排队和生成时间。
 * 同步 Chat Completions 不暴露首 Token，因此 TTFT 和纯生成耗时保持 {@code null}，不能用总耗时伪造。</p>
 */
public record AssistantModelCallTiming(
        AssistantModelCallMode mode,
        long totalDurationMs,
        long requestPreparationDurationMs,
        long providerRoundTripDurationMs,
        long responseProcessingDurationMs,
        Long timeToFirstTokenDurationMs,
        Long generationDurationMs
) {
    public static final AssistantModelCallTiming ZERO = synchronous(0, 0, 0, 0);

    public AssistantModelCallTiming {
        mode = mode == null ? AssistantModelCallMode.SYNCHRONOUS : mode;
        totalDurationMs = nonNegative(totalDurationMs);
        requestPreparationDurationMs = nonNegative(requestPreparationDurationMs);
        providerRoundTripDurationMs = nonNegative(providerRoundTripDurationMs);
        responseProcessingDurationMs = nonNegative(responseProcessingDurationMs);
        timeToFirstTokenDurationMs = nullableNonNegative(timeToFirstTokenDurationMs);
        generationDurationMs = nullableNonNegative(generationDurationMs);
    }

    public static AssistantModelCallTiming synchronous(long totalDurationMs,
                                                       long requestPreparationDurationMs,
                                                       long providerRoundTripDurationMs,
                                                       long responseProcessingDurationMs) {
        return new AssistantModelCallTiming(
                AssistantModelCallMode.SYNCHRONOUS,
                totalDurationMs,
                requestPreparationDurationMs,
                providerRoundTripDurationMs,
                responseProcessingDurationMs,
                null,
                null
        );
    }

    private static long nonNegative(long value) {
        return Math.max(0, value);
    }

    private static Long nullableNonNegative(Long value) {
        return value == null ? null : nonNegative(value);
    }
}
