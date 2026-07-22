package com.laker.admin.module.assistant.tool.workflow;

import com.laker.admin.module.assistant.context.AssistantRequestContext;
import com.laker.admin.module.assistant.state.AssistantConversationState;
import com.laker.admin.module.assistant.tool.AssistantToolArgument;
import com.laker.admin.module.assistant.tool.AssistantToolArgumentReferenceResolver;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.AssistantToolRunStatus;
import com.laker.admin.module.assistant.tool.AssistantVisibleTool;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/** 将本轮流程详情中的实例号或序号稳定转换为对应业务单号。 */
@Component
public class LeaveRequestDetailReferenceResolver implements AssistantToolArgumentReferenceResolver {
    private static final String REQUEST_IDENTIFIER_KEY = "requestIdentifier";

    @Override
    public String toolName() {
        return LeaveRequestDetailTool.TOOL_NAME;
    }

    @Override
    public List<AssistantToolArgument> resolveReferences(AssistantRequestContext context,
                                                         AssistantVisibleTool tool,
                                                         AssistantConversationState conversationState,
                                                         List<AssistantToolArgument> arguments) {
        return arguments == null ? List.of() : List.copyOf(arguments);
    }

    @Override
    public List<AssistantToolArgument> resolveReferences(AssistantRequestContext context,
                                                         AssistantVisibleTool tool,
                                                         AssistantConversationState conversationState,
                                                         List<AssistantToolArgument> arguments,
                                                         List<AssistantToolResult> trustedToolResults) {
        List<WorkflowProcessToolItem> processDetails = successfulProcessDetails(trustedToolResults);
        if (arguments == null || arguments.isEmpty() || processDetails.isEmpty()) {
            return arguments == null ? List.of() : List.copyOf(arguments);
        }
        return arguments.stream()
                .map(argument -> REQUEST_IDENTIFIER_KEY.equals(argument.key())
                        ? resolveRequestIdentifier(argument, processDetails)
                        : argument)
                .toList();
    }

    private static AssistantToolArgument resolveRequestIdentifier(AssistantToolArgument argument,
                                                                  List<WorkflowProcessToolItem> processDetails) {
        WorkflowProcessToolItem selected = processDetails.stream()
                .filter(item -> matchesStableIdentifier(argument.value(), item))
                .findFirst()
                .orElseGet(() -> itemAtOrdinal(argument.value(), processDetails));
        if (selected == null || !StringUtils.hasText(selected.businessId())) {
            return argument;
        }
        return new AssistantToolArgument(
                argument.key(),
                argument.label(),
                selected.businessId(),
                selected.businessId()
        );
    }

    private static boolean matchesStableIdentifier(String value, WorkflowProcessToolItem item) {
        if (!StringUtils.hasText(value) || item == null) {
            return false;
        }
        String selected = value.trim();
        return selected.equals(item.businessId())
                || (item.instanceId() != null && selected.equals(item.instanceId().toString()));
    }

    private static WorkflowProcessToolItem itemAtOrdinal(String value,
                                                         List<WorkflowProcessToolItem> processDetails) {
        try {
            int ordinal = Integer.parseInt(value == null ? "" : value.trim());
            return ordinal > 0 && ordinal <= processDetails.size()
                    ? processDetails.get(ordinal - 1)
                    : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static List<WorkflowProcessToolItem> successfulProcessDetails(
            List<AssistantToolResult> trustedToolResults) {
        if (trustedToolResults == null || trustedToolResults.isEmpty()) {
            return List.of();
        }
        return trustedToolResults.stream()
                .filter(result -> result != null
                        && result.status() == AssistantToolRunStatus.EXECUTED
                        && ProcessDetailTool.TOOL_NAME.equals(result.toolName())
                        && "PROCESS_DETAIL".equals(result.artifactType())
                        && result.data() instanceof WorkflowProcessToolItem)
                .map(result -> (WorkflowProcessToolItem) result.data())
                .toList();
    }
}
