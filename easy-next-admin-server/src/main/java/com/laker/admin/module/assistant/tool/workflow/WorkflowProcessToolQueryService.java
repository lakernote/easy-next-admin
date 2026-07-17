package com.laker.admin.module.assistant.tool.workflow;

import com.laker.admin.module.assistant.context.AssistantRequestContext.AssistantUserContext;

import java.util.List;
import java.util.Optional;

public interface WorkflowProcessToolQueryService {
    /**
     * 查询当前登录用户自己发起的流程。
     *
     * <p>这里故意不用含糊的 {@code visibleProcesses}：管理员“可见”不等于用户口中的“我的申请”。</p>
     */
    List<WorkflowProcessToolItem> myStartedProcesses(AssistantUserContext user, String keyword, int limit);

    default List<WorkflowProcessToolItem> myStartedProcesses(AssistantUserContext user,
                                                             String keyword,
                                                             WorkflowProcessStatusFilter statusFilter,
                                                             int limit) {
        return myStartedProcesses(user, keyword, limit);
    }

    default Optional<WorkflowProcessToolItem> visibleProcessDetail(AssistantUserContext user, String identifier) {
        return myStartedProcesses(user, identifier, 5).stream()
                .filter(item -> matches(item, identifier))
                .findFirst()
                .or(() -> myStartedProcesses(user, identifier, 1).stream().findFirst());
    }

    private static boolean matches(WorkflowProcessToolItem item, String identifier) {
        if (item == null || identifier == null || identifier.isBlank()) {
            return false;
        }
        String normalized = identifier.trim();
        return normalized.equals(String.valueOf(item.instanceId()))
                || normalized.equalsIgnoreCase(item.businessId());
    }
}
