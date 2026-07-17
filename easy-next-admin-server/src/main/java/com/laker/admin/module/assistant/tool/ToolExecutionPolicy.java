package com.laker.admin.module.assistant.tool;

import com.laker.admin.module.assistant.context.AssistantRequestContext;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionPlan;

/** 工具真正进入执行器前的企业执行策略端口。 */
public interface ToolExecutionPolicy {
    PolicyDecision evaluate(AssistantRequestContext context, ToolExecutionPlan plan, AssistantTool tool);

    record PolicyDecision(boolean allowed, String reason) {
        public static PolicyDecision allow() {
            return new PolicyDecision(true, "工具执行策略校验通过");
        }

        public static PolicyDecision deny(String reason) {
            return new PolicyDecision(false, reason);
        }
    }
}
