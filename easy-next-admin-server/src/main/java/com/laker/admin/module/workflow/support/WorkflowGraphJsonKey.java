package com.laker.admin.module.workflow.support;

import com.fasterxml.jackson.databind.JsonNode;

public enum WorkflowGraphJsonKey {
    NODES("nodes"),
    EDGES("edges"),
    ID("id"),
    TYPE("type"),
    TEXT("text"),
    VALUE("value"),
    PROPERTIES("properties"),
    SOURCE_NODE_ID("sourceNodeId"),
    TARGET_NODE_ID("targetNodeId");

    private final String key;

    WorkflowGraphJsonKey(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public JsonNode child(JsonNode node) {
        return node == null ? null : node.get(key);
    }

    public JsonNode path(JsonNode node) {
        return node == null ? null : node.path(key);
    }

    public String text(JsonNode node) {
        JsonNode value = child(node);
        return value != null && !value.isNull() ? value.asText() : null;
    }
}
