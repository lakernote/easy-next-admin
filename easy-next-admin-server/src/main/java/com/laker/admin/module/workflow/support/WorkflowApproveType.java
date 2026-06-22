package com.laker.admin.module.workflow.support;

import org.springframework.util.StringUtils;

public enum WorkflowApproveType {
    ANY_ONE,
    ALL,
    SEQUENTIAL;

    public static WorkflowApproveType of(String value) {
        String code = value == null ? "" : value.trim().toUpperCase();
        if (!StringUtils.hasText(code)) {
            return ANY_ONE;
        }
        for (WorkflowApproveType type : values()) {
            if (type.name().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("不支持的审批方式：" + value);
    }
}
