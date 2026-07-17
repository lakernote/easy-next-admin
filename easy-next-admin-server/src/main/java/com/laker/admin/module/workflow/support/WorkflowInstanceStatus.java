package com.laker.admin.module.workflow.support;

public enum WorkflowInstanceStatus {
    RUNNING,
    APPROVED,
    REJECTED,
    REVOKED,
    TERMINATED;

    public String code() {
        return name();
    }

    public boolean equalsCode(String status) {
        return code().equals(status);
    }
}
