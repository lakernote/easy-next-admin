package com.laker.admin.module.assistant.tool.workflow;

import com.laker.admin.module.assistant.context.AssistantRequestContext;
import com.laker.admin.module.assistant.tool.AssistantToolArgumentResolution;
import com.laker.admin.module.assistant.tool.AssistantToolArgumentValidationRule;
import com.laker.admin.module.assistant.tool.AssistantVisibleTool;
import com.laker.admin.module.assistant.tool.ToolInputField;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class LeavePrepareToolArgumentValidationRule implements AssistantToolArgumentValidationRule {
    @Override
    public String toolName() {
        return LeavePrepareTool.TOOL_NAME;
    }

    @Override
    public List<ToolInputField> invalidParameters(AssistantRequestContext context,
                                                  AssistantVisibleTool tool,
                                                  AssistantToolArgumentResolution resolution) {
        Map<String, ToolInputField> parameterMap = tool.inputSchema().stream()
                .collect(Collectors.toMap(
                        ToolInputField::name,
                        Function.identity(),
                        (left, right) -> left
                ));
        Map<String, String> arguments = resolution.argumentMap();
        List<ToolInputField> invalid = new ArrayList<>();
        LocalDateTime startTime = parse(arguments.get("startTime"));
        LocalDateTime endTime = parse(arguments.get("endTime"));
        if (startTime != null && endTime != null && !startTime.isBefore(endTime)) {
            addInvalid(invalid, parameterMap, "startTime");
            addInvalid(invalid, parameterMap, "endTime");
        }
        String reason = arguments.get("reason");
        if (reason != null
                && resolution.providedArgumentKeys().contains("reason")
                && !context.normalizedMessage().contains(reason)) {
            // 请假原因会进入真实企业单据，必须能在本轮用户原文中找到证据。
            // 这是自由文本事实的确定性来源校验，不承担意图识别，也不使用关键词/正则选择 Tool。
            addInvalid(invalid, parameterMap, "reason");
        }
        return invalid;
    }

    private static LocalDateTime parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static void addInvalid(List<ToolInputField> invalid,
                                   Map<String, ToolInputField> parameterMap,
                                   String key) {
        ToolInputField parameter = parameterMap.get(key);
        if (parameter != null && !invalid.contains(parameter)) {
            invalid.add(parameter);
        }
    }
}
