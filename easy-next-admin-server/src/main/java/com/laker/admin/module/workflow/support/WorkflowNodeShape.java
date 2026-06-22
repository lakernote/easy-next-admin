package com.laker.admin.module.workflow.support;

public enum WorkflowNodeShape {
    CIRCLE,
    RECT,
    DIAMOND,
    UNKNOWN;

    public static WorkflowNodeShape of(String value) {
        String code = value == null ? "" : value.trim().toUpperCase();
        if (code.isEmpty()) {
            return UNKNOWN;
        }
        try {
            return WorkflowNodeShape.valueOf(code);
        } catch (IllegalArgumentException ignored) {
            return UNKNOWN;
        }
    }
}
