package com.laker.admin.module.assistant.tool.workflow;

import com.laker.admin.module.assistant.context.AssistantRequestContext;
import com.laker.admin.module.assistant.tool.AssistantToolArgumentResolution;
import com.laker.admin.module.assistant.tool.AssistantToolArgumentValidationRule;
import com.laker.admin.module.assistant.tool.AssistantVisibleTool;
import com.laker.admin.module.assistant.tool.ToolInputField;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/** 流程详情 Tool 的确定性标识格式校验，不参与自然语言理解。 */
@Component
public class WorkflowProcessDetailArgumentValidationRule implements AssistantToolArgumentValidationRule {
    private static final String PROCESS_IDENTIFIER_KEY = "processIdentifier";
    private static final Pattern INSTANCE_ID = Pattern.compile("\\d+");
    private static final Pattern BUSINESS_ID = Pattern.compile("[A-Za-z0-9]+(?:[-_][A-Za-z0-9]+)+");

    @Override
    public String toolName() {
        return ProcessDetailTool.TOOL_NAME;
    }

    @Override
    public List<ToolInputField> invalidParameters(AssistantRequestContext context,
                                                  AssistantVisibleTool tool,
                                                  AssistantToolArgumentResolution resolution) {
        String identifier = resolution.argumentMap().get(PROCESS_IDENTIFIER_KEY);
        if (identifier == null
                || INSTANCE_ID.matcher(identifier).matches()
                || BUSINESS_ID.matcher(identifier).matches()) {
            return List.of();
        }
        return tool.inputSchema().stream()
                .filter(parameter -> PROCESS_IDENTIFIER_KEY.equals(parameter.name()))
                .toList();
    }
}
