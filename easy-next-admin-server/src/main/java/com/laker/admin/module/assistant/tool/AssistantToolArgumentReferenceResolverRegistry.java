package com.laker.admin.module.assistant.tool;

import com.laker.admin.module.assistant.context.AssistantRequestContext;
import com.laker.admin.module.assistant.state.AssistantConversationState;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 按 toolName 分发结构化引用解析器。
 *
 * <p>Resolver 只读取上一轮结构化 RunState，用于跨工具、跨轮次的业务 ID 转换，
 * 不扫描当前消息做语义判断。</p>
 */
@Component
public class AssistantToolArgumentReferenceResolverRegistry {
    private final Map<String, AssistantToolArgumentReferenceResolver> resolvers;

    public AssistantToolArgumentReferenceResolverRegistry(List<AssistantToolArgumentReferenceResolver> resolvers) {
        this.resolvers = (resolvers == null ? List.<AssistantToolArgumentReferenceResolver>of() : resolvers).stream()
                .collect(Collectors.toUnmodifiableMap(
                        AssistantToolArgumentReferenceResolver::toolName,
                        Function.identity(),
                        (left, right) -> {
                            throw new IllegalArgumentException("重复的工具参数引用解析器: " + left.toolName());
                        }
                ));
    }

    public List<AssistantToolArgument> resolveReferences(AssistantRequestContext context,
                                                         AssistantVisibleTool tool,
                                                         AssistantConversationState conversationState,
                                                         List<AssistantToolArgument> arguments) {
        AssistantToolArgumentReferenceResolver resolver = resolvers.get(tool.name());
        if (resolver == null) {
            return arguments == null ? List.of() : List.copyOf(arguments);
        }
        return resolver.resolveReferences(context, tool, conversationState, arguments);
    }
}
