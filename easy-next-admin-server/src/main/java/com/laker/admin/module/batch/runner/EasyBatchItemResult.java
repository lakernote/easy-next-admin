package com.laker.admin.module.batch.runner;

import org.springframework.util.StringUtils;

public record EasyBatchItemResult(boolean success, String message) {

    public EasyBatchItemResult {
        message = StringUtils.hasText(message) ? message.trim() : null;
    }

    public static EasyBatchItemResult success(String message) {
        return new EasyBatchItemResult(true, message);
    }

    public static EasyBatchItemResult failed(String message) {
        return new EasyBatchItemResult(false, message);
    }
}
