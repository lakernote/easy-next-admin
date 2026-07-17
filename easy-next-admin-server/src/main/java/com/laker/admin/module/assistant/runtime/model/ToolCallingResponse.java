package com.laker.admin.module.assistant.runtime.model;

import java.util.List;

/** 模型的一轮响应：普通内容或一个/多个原生 Tool Call。 */
public record ToolCallingResponse(
        String content,
        List<ToolCall> toolCalls,
        ModelFinishReason finishReason,
        String providerFinishReason
) {
    public ToolCallingResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        providerFinishReason = providerFinishReason == null ? "" : providerFinishReason.trim();
        finishReason = finishReason == null
                ? ModelFinishReason.fromProvider(providerFinishReason)
                : finishReason;
    }

    /**
     * 协议适配器和测试都可以直接传供应商原始值；构造后 AgentLoop 只读取标准枚举。
     */
    public ToolCallingResponse(String content, List<ToolCall> toolCalls, String providerFinishReason) {
        this(
                content,
                toolCalls,
                ModelFinishReason.fromProvider(providerFinishReason),
                providerFinishReason
        );
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    public boolean hasContent() {
        return content != null && !content.isBlank();
    }
}
