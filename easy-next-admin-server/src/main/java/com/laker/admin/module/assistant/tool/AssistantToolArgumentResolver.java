package com.laker.admin.module.assistant.tool;

import com.laker.admin.module.assistant.context.AssistantRequestContext;
import com.laker.admin.module.assistant.state.AssistantConversationState;

import java.util.Map;

/** 将模型按 Tool Schema 生成的原生参数转换为可验证的企业 Tool 参数。 */
public interface AssistantToolArgumentResolver {
    AssistantToolArgumentResolution resolveToolCallArguments(AssistantRequestContext context,
                                                               AssistantVisibleTool tool,
                                                               Map<String, Object> arguments,
                                                               AssistantConversationState conversationState);
}
