package com.laker.admin.module.workflow.support;

import com.laker.admin.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class WorkflowGraphNavigator {
    private final WorkflowConditionEvaluator conditionEvaluator;

    public WorkflowGraph.NextStep resolveNextStep(WorkflowGraph graph, String fromNodeKey, Map<String, Object> variables) {
        if (graph.nodes().isEmpty()) {
            return new WorkflowGraph.NextStep(null, List.of());
        }
        if (!StringUtils.hasText(fromNodeKey) && !StringUtils.hasText(graph.startKey())) {
            return graph.nodes().values().stream()
                    .filter(node -> node.kind().isApproval())
                    .findFirst()
                    .map(node -> new WorkflowGraph.NextStep(node, List.<WorkflowGraph.NodeInfo>of()))
                    .orElseGet(() -> new WorkflowGraph.NextStep(null, List.of()));
        }
        String cursor = StringUtils.hasText(fromNodeKey) ? fromNodeKey : graph.startKey();
        Set<String> visited = new HashSet<>();
        List<WorkflowGraph.NodeInfo> ccNodes = new ArrayList<>();
        while (StringUtils.hasText(cursor)) {
            List<WorkflowGraph.EdgeInfo> nextEdges = graph.outgoing().getOrDefault(cursor, List.of());
            if (nextEdges.isEmpty()) {
                return new WorkflowGraph.NextStep(null, ccNodes);
            }
            WorkflowGraph.EdgeInfo nextEdge = selectNextEdge(nextEdges, variables);
            if (nextEdge == null) {
                throw new BusinessException("流程分支条件未命中，请检查连线条件或设置默认路径");
            }
            String nextKey = nextEdge.targetKey();
            if (!visited.add(nextKey)) {
                throw new BusinessException("流程图存在循环，无法自动流转");
            }
            WorkflowGraph.NodeInfo node = graph.nodes().get(nextKey);
            if (node == null || node.kind().isEnd()) {
                return new WorkflowGraph.NextStep(null, ccNodes);
            }
            if (node.kind().isCc()) {
                ccNodes.add(node);
                cursor = node.key();
                continue;
            }
            if (node.kind().isSkippable()) {
                cursor = node.key();
                continue;
            }
            return new WorkflowGraph.NextStep(node, ccNodes);
        }
        return new WorkflowGraph.NextStep(null, ccNodes);
    }

    public String defaultReturnNodeKey(WorkflowGraph graph) {
        return graph.nodes().values().stream()
                .filter(node -> node.kind() == WorkflowNodeKind.SUBMIT)
                .findFirst()
                .map(WorkflowGraph.NodeInfo::key)
                .orElseThrow(() -> new BusinessException("流程图未找到可退回的提交节点"));
    }

    public String resolveReturnNodeKey(WorkflowGraph graph, String currentNodeKey, String requestedReturnNodeKey) {
        if (!StringUtils.hasText(requestedReturnNodeKey)) {
            return defaultReturnNodeKey(graph);
        }
        String normalizedReturnNodeKey = requestedReturnNodeKey.trim();
        if (!"previous".equalsIgnoreCase(normalizedReturnNodeKey)) {
            return normalizedReturnNodeKey;
        }
        return previousReturnNodeKey(graph, currentNodeKey)
                .orElseGet(() -> defaultReturnNodeKey(graph));
    }

    public boolean canReturnTo(WorkflowGraph.NodeInfo node) {
        return node != null && (node.kind() == WorkflowNodeKind.SUBMIT || node.kind().isApproval());
    }

    private Optional<String> previousReturnNodeKey(WorkflowGraph graph, String currentNodeKey) {
        if (!StringUtils.hasText(currentNodeKey)) {
            return Optional.empty();
        }
        Map<String, List<String>> incoming = incomingEdges(graph);
        return previousReturnNodeKey(graph, incoming, currentNodeKey, new HashSet<>());
    }

    private Optional<String> previousReturnNodeKey(WorkflowGraph graph,
                                                  Map<String, List<String>> incoming,
                                                  String currentNodeKey,
                                                  Set<String> visited) {
        if (!visited.add(currentNodeKey)) {
            throw new BusinessException("流程图存在循环，无法退回上一节点");
        }
        List<String> sourceNodeKeys = incoming.getOrDefault(currentNodeKey, List.of());
        for (String sourceNodeKey : sourceNodeKeys) {
            WorkflowGraph.NodeInfo sourceNode = graph.nodes().get(sourceNodeKey);
            if (canReturnTo(sourceNode)) {
                return Optional.of(sourceNode.key());
            }
        }
        for (String sourceNodeKey : sourceNodeKeys) {
            Optional<String> previous = previousReturnNodeKey(graph, incoming, sourceNodeKey, new HashSet<>(visited));
            if (previous.isPresent()) {
                return previous;
            }
        }
        return Optional.empty();
    }

    private Map<String, List<String>> incomingEdges(WorkflowGraph graph) {
        Map<String, List<String>> incoming = new LinkedHashMap<>();
        graph.outgoing().forEach((sourceNodeKey, edges) -> edges.forEach(edge ->
                incoming.computeIfAbsent(edge.targetKey(), ignored -> new ArrayList<>()).add(sourceNodeKey)));
        return incoming;
    }

    private WorkflowGraph.EdgeInfo selectNextEdge(List<WorkflowGraph.EdgeInfo> edges, Map<String, Object> variables) {
        if (edges.size() == 1) {
            WorkflowGraph.EdgeInfo onlyEdge = edges.get(0);
            return !isConditionalEdge(onlyEdge)
                    || conditionEvaluator.matches(onlyEdge.conditionType(), onlyEdge.conditionExpression(), variables)
                    ? onlyEdge
                    : null;
        }
        for (WorkflowGraph.EdgeInfo edge : edges) {
            if (isConditionalEdge(edge)
                    && conditionEvaluator.matches(edge.conditionType(), edge.conditionExpression(), variables)) {
                return edge;
            }
        }
        return edges.stream()
                .filter(edge -> !isConditionalEdge(edge))
                .findFirst()
                .orElse(null);
    }

    private boolean isConditionalEdge(WorkflowGraph.EdgeInfo edge) {
        return edge.conditionType().isConditional();
    }
}
