package com.laker.admin.module.workflow.support;

import com.fasterxml.jackson.databind.JsonNode;

public enum WorkflowGraphProperty {
    NODE_TYPE("nodeType"),
    APPROVE_TYPE("approveType"),
    APPROVER_TYPE("approverType"),
    ROLE_CODE("roleCode"),
    ASSIGNEE_IDS("assigneeIds"),
    CC_USER_IDS("ccUserIds"),
    ALLOW_TRANSFER("allowTransfer"),
    ALLOW_DELEGATE("allowDelegate"),
    ALLOW_ADD_SIGN("allowAddSign"),
    ALLOW_REMOVE_SIGN("allowRemoveSign"),
    ALLOW_RETURN("allowReturn"),
    CONDITION_TYPE("conditionType"),
    CONDITION_EXPRESSION("conditionExpression");

    private final String key;

    WorkflowGraphProperty(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public JsonNode child(JsonNode properties) {
        return properties == null ? null : properties.get(key);
    }

    public String text(JsonNode properties) {
        JsonNode value = child(properties);
        return value != null && !value.isNull() ? value.asText() : "";
    }
}
