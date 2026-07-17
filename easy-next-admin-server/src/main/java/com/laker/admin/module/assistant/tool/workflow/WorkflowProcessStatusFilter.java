package com.laker.admin.module.assistant.tool.workflow;

import com.laker.admin.module.workflow.support.WorkflowInstanceStatus;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** 流程查询 Tool 的标准状态筛选口径。 */
public enum WorkflowProcessStatusFilter {
    RUNNING,
    COMPLETED,
    APPROVED,
    REJECTED,
    REVOKED,
    TERMINATED;

    private static final List<String> COMPLETED_STATUSES = List.of(
            WorkflowInstanceStatus.APPROVED.code(),
            WorkflowInstanceStatus.REJECTED.code(),
            WorkflowInstanceStatus.REVOKED.code(),
            WorkflowInstanceStatus.TERMINATED.code()
    );

    public static Optional<WorkflowProcessStatusFilter> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(code.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public List<String> instanceStatuses() {
        return this == COMPLETED ? COMPLETED_STATUSES : List.of(name());
    }
}
