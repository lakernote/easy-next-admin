package com.laker.admin.module.workflow.support;

import org.springframework.util.StringUtils;

public enum WorkflowApproverType {
    USER,
    ROLE,
    INITIATOR,
    INITIATOR_SELECTED,
    MANAGER,
    DEPT_LEADER,
    UPPER_DEPT_LEADER,
    UNKNOWN;

    public boolean acceptsPreferredAssignee() {
        return this == INITIATOR_SELECTED;
    }

    public boolean isConfiguredRule() {
        return this != UNKNOWN;
    }

    public String unresolvedMessage() {
        return switch (this) {
            case USER -> "指定审批人不存在或已停用";
            case ROLE -> "请为审批节点配置有效角色及角色成员";
            case INITIATOR -> "未获取到流程发起人";
            case INITIATOR_SELECTED -> "发起人自选审批节点需要提交时指定处理人";
            case MANAGER -> "未找到发起人直属上级";
            case DEPT_LEADER -> "未找到发起人部门负责人";
            case UPPER_DEPT_LEADER -> "未找到发起人上级部门负责人";
            case UNKNOWN -> "未配置审批人";
        };
    }

    public static WorkflowApproverType of(String value) {
        String code = normalize(value);
        if (!StringUtils.hasText(code)) {
            return UNKNOWN;
        }
        for (WorkflowApproverType type : values()) {
            if (type.name().equals(code)) {
                return type;
            }
        }
        return UNKNOWN;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
