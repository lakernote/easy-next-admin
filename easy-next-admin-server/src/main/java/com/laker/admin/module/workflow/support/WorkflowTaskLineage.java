package com.laker.admin.module.workflow.support;

import java.util.Collection;
import java.util.List;

public final class WorkflowTaskLineage {

    private WorkflowTaskLineage() {
    }

    public static Long sequentialCursorAssigneeId(List<Long> assigneeIds,
                                                  Long approvedTaskAssigneeId,
                                                  Collection<Delegation> delegations) {
        Long reassignedFromUserId = firstFromUserId(delegations,
                WorkflowTaskDelegationType.TRANSFER,
                WorkflowTaskDelegationType.DELEGATE);
        if (containsAssignee(assigneeIds, reassignedFromUserId)) {
            return reassignedFromUserId;
        }
        if (containsAssignee(assigneeIds, approvedTaskAssigneeId)) {
            return approvedTaskAssigneeId;
        }
        Long addSignFromUserId = firstFromUserId(delegations, WorkflowTaskDelegationType.ADD_SIGN);
        return containsAssignee(assigneeIds, addSignFromUserId) ? addSignFromUserId : approvedTaskAssigneeId;
    }

    private static boolean containsAssignee(List<Long> assigneeIds, Long assigneeId) {
        return assigneeId != null && assigneeIds.contains(assigneeId);
    }

    private static Long firstFromUserId(Collection<Delegation> delegations,
                                        WorkflowTaskDelegationType... delegationTypes) {
        if (delegations == null || delegations.isEmpty()) {
            return null;
        }
        List<WorkflowTaskDelegationType> targetTypes = List.of(delegationTypes);
        return delegations.stream()
                .filter(delegation -> delegation != null && targetTypes.contains(delegation.type()))
                .map(Delegation::fromUserId)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    public record Delegation(Long fromUserId, WorkflowTaskDelegationType type) {
    }
}
