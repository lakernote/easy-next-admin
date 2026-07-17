package com.laker.admin.module.assistant.tool;

import com.laker.admin.module.assistant.context.AssistantRequestContext;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionStatus;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionPlan;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 默认企业执行策略：读写阶段严格分离，关键风险动作必须交给外部审批或人工处理能力。
 */
@Component
public class DefaultToolExecutionPolicy implements ToolExecutionPolicy {
    @Override
    public PolicyDecision evaluate(AssistantRequestContext context, ToolExecutionPlan plan, AssistantTool tool) {
        ToolDefinition definition = tool.definition();
        if (!hasPermission(context, definition.requiredPermission())) {
            return PolicyDecision.deny("当前用户没有执行该工具所需权限");
        }
        ToolExecutionStatus decision = plan.status();
        if (decision == ToolExecutionStatus.READY) {
            return definition.readOnly()
                    ? PolicyDecision.allow()
                    : PolicyDecision.deny("只读工具调用阶段不能执行写操作工具");
        }
        if (decision == ToolExecutionStatus.APPROVAL_REQUIRED) {
            return definition.requiresConfirmation()
                    ? PolicyDecision.allow()
                    : PolicyDecision.deny("动作草稿阶段只能准备需要确认的写操作工具");
        }
        if (decision == ToolExecutionStatus.APPROVED) {
            if (!definition.requiresConfirmation()) {
                return PolicyDecision.deny("确认执行阶段只能执行需要确认的写操作工具");
            }
            if (definition.riskLevel() == ToolRiskLevel.CRITICAL) {
                return PolicyDecision.deny("关键风险动作需要外部审批或人工处理，当前 Mini Agent 不直接执行");
            }
            return PolicyDecision.allow();
        }
        return PolicyDecision.deny("当前计划阶段不允许执行工具");
    }

    private static boolean hasPermission(AssistantRequestContext context, String permission) {
        if (!StringUtils.hasText(permission)) {
            return true;
        }
        if (context == null || context.user() == null) {
            return false;
        }
        return context.user().superAdmin()
                || (context.user().permissions() != null && context.user().permissions().contains(permission));
    }
}
