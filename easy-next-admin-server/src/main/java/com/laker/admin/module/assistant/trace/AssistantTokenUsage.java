package com.laker.admin.module.assistant.trace;

/**
 * 单次助手链路里的模型 token 用量。
 *
 * <p>不是所有 OpenAI-compatible 网关都会返回 usage；未返回时按 0 处理，
 * 这样调试响应结构保持稳定。</p>
 */
public record AssistantTokenUsage(
        int promptTokens,
        int completionTokens,
        int totalTokens,
        int cachedPromptTokens,
        int reasoningTokens
) {
    public static final AssistantTokenUsage ZERO = new AssistantTokenUsage(0, 0, 0, 0, 0);

    public AssistantTokenUsage {
        promptTokens = Math.max(0, promptTokens);
        completionTokens = Math.max(0, completionTokens);
        totalTokens = Math.max(0, totalTokens);
        cachedPromptTokens = Math.max(0, cachedPromptTokens);
        reasoningTokens = Math.max(0, reasoningTokens);
    }

    public AssistantTokenUsage plus(AssistantTokenUsage other) {
        if (other == null) {
            return this;
        }
        return new AssistantTokenUsage(
                promptTokens + other.promptTokens(),
                completionTokens + other.completionTokens(),
                totalTokens + other.totalTokens(),
                cachedPromptTokens + other.cachedPromptTokens(),
                reasoningTokens + other.reasoningTokens()
        );
    }
}
