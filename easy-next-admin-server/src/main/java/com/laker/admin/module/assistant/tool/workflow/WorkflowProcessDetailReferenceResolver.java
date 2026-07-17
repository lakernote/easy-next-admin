package com.laker.admin.module.assistant.tool.workflow;

import com.laker.admin.module.assistant.context.AssistantRequestContext;
import com.laker.admin.module.assistant.state.AssistantConversationState;
import com.laker.admin.module.assistant.state.AssistantRunReference;
import com.laker.admin.module.assistant.tool.AssistantToolArgument;
import com.laker.admin.module.assistant.tool.AssistantToolArgumentReferenceResolver;
import com.laker.admin.module.assistant.tool.AssistantVisibleTool;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * 将上一轮待办引用转换为流程详情工具需要的流程实例标识。
 *
 * <p>待办工具结果的 {@code reference.value} 是 taskId；流程详情查询需要 instanceId/businessId。
 * 这里不判断“第一个”等语义，只在 LLM 已经选中某个 reference 后，把错误层级的 taskId
 * 纠正成同一 reference 上的 instanceId。</p>
 */
@Component
public class WorkflowProcessDetailReferenceResolver implements AssistantToolArgumentReferenceResolver {
    private static final String PROCESS_IDENTIFIER_KEY = "processIdentifier";
    private static final String WORKFLOW_TASK_REFERENCE_TYPE = "workflow.task";

    @Override
    public String toolName() {
        return ProcessDetailTool.TOOL_NAME;
    }

    @Override
    public List<AssistantToolArgument> resolveReferences(AssistantRequestContext context,
                                                         AssistantVisibleTool tool,
                                                         AssistantConversationState conversationState,
                                                         List<AssistantToolArgument> arguments) {
        if (arguments == null || arguments.isEmpty()
                || conversationState == null
                || conversationState.lastResult() == null
                || conversationState.lastResult().references().isEmpty()) {
            return arguments == null ? List.of() : List.copyOf(arguments);
        }
        return arguments.stream()
                .map(argument -> PROCESS_IDENTIFIER_KEY.equals(argument.key())
                        ? resolveProcessIdentifier(argument, conversationState.lastResult().references())
                        : argument)
                .toList();
    }

    private static AssistantToolArgument resolveProcessIdentifier(AssistantToolArgument argument,
                                                                  List<AssistantRunReference> references) {
        return references.stream()
                .filter(reference -> WORKFLOW_TASK_REFERENCE_TYPE.equals(reference.type()))
                .filter(reference -> matchesSelectedReference(argument, reference))
                .map(reference -> toInstanceIdentifier(argument, reference))
                .flatMap(List::stream)
                .findFirst()
                .orElse(argument);
    }

    private static List<AssistantToolArgument> toInstanceIdentifier(AssistantToolArgument argument,
                                                                    AssistantRunReference reference) {
        String instanceId = text(reference.attributes().get("instanceId"));
        if (!StringUtils.hasText(instanceId)) {
            return List.of();
        }
        String displayValue = StringUtils.hasText(reference.displayValue())
                ? reference.displayValue()
                : instanceId;
        return List.of(new AssistantToolArgument(argument.key(), argument.label(), instanceId, displayValue));
    }

    private static boolean matchesSelectedReference(AssistantToolArgument argument, AssistantRunReference reference) {
        String selectedValue = compact(argument.value());
        String selectedDisplayValue = compact(argument.displayValue());
        if (!StringUtils.hasText(selectedValue) && !StringUtils.hasText(selectedDisplayValue)) {
            return false;
        }
        return equalsCompact(selectedValue, reference.value())
                || equalsCompact(selectedValue, reference.attributes().get("taskId"))
                || equalsCompact(selectedDisplayValue, reference.value())
                || equalsCompact(selectedDisplayValue, reference.displayValue())
                || equalsCompact(selectedDisplayValue, reference.attributes().get("taskId"));
    }

    private static boolean equalsCompact(String selected, Object candidate) {
        String candidateText = compact(text(candidate));
        return StringUtils.hasText(selected)
                && StringUtils.hasText(candidateText)
                && selected.equals(candidateText);
    }

    private static String compact(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder compact = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (!Character.isWhitespace(current)
                    && current != '，'
                    && current != ','
                    && current != '。'
                    && current != '；'
                    && current != ';'
                    && current != '：'
                    && current != ':'
                    && current != '、') {
                compact.append(current);
            }
        }
        return compact.toString().trim();
    }

    private static String text(Object value) {
        return Objects.toString(value, "");
    }
}
