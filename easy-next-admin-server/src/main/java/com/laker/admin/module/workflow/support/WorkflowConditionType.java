package com.laker.admin.module.workflow.support;

import org.springframework.util.StringUtils;

public enum WorkflowConditionType {
    ALWAYS,
    EXPRESSION,
    UNKNOWN;

    public static WorkflowConditionType of(String conditionType) {
        String code = normalize(conditionType);
        if (!StringUtils.hasText(code)) {
            return ALWAYS;
        }
        for (WorkflowConditionType type : values()) {
            if (type.name().equals(code)) {
                return type;
            }
        }
        return UNKNOWN;
    }

    public boolean isConditional() {
        return this == EXPRESSION;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
