package com.laker.admin.module.workflow.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.infrastructure.json.EasyJsonCodec;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class WorkflowGraphParser {
    private final EasyJsonCodec jsonCodec;

    public WorkflowGraphParser(EasyJsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
    }

    public WorkflowGraph parse(String graphJson) {
        if (!StringUtils.hasText(graphJson)) {
            return emptyGraph();
        }
        JsonNode root = jsonCodec.readTree(graphJson);
        Map<String, WorkflowGraph.NodeInfo> nodes = new LinkedHashMap<>();
        String startKey = null;
        for (JsonNode node : WorkflowGraphJsonKey.NODES.path(root)) {
            String key = WorkflowGraphJsonKey.ID.text(node);
            if (!StringUtils.hasText(key)) {
                continue;
            }
            String name = nodeName(node, key);
            WorkflowNodeShape shape = WorkflowNodeShape.of(WorkflowGraphJsonKey.TYPE.text(node));
            WorkflowNodeKind kind = resolveNodeKind(node, key);
            WorkflowGraph.NodeInfo nodeInfo = new WorkflowGraph.NodeInfo(key, name, shape, kind, node);
            nodes.put(key, nodeInfo);
            if (startKey == null && nodeInfo.kind().isStart()) {
                startKey = key;
            }
        }
        Map<String, List<WorkflowGraph.EdgeInfo>> outgoing = new LinkedHashMap<>();
        for (JsonNode edge : WorkflowGraphJsonKey.EDGES.path(root)) {
            String source = WorkflowGraphJsonKey.SOURCE_NODE_ID.text(edge);
            String target = WorkflowGraphJsonKey.TARGET_NODE_ID.text(edge);
            if (StringUtils.hasText(source) && StringUtils.hasText(target)) {
                WorkflowGraph.EdgeInfo edgeInfo = new WorkflowGraph.EdgeInfo(
                        target,
                        WorkflowConditionType.of(graphPropertyText(edge, WorkflowGraphProperty.CONDITION_TYPE)),
                        graphPropertyText(edge, WorkflowGraphProperty.CONDITION_EXPRESSION),
                        edgeName(edge),
                        edge);
                outgoing.computeIfAbsent(source, ignored -> new ArrayList<>()).add(edgeInfo);
            }
        }
        return new WorkflowGraph(unmodifiableMap(nodes), unmodifiableOutgoing(outgoing), startKey);
    }

    private WorkflowGraph emptyGraph() {
        return new WorkflowGraph(Map.of(), Map.of(), null);
    }

    private Map<String, WorkflowGraph.NodeInfo> unmodifiableMap(Map<String, WorkflowGraph.NodeInfo> nodes) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
    }

    private Map<String, List<WorkflowGraph.EdgeInfo>> unmodifiableOutgoing(Map<String, List<WorkflowGraph.EdgeInfo>> outgoing) {
        Map<String, List<WorkflowGraph.EdgeInfo>> copy = new LinkedHashMap<>();
        outgoing.forEach((source, edges) -> copy.put(source, List.copyOf(edges)));
        return Collections.unmodifiableMap(copy);
    }

    private String nodeName(JsonNode node, String fallback) {
        JsonNode text = WorkflowGraphJsonKey.TEXT.child(node);
        if (text == null || text.isNull()) {
            return fallback;
        }
        if (text.isTextual()) {
            return text.asText();
        }
        if (text.isObject()) {
            String value = WorkflowGraphJsonKey.VALUE.text(text);
            return StringUtils.hasText(value) ? value : fallback;
        }
        return fallback;
    }

    private String edgeName(JsonNode edge) {
        JsonNode text = WorkflowGraphJsonKey.TEXT.child(edge);
        if (text == null || text.isNull()) {
            return "";
        }
        if (text.isTextual()) {
            return text.asText();
        }
        if (text.isObject()) {
            String value = WorkflowGraphJsonKey.VALUE.text(text);
            return StringUtils.hasText(value) ? value : "";
        }
        return "";
    }

    private String graphPropertyText(JsonNode node, WorkflowGraphProperty property) {
        return property.text(WorkflowGraphJsonKey.PROPERTIES.child(node));
    }

    private WorkflowNodeKind resolveNodeKind(JsonNode node, String key) {
        WorkflowNodeKind kind = WorkflowNodeKind.of(graphPropertyText(node, WorkflowGraphProperty.NODE_TYPE));
        if (kind == WorkflowNodeKind.UNKNOWN) {
            throw new BusinessException("流程节点 " + key + " 缺少或不支持 nodeType");
        }
        return kind;
    }
}
