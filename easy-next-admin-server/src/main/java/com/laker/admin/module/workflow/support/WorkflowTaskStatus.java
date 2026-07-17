package com.laker.admin.module.workflow.support;

public enum WorkflowTaskStatus {
    PENDING,
    APPROVED,
    REJECTED,
    TRANSFERRED,
    DELEGATED,
    CANCELED;

    public String code() {
        return name();
    }

    public boolean equalsCode(String status) {
        return code().equals(status);
    }
}
