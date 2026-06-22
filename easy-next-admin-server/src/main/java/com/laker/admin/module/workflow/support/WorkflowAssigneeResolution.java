package com.laker.admin.module.workflow.support;

import java.util.List;

public record WorkflowAssigneeResolution(List<Long> assigneeIds,
                                         String ruleType,
                                         String ruleName,
                                         String resolvePath) {

    public WorkflowAssigneeResolution {
        assigneeIds = assigneeIds == null ? List.of() : assigneeIds.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
        ruleType = ruleType == null ? "" : ruleType;
        ruleName = ruleName == null ? "" : ruleName;
        resolvePath = resolvePath == null ? "" : resolvePath;
    }

    public static WorkflowAssigneeResolution empty(WorkflowApproverType approverType, String ruleName) {
        return new WorkflowAssigneeResolution(List.of(), approverType == null ? "" : approverType.name(), ruleName, "");
    }
}
