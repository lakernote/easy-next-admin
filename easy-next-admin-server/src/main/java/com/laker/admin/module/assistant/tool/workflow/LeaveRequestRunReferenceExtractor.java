package com.laker.admin.module.assistant.tool.workflow;

import com.laker.admin.module.assistant.state.AssistantRunReference;
import com.laker.admin.module.assistant.state.AssistantRunReferenceExtractor;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.workflow.leave.dto.LeaveRequestView;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将已提交的请假单转换成下一轮可引用项。
 *
 * <p>提交结果里的 requestNo / workflowInstanceId 是稳定业务标识；下一轮用户说“刚刚的单号”
 * 或“查刚刚那个流程”时，LLM 参数抽取会基于 RunState.references 选择这些标识填入工具参数。</p>
 */
@Component
public class LeaveRequestRunReferenceExtractor implements AssistantRunReferenceExtractor {
    @Override
    public List<AssistantRunReference> extract(AssistantToolResult toolResult) {
        if (toolResult == null
                || !LeavePrepareTool.TOOL_NAME.equals(toolResult.toolName())
                || !"LEAVE_REQUEST_SUBMITTED".equals(toolResult.artifactType())
                || !(toolResult.data() instanceof LeaveRequestView view)
                || !StringUtils.hasText(view.getRequestNo())) {
            return List.of();
        }
        return List.of(toReference(view));
    }

    private static AssistantRunReference toReference(LeaveRequestView view) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("businessType", "LEAVE");
        attributes.put("businessId", view.getRequestNo());
        attributes.put("requestNo", view.getRequestNo());
        attributes.put("workflowInstanceId", view.getWorkflowInstanceId() == null ? "" : view.getWorkflowInstanceId());
        attributes.put("leaveType", text(view.getLeaveType()));
        attributes.put("status", text(view.getStatus()));
        attributes.put("workflowStatus", text(view.getWorkflowStatus()));
        attributes.put("title", "请假申请");
        return new AssistantRunReference(
                "workflow.process",
                "请假单",
                view.getRequestNo(),
                displayValue(view),
                attributes
        );
    }

    private static String displayValue(LeaveRequestView view) {
        if (view.getWorkflowInstanceId() == null) {
            return "请假单 / " + view.getRequestNo();
        }
        return "请假单 / " + view.getRequestNo() + " / 流程实例号 " + view.getWorkflowInstanceId();
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
