package com.laker.admin.module.batch.enums;

public enum BatchTaskItemStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    SKIPPED,
    RETRYING;

    public String code() {
        return name();
    }
}
