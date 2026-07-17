package com.laker.admin.module.assistant.trace;

/**
 * 一次真实模型网关调用的脱敏记录。
 */
public record AssistantModelCallRecord(
        String name,
        String model,
        String status,
        AssistantModelCallTiming timing,
        AssistantTokenUsage tokenUsage,
        Object requestPayload,
        Object responsePayload,
        String errorMessage
) {
    public AssistantModelCallRecord(String name,
                                    String model,
                                    String status,
                                    AssistantModelCallTiming timing,
                                    AssistantTokenUsage tokenUsage) {
        this(name, model, status, timing, tokenUsage, null, null, null);
    }

    public AssistantModelCallRecord {
        timing = timing == null ? AssistantModelCallTiming.ZERO : timing;
        tokenUsage = tokenUsage == null ? AssistantTokenUsage.ZERO : tokenUsage;
    }
}
