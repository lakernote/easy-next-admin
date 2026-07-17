package com.laker.admin.module.assistant.dto;

import com.laker.admin.module.assistant.context.AssistantRequestContext;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AssistantMessageRequest(
        @NotBlank(message = "消息内容不能为空")
        @Size(max = 2000, message = "消息内容不能超过 2000 字")
        String message,
        @Positive(message = "conversationId 必须是正整数")
        @Max(value = AssistantRequestContext.MAX_SAFE_CONVERSATION_ID,
                message = "conversationId 超出 JavaScript 安全整数范围")
        Long conversationId
) {
}
