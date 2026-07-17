package com.laker.admin.module.batch.enums;

import java.util.Set;

public enum BatchTaskStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILED,
    CANCELING,
    CANCELED;

    private static final Set<BatchTaskStatus> TERMINAL = Set.of(SUCCESS, PARTIAL_SUCCESS, FAILED, CANCELED);

    public String code() {
        return name();
    }

    public boolean terminal() {
        return TERMINAL.contains(this);
    }

    public static BatchTaskStatus of(String value) {
        for (BatchTaskStatus status : values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        return PENDING;
    }
}
