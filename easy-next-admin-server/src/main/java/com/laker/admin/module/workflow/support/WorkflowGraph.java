package com.laker.admin.module.workflow.support;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public record WorkflowGraph(Map<String, NodeInfo> nodes,
                            Map<String, List<EdgeInfo>> outgoing,
                            String startKey) {

    public record NodeInfo(String key,
                           String name,
                           WorkflowNodeShape shape,
                           WorkflowNodeKind kind,
                           JsonNode raw) {

        public JsonNode property(WorkflowGraphProperty property) {
            JsonNode properties = WorkflowGraphJsonKey.PROPERTIES.child(raw);
            return property.child(properties);
        }

        public String propertyText(WorkflowGraphProperty property) {
            JsonNode properties = WorkflowGraphJsonKey.PROPERTIES.child(raw);
            return property.text(properties);
        }
    }

    public record EdgeInfo(String targetKey,
                           WorkflowConditionType conditionType,
                           String conditionExpression,
                           String label,
                           JsonNode raw) {
    }

    public record NextStep(NodeInfo approvalNode, List<NodeInfo> ccNodes) {
    }
}
