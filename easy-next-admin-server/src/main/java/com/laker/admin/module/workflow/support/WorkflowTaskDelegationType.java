package com.laker.admin.module.workflow.support;

import org.springframework.util.StringUtils;

public enum WorkflowTaskDelegationType {
    ADD_SIGN("ADD_SIGN"),
    TRANSFER("TRANSFER"),
    DELEGATE("DELEGATE");

    private final String code;

    WorkflowTaskDelegationType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static WorkflowTaskDelegationType of(String value) {
        String normalizedValue = value == null ? "" : value.trim().toUpperCase();
        if (!StringUtils.hasText(normalizedValue)) {
            throw new IllegalArgumentException("任务委派类型不能为空");
        }
        for (WorkflowTaskDelegationType type : values()) {
            if (type.code.equals(normalizedValue)) {
                return type;
            }
        }
        throw new IllegalArgumentException("不支持的任务委派类型：" + value);
    }
}
