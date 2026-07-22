package com.laker.admin.module.assistant.tool.workflow;

import com.laker.admin.module.assistant.context.AssistantRequestContext;
import com.laker.admin.module.assistant.tool.AssistantToolArgumentResolution;
import com.laker.admin.module.assistant.tool.AssistantToolArgumentValidationRule;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.AssistantToolRunStatus;
import com.laker.admin.module.assistant.tool.AssistantVisibleTool;
import com.laker.admin.module.assistant.tool.ToolInputField;
import com.laker.admin.module.workflow.leave.support.LeaveType;
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
        return invalidParameters(context, tool, resolution, List.of());
    }

    @Override
    public List<ToolInputField> invalidParameters(AssistantRequestContext context,
                                                  AssistantVisibleTool tool,
                                                  AssistantToolArgumentResolution resolution,
                                                  List<AssistantToolResult> trustedToolResults) {
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
        String leaveType = arguments.get("leaveType");
        if (leaveType != null
                && resolution.providedArgumentKeys().contains("leaveType")
                && LeaveType.fromCode(leaveType)
                .map(type -> !type.isExplicitlyMentionedIn(context.normalizedMessage())
                        && !matchesTrustedLeaveType(type, trustedToolResults))
                .orElse(true)) {
            // 假别必须来自用户原文或本轮最后一张成功查询的请假单，模型不能补默认值。
            addInvalid(invalid, parameterMap, "leaveType");
        }
        String reason = arguments.get("reason");
        if (reason != null
                && resolution.providedArgumentKeys().contains("reason")
                && ((!context.normalizedMessage().contains(reason)
                || occupiesMostOfMessage(context.normalizedMessage(), reason))
                && !matchesTrustedLeaveReason(reason, trustedToolResults))) {
            // 请假原因必须来自用户原文或本轮最后一张成功查询的请假单。
            // 几乎复制整句请求的值不是独立原因证据；这个校验不承担意图识别或 Tool 选择。
            addInvalid(invalid, parameterMap, "reason");
        }
        return invalid;
    }

    private static boolean matchesTrustedLeaveType(LeaveType leaveType,
                                                   List<AssistantToolResult> trustedToolResults) {
        LeaveRequestToolItem source = lastTrustedLeaveDetail(trustedToolResults);
        return source != null && leaveType.label().equals(source.leaveType());
    }

    private static boolean matchesTrustedLeaveReason(String reason,
                                                     List<AssistantToolResult> trustedToolResults) {
        LeaveRequestToolItem source = lastTrustedLeaveDetail(trustedToolResults);
        return source != null && reason.equals(source.reason());
    }

    private static LeaveRequestToolItem lastTrustedLeaveDetail(List<AssistantToolResult> trustedToolResults) {
        if (trustedToolResults == null || trustedToolResults.isEmpty()) {
            return null;
        }
        for (int index = trustedToolResults.size() - 1; index >= 0; index--) {
            AssistantToolResult result = trustedToolResults.get(index);
            if (result != null
                    && result.status() == AssistantToolRunStatus.EXECUTED
                    && LeaveRequestDetailTool.TOOL_NAME.equals(result.toolName())
                    && "LEAVE_REQUEST_DETAIL".equals(result.artifactType())
                    && result.data() instanceof LeaveRequestToolItem item) {
                return item;
            }
        }
        return null;
    }

    private static boolean occupiesMostOfMessage(String message, String value) {
        if (message == null || value == null) {
            return false;
        }
        int messageLength = message.codePointCount(0, message.length());
        int valueLength = value.codePointCount(0, value.length());
        return messageLength > 0 && valueLength * 4 >= messageLength * 3;
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
