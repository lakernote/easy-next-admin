package com.laker.admin.module.workflow.support;

import java.util.List;
import java.util.Optional;

/**
 * 审批节点流转策略。
 *
 * <p>这里不访问数据库，只表达“当前节点是否还能继续等待”的业务规则，避免加签、会签、
 * 顺序签等判断散落在运行时服务中。</p>
 */
public enum WorkflowTaskPolicy {
    ANY_ONE(WorkflowApproveType.ANY_ONE) {
        @Override
        protected boolean shouldWaitRegularTasks(long pendingRegularTaskCount) {
            return false;
        }
    },
    ALL(WorkflowApproveType.ALL) {
        @Override
        protected boolean shouldWaitRegularTasks(long pendingRegularTaskCount) {
            return pendingRegularTaskCount > 0;
        }
    },
    SEQUENTIAL(WorkflowApproveType.SEQUENTIAL) {
        @Override
        protected boolean shouldWaitRegularTasks(long pendingRegularTaskCount) {
            return false;
        }

        @Override
        public List<Long> initialAssignees(List<Long> assigneeIds) {
            return assigneeIds.isEmpty() ? List.of() : List.of(assigneeIds.get(0));
        }

        @Override
        public Optional<Long> nextAssigneeAfter(List<Long> assigneeIds, Long currentAssigneeId) {
            int currentIndex = assigneeIds.indexOf(currentAssigneeId);
            if (currentIndex >= 0 && currentIndex + 1 < assigneeIds.size()) {
                return Optional.of(assigneeIds.get(currentIndex + 1));
            }
            return Optional.empty();
        }
    };

    private final WorkflowApproveType approveType;

    WorkflowTaskPolicy(WorkflowApproveType approveType) {
        this.approveType = approveType;
    }

    public boolean shouldHoldCurrentNode(boolean approvedTaskFromAddSign,
                                         long pendingAddSignTaskCount,
                                         long pendingRegularTaskCount) {
        if (pendingAddSignTaskCount > 0) {
            return true;
        }
        if (approvedTaskFromAddSign && pendingRegularTaskCount > 0) {
            return true;
        }
        return shouldWaitRegularTasks(pendingRegularTaskCount);
    }

    public List<Long> initialAssignees(List<Long> assigneeIds) {
        return assigneeIds;
    }

    public Optional<Long> nextAssigneeAfter(List<Long> assigneeIds, Long currentAssigneeId) {
        return Optional.empty();
    }

    protected abstract boolean shouldWaitRegularTasks(long pendingRegularTaskCount);

    public static WorkflowTaskPolicy of(WorkflowApproveType approveType) {
        WorkflowApproveType normalizedApproveType = approveType == null ? WorkflowApproveType.ANY_ONE : approveType;
        for (WorkflowTaskPolicy policy : values()) {
            if (policy.approveType == normalizedApproveType) {
                return policy;
            }
        }
        throw new IllegalArgumentException("未配置审批节点流转策略：" + normalizedApproveType);
    }
}
