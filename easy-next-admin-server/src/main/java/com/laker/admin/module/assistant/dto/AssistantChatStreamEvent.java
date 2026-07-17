package com.laker.admin.module.assistant.dto;

/**
 * 面向普通用户的流式对话事件。
 *
 * <p>这里只提供交互所需的阶段、提示语和最终精简响应；Prompt、Tool 参数、模型响应、
 * Trace 和内部异常信息只保留在同步调试接口与服务端观测中。</p>
 */
public record AssistantChatStreamEvent(
        String event,
        String step,
        int sequence,
        AssistantChatPhase phase,
        String message,
        AssistantChatResponse response
) {
    public static AssistantChatStreamEvent progress(String step,
                                                    int sequence,
                                                    AssistantChatPhase phase,
                                                    String message) {
        return new AssistantChatStreamEvent("progress", step, sequence, phase, message, null);
    }

    public static AssistantChatStreamEvent done(AssistantEntryResponse response) {
        return new AssistantChatStreamEvent(
                "done",
                "completed",
                0,
                AssistantChatPhase.COMPLETED,
                "处理完成",
                AssistantChatResponse.from(response)
        );
    }

    public static AssistantChatStreamEvent failed(String message) {
        return new AssistantChatStreamEvent(
                "error",
                "failed",
                0,
                AssistantChatPhase.FAILED,
                message == null || message.isBlank() ? "助手执行失败，请稍后重试" : message,
                null
        );
    }

    public enum AssistantChatPhase {
        PREPARING,
        THINKING,
        USING_TOOL,
        SAVING,
        COMPLETED,
        FAILED
    }
}
