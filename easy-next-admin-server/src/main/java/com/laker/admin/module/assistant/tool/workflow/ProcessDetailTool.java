package com.laker.admin.module.assistant.tool.workflow;

import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.assistant.tool.AssistantTool;
import com.laker.admin.module.assistant.tool.AssistantToolExecutionContext;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.AssistantToolRunStatus;
import com.laker.admin.module.assistant.tool.ToolDefinition;
import com.laker.admin.module.assistant.tool.ToolExecutionMode;
import com.laker.admin.module.assistant.tool.ToolInputField;
import com.laker.admin.module.assistant.tool.ToolOutputField;
import com.laker.admin.module.assistant.tool.ToolRiskLevel;
import com.laker.admin.module.assistant.tool.ToolSchemaFormat;
import com.laker.admin.module.assistant.tool.ToolSchemaType;
import com.laker.admin.module.assistant.tool.ToolSubjectScope;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class ProcessDetailTool implements AssistantTool {
    public static final String TOOL_NAME = "workflow.process.detail";
    private static final String DESCRIPTION = """
            用于按用户明确给出的流程实例号、业务单号或会话中的唯一流程引用，查询单个流程的当前节点和状态；不用于先定位最近流程、查询流程列表、待办任务或申请单业务字段。本结果已是完整进度答案，不需要扩查申请单。
            """;

    private static final ToolDefinition DEFINITION = new ToolDefinition(
            TOOL_NAME,
            "查询流程详情",
            DESCRIPTION,
            EasyPermissions.Workflow.VIEW,
            ToolExecutionMode.READ_ONLY,
            ToolRiskLevel.LOW,
            List.of(new ToolInputField(
                    "processIdentifier",
                    "流程标识",
                    ToolSchemaType.STRING,
                    ToolSchemaFormat.NONE,
                    "流程实例号或业务单号，如 90001 或 LV-20260709-000001。",
                    true,
                    List.of()
            )),
            List.of(
                    new ToolOutputField("instanceId", ToolSchemaType.INTEGER, true),
                    new ToolOutputField("businessType", ToolSchemaType.STRING, false),
                    new ToolOutputField("businessId", ToolSchemaType.STRING, false),
                    new ToolOutputField("title", ToolSchemaType.STRING, false),
                    new ToolOutputField("status", ToolSchemaType.STRING, true),
                    new ToolOutputField("currentNodeKey", ToolSchemaType.STRING, false)
            ),
            ToolSubjectScope.AUTHORIZED_TARGET
    );

    private final WorkflowProcessToolQueryService queryService;

    public ProcessDetailTool(WorkflowProcessToolQueryService queryService) {
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
    }

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public AssistantToolResult execute(AssistantToolExecutionContext context) {
        Map<String, String> arguments = context.plan().argumentMap();
        String identifier = arguments.get("processIdentifier");
        if (!StringUtils.hasText(identifier)) {
            return AssistantToolResult.failed(TOOL_NAME, "缺少流程标识，无法查询流程详情");
        }
        return queryService.visibleProcessDetail(context.requestContext().user(), identifier)
                .map(item -> new AssistantToolResult(
                        TOOL_NAME,
                        AssistantToolRunStatus.EXECUTED,
                        "PROCESS_DETAIL",
                        summary(item),
                        item
                ))
                .orElseGet(() -> AssistantToolResult.failed(TOOL_NAME, "没有找到当前用户可见的流程详情"));
    }

    private static String summary(WorkflowProcessToolItem item) {
        String businessId = StringUtils.hasText(item.businessId()) ? "，业务单号：" + item.businessId() : "";
        return "流程详情：%s，流程实例号：%s%s，当前节点：%s，状态：%s".formatted(
                item.title(),
                item.instanceId(),
                businessId,
                item.currentNodeKey(),
                item.status()
        );
    }
}
