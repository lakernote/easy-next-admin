package com.laker.admin.module.workflow.purchase.support;

import com.laker.admin.module.workflow.support.WorkflowInstanceStatus;

/** 采购业务状态与流程实例状态之间的唯一映射。 */
public final class PurchaseStatusPolicy {
    private PurchaseStatusPolicy() {
    }

    public static String fromWorkflow(String workflowStatus) {
        if (WorkflowInstanceStatus.RUNNING.equalsCode(workflowStatus)) {
            return "APPROVING";
        }
        if (WorkflowInstanceStatus.APPROVED.equalsCode(workflowStatus)) {
            return "APPROVED";
        }
        if (WorkflowInstanceStatus.REJECTED.equalsCode(workflowStatus)) {
            return "REJECTED";
        }
        if (WorkflowInstanceStatus.REVOKED.equalsCode(workflowStatus)) {
            return "REVOKED";
        }
        if (WorkflowInstanceStatus.TERMINATED.equalsCode(workflowStatus)) {
            return "TERMINATED";
        }
        return workflowStatus;
    }
}
