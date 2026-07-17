package com.laker.admin.module.assistant.tool;

import com.laker.admin.module.assistant.context.AssistantRequestContext;

/** 按当前企业用户与权限生成本轮模型可见 Tool 面。 */
public interface ToolVisibilityPolicy {
    AssistantToolVisibilityPlan selectVisibleTools(AssistantRequestContext context);
}
