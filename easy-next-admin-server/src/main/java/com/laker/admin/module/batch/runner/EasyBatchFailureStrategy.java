package com.laker.admin.module.batch.runner;

public enum EasyBatchFailureStrategy {
    CONTINUE_ON_ERROR,
    STOP_ON_ERROR,
    STOP_ON_FAILURE_LIMIT;

    public static EasyBatchFailureStrategy normalize(EasyBatchFailureStrategy strategy) {
        return strategy == null ? CONTINUE_ON_ERROR : strategy;
    }
}
