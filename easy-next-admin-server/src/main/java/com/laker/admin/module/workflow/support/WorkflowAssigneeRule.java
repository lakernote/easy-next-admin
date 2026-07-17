package com.laker.admin.module.workflow.support;

import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public record WorkflowAssigneeRule(WorkflowApproverType approverType,
                                   List<Long> assigneeIds,
                                   String roleCode,
                                   boolean declared) {

    public WorkflowAssigneeRule {
        approverType = approverType == null ? WorkflowApproverType.UNKNOWN : approverType;
        assigneeIds = assigneeIds == null ? List.of() : assigneeIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        roleCode = roleCode == null ? "" : roleCode.trim();
    }

    public static WorkflowAssigneeRule of(String approverType,
                                          Collection<Long> assigneeIds,
                                          String roleCode) {
        List<Long> normalizedAssigneeIds = assigneeIds == null ? List.of() : assigneeIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        WorkflowApproverType type = WorkflowApproverType.of(approverType);
        if (type == WorkflowApproverType.UNKNOWN && !normalizedAssigneeIds.isEmpty()) {
            type = WorkflowApproverType.USER;
        }
        if (type == WorkflowApproverType.UNKNOWN && StringUtils.hasText(roleCode)) {
            type = WorkflowApproverType.ROLE;
        }
        boolean declaredRule = StringUtils.hasText(approverType)
                || !normalizedAssigneeIds.isEmpty()
                || StringUtils.hasText(roleCode);
        return new WorkflowAssigneeRule(type, normalizedAssigneeIds, roleCode, declaredRule);
    }

    public boolean shouldFailWhenUnresolved() {
        return declared || approverType.isConfiguredRule();
    }
}
