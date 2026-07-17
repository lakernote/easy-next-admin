package com.laker.admin.module.batch.enums;

public enum BatchTaskItemStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    SKIPPED;

    public String code() {
        return name();
    }

    public static BatchTaskItemStatus of(String value) {
        for (BatchTaskItemStatus status : values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        return PENDING;
    }
}
