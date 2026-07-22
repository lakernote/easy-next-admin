package com.laker.admin.module.assistant.tool;

import com.laker.admin.module.assistant.context.AssistantRequestContext;
import com.laker.admin.module.assistant.state.AssistantConversationState;

import java.util.List;

/**
 * 工具参数的结构化引用解析器。
 *
 * <p>LLM 负责理解“第一个”“这个流程”等自然语言指代；这里只处理上一轮
 * {@code RunState.references} 或本轮成功 Tool Result 中已经命中的结构化标识转换。例如待办列表引用里的
 * {@code taskId} 不能直接作为流程详情参数，需要转换为同一引用上的 {@code instanceId}。</p>
 */
public interface AssistantToolArgumentReferenceResolver {
    String toolName();

    List<AssistantToolArgument> resolveReferences(AssistantRequestContext context,
                                                  AssistantVisibleTool tool,
                                                  AssistantConversationState conversationState,
                                                  List<AssistantToolArgument> arguments);

    default List<AssistantToolArgument> resolveReferences(AssistantRequestContext context,
                                                          AssistantVisibleTool tool,
                                                          AssistantConversationState conversationState,
                                                          List<AssistantToolArgument> arguments,
                                                          List<AssistantToolResult> trustedToolResults) {
        return resolveReferences(context, tool, conversationState, arguments);
    }
}
