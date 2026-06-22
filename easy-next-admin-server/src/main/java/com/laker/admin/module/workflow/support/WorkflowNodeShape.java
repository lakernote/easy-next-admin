package com.laker.admin.module.workflow.support;

import java.util.Arrays;

public enum WorkflowNodeShape {
    CIRCLE("CIRCLE"),
    RECT("RECT", "RECTANGLE"),
    DIAMOND("DIAMOND"),
    UNKNOWN;

    private final String[] aliases;

    WorkflowNodeShape(String... aliases) {
        this.aliases = aliases;
    }

    public static WorkflowNodeShape of(String value) {
        String code = value == null ? "" : value.trim().toUpperCase();
        for (WorkflowNodeShape shape : values()) {
            if (Arrays.asList(shape.aliases).contains(code)) {
                return shape;
            }
        }
        return UNKNOWN;
    }
}
