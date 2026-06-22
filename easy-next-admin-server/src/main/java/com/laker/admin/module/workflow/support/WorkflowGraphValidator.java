package com.laker.admin.module.workflow.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.laker.admin.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class WorkflowGraphValidator {
    private final WorkflowGraphParser graphParser;
    private final WorkflowExpressionEvaluator expressionEvaluator;

    public WorkflowGraphValidator(WorkflowGraphParser graphParser,
                                  WorkflowExpressionEvaluator expressionEvaluator) {
        this.graphParser = graphParser;
        this.expressionEvaluator = expressionEvaluator;
    }

    public void validateForEnable(String graphJson) {
        WorkflowGraph graph = graphParser.parse(graphJson);
        if (graph.nodes().isEmpty()) {
            throw new BusinessException("启用流程前请先配置流程图");
        }
        validateNodeShape(graph);
        validateEdges(graph);
        validateAcyclic(graph);
        validateBranchEdges(graph);
        validateReachableEnd(graph);
    }

    private void validateNodeShape(WorkflowGraph graph) {
        long startCount = graph.nodes().values().stream().filter(node -> node.kind().isStart()).count();
        if (startCount == 0) {
            throw new BusinessException("启用流程需要开始节点");
        }
        if (startCount > 1) {
            throw new BusinessException("启用流程只能有一个开始节点");
        }
        long endCount = graph.nodes().values().stream().filter(node -> node.kind().isEnd()).count();
        if (endCount == 0) {
            throw new BusinessException("启用流程需要结束节点");
        }
        if (endCount > 1) {
            throw new BusinessException("启用流程只能有一个结束节点");
        }
        List<WorkflowGraph.NodeInfo> approvalNodes = graph.nodes().values().stream()
                .filter(node -> node.kind().isApproval())
                .toList();
        if (approvalNodes.isEmpty()) {
            throw new BusinessException("启用流程至少需要一个审批节点");
        }
        approvalNodes.forEach(this::validateApprovalNode);
    }

    private void validateApprovalNode(WorkflowGraph.NodeInfo node) {
        try {
            WorkflowApproveType.of(node.propertyText(WorkflowGraphProperty.APPROVE_TYPE));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ex.getMessage());
        }
        WorkflowAssigneeRule rule = WorkflowAssigneeRule.of(
                node.propertyText(WorkflowGraphProperty.APPROVER_TYPE),
                nodeUserIds(node, WorkflowGraphProperty.ASSIGNEE_IDS),
                node.propertyText(WorkflowGraphProperty.ROLE_CODE));
        boolean valid = switch (rule.approverType()) {
            case USER -> !rule.assigneeIds().isEmpty();
            case ROLE -> StringUtils.hasText(rule.roleCode());
            case INITIATOR, INITIATOR_SELECTED, MANAGER, DEPT_LEADER, UPPER_DEPT_LEADER -> true;
            case UNKNOWN -> false;
        };
        if (!valid) {
            throw new BusinessException(node.name() + " 未配置有效处理人规则");
        }
    }

    private void validateEdges(WorkflowGraph graph) {
        Set<String> nodeKeys = graph.nodes().keySet();
        if (graph.outgoing().values().stream().mapToInt(List::size).sum() == 0) {
            throw new BusinessException("启用流程需要配置节点连线");
        }
        for (Map.Entry<String, List<WorkflowGraph.EdgeInfo>> entry : graph.outgoing().entrySet()) {
            String sourceKey = entry.getKey();
            if (!nodeKeys.contains(sourceKey)) {
                throw new BusinessException("启用流程存在连接到不存在节点的连线");
            }
            for (WorkflowGraph.EdgeInfo edge : entry.getValue()) {
                if (!nodeKeys.contains(edge.targetKey())) {
                    throw new BusinessException("启用流程存在连接到不存在节点的连线");
                }
                if (edge.conditionType() == WorkflowConditionType.UNKNOWN) {
                    throw new BusinessException("启用流程存在不支持的连线条件类型");
                }
                if (edge.conditionType().isConditional() && !StringUtils.hasText(edge.conditionExpression())) {
                    throw new BusinessException("启用流程存在未配置表达式的条件连线");
                }
                if (edge.conditionType().isConditional()) {
                    validateConditionExpression(edge.conditionExpression());
                }
            }
        }
    }

    private void validateConditionExpression(String expression) {
        try {
            expressionEvaluator.validate(expression);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ex.getMessage());
        }
    }

    private void validateBranchEdges(WorkflowGraph graph) {
        for (Map.Entry<String, List<WorkflowGraph.EdgeInfo>> entry : graph.outgoing().entrySet()) {
            List<WorkflowGraph.EdgeInfo> edges = entry.getValue();
            if (edges.size() <= 1) {
                continue;
            }
            WorkflowGraph.NodeInfo sourceNode = graph.nodes().get(entry.getKey());
            String sourceName = sourceNode == null ? entry.getKey() : sourceNode.name();
            long conditionalCount = edges.stream()
                    .filter(edge -> edge.conditionType().isConditional())
                    .count();
            long defaultCount = edges.stream()
                    .filter(edge -> edge.conditionType() == WorkflowConditionType.ALWAYS)
                    .count();
            if (conditionalCount == 0) {
                throw new BusinessException(sourceName + " 存在多条默认路径，请配置条件分支");
            }
            if (defaultCount == 0) {
                throw new BusinessException(sourceName + " 条件分支需要配置默认路径");
            }
            if (defaultCount > 1) {
                throw new BusinessException(sourceName + " 条件分支只能配置一条默认路径");
            }
        }
    }

    private void validateAcyclic(WorkflowGraph graph) {
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        for (String nodeKey : graph.nodes().keySet()) {
            if (hasCycle(graph, nodeKey, visiting, visited)) {
                throw new BusinessException("流程图存在循环，请调整节点连线");
            }
        }
    }

    private boolean hasCycle(WorkflowGraph graph, String nodeKey, Set<String> visiting, Set<String> visited) {
        if (visiting.contains(nodeKey)) {
            return true;
        }
        if (!visited.add(nodeKey)) {
            return false;
        }
        visiting.add(nodeKey);
        for (WorkflowGraph.EdgeInfo edge : graph.outgoing().getOrDefault(nodeKey, List.of())) {
            if (graph.nodes().containsKey(edge.targetKey()) && hasCycle(graph, edge.targetKey(), visiting, visited)) {
                return true;
            }
        }
        visiting.remove(nodeKey);
        return false;
    }

    private void validateReachableEnd(WorkflowGraph graph) {
        String startKey = graph.startKey();
        String endKey = graph.nodes().values().stream()
                .filter(node -> node.kind().isEnd())
                .map(WorkflowGraph.NodeInfo::key)
                .findFirst()
                .orElse(null);
        if (!StringUtils.hasText(startKey) || !StringUtils.hasText(endKey)) {
            throw new BusinessException("启用流程需要开始和结束节点");
        }
        if (!canReach(graph, startKey, endKey)) {
            throw new BusinessException("启用流程需要从开始节点连通到结束节点");
        }
    }

    private boolean canReach(WorkflowGraph graph, String startKey, String endKey) {
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        pending.add(startKey);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            if (Objects.equals(current, endKey)) {
                return true;
            }
            graph.outgoing().getOrDefault(current, List.of()).stream()
                    .map(WorkflowGraph.EdgeInfo::targetKey)
                    .filter(StringUtils::hasText)
                    .forEach(pending::addLast);
        }
        return false;
    }

    private List<Long> nodeUserIds(WorkflowGraph.NodeInfo node, WorkflowGraphProperty property) {
        List<Long> ids = new ArrayList<>();
        appendLongs(ids, node.property(property));
        return ids;
    }

    private void appendLongs(List<Long> target, JsonNode valueNode) {
        if (valueNode == null || valueNode.isNull()) {
            return;
        }
        if (valueNode.isArray()) {
            valueNode.forEach(item -> appendLongs(target, item));
            return;
        }
        if (valueNode.isNumber()) {
            target.add(valueNode.asLong());
            return;
        }
        if (valueNode.isTextual() && StringUtils.hasText(valueNode.asText())) {
            try {
                target.add(Long.valueOf(valueNode.asText().trim()));
            } catch (NumberFormatException ignored) {
                // 节点人员配置允许字符串扩展，无法转成用户ID时按无效用户处理。
            }
        }
    }
}
