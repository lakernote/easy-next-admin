package com.laker.admin.module.workflow.support;

import org.springframework.util.StringUtils;

public enum WorkflowNodeKind {
    START,
    SUBMIT,
    CONDITION,
    APPROVAL,
    CC,
    END,
    UNKNOWN;

    public boolean isStart() {
        return this == START;
    }

    public boolean isEnd() {
        return this == END;
    }

    public boolean isCc() {
        return this == CC;
    }

    public boolean isSkippable() {
        return this == START || this == SUBMIT || this == CONDITION;
    }

    public boolean isApproval() {
        return this == APPROVAL;
    }

    public static WorkflowNodeKind of(String value) {
        String code = normalize(value);
        if (!StringUtils.hasText(code)) {
            return UNKNOWN;
        }
        for (WorkflowNodeKind kind : values()) {
            if (kind.name().equals(code)) {
                return kind;
            }
        }
        return UNKNOWN;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
