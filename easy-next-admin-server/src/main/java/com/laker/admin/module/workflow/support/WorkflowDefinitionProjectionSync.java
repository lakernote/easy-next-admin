package com.laker.admin.module.workflow.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.laker.admin.infrastructure.json.EasyJsonCodec;
import com.laker.admin.module.workflow.entity.WfProcessDefinitionVersion;
import com.laker.admin.module.workflow.entity.WfProcessNode;
import com.laker.admin.module.workflow.entity.WfProcessTransition;
import com.laker.admin.module.workflow.service.IWfProcessNodeService;
import com.laker.admin.module.workflow.service.IWfProcessTransitionService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class WorkflowDefinitionProjectionSync {
    private static final String ALLOW_TRANSFER = "allowTransfer";
    private static final String ALLOW_DELEGATE = "allowDelegate";
    private static final String ALLOW_ADD_SIGN = "allowAddSign";
    private static final String ALLOW_REMOVE_SIGN = "allowRemoveSign";
    private static final String ALLOW_RETURN = "allowReturn";

    private final IWfProcessNodeService nodeService;
    private final IWfProcessTransitionService transitionService;
    private final WorkflowGraphParser graphParser;
    private final EasyJsonCodec jsonCodec;

    public WorkflowDefinitionProjectionSync(IWfProcessNodeService nodeService,
                                            IWfProcessTransitionService transitionService,
                                            WorkflowGraphParser graphParser,
                                            EasyJsonCodec jsonCodec) {
        this.nodeService = nodeService;
        this.transitionService = transitionService;
        this.graphParser = graphParser;
        this.jsonCodec = jsonCodec;
    }

    public void sync(WfProcessDefinitionVersion version, String graphJson) {
        if (version == null || version.getId() == null) {
            return;
        }
        removeByVersionId(version.getId());
        if (!StringUtils.hasText(graphJson)) {
            return;
        }
        WorkflowGraph graph = graphParser.parse(graphJson);
        List<WfProcessNode> nodes = new ArrayList<>();
        int nodeIndex = 0;
        for (WorkflowGraph.NodeInfo nodeInfo : graph.nodes().values()) {
            nodes.add(toProcessNode(version.getId(), nodeInfo, nodeIndex++));
        }
        if (!nodes.isEmpty()) {
            nodeService.saveBatch(nodes);
        }
        List<WfProcessTransition> transitions = new ArrayList<>();
        graph.outgoing().forEach((sourceKey, edges) -> {
            for (int priority = 0; priority < edges.size(); priority++) {
                transitions.add(toProcessTransition(version.getId(), sourceKey, edges.get(priority), priority));
            }
        });
        if (!transitions.isEmpty()) {
            transitionService.saveBatch(transitions);
        }
    }

    public void removeByVersionId(Long versionId) {
        if (versionId == null) {
            return;
        }
        nodeService.lambdaUpdate()
                .eq(WfProcessNode::getVersionId, versionId)
                .remove();
        transitionService.lambdaUpdate()
                .eq(WfProcessTransition::getVersionId, versionId)
                .remove();
    }

    public void removeByVersionIds(List<Long> versionIds) {
        if (versionIds == null || versionIds.isEmpty()) {
            return;
        }
        nodeService.lambdaUpdate()
                .in(WfProcessNode::getVersionId, versionIds)
                .remove();
        transitionService.lambdaUpdate()
                .in(WfProcessTransition::getVersionId, versionIds)
                .remove();
    }

    private WfProcessNode toProcessNode(Long versionId, WorkflowGraph.NodeInfo nodeInfo, int sortOrder) {
        WfProcessNode node = new WfProcessNode();
        node.setVersionId(versionId);
        node.setNodeKey(nodeInfo.key());
        node.setNodeName(nodeInfo.name());
        node.setNodeType(nodeInfo.kind().name());
        node.setApproveType(text(nodeInfo, WorkflowGraphProperty.APPROVE_TYPE));
        node.setApproverType(text(nodeInfo, WorkflowGraphProperty.APPROVER_TYPE));
        node.setApproverValue(approverValue(nodeInfo));
        node.setAllowTransfer(booleanProperty(nodeInfo.raw(), ALLOW_TRANSFER, true));
        node.setAllowDelegate(booleanProperty(nodeInfo.raw(), ALLOW_DELEGATE, true));
        node.setAllowAddSign(booleanProperty(nodeInfo.raw(), ALLOW_ADD_SIGN, true));
        node.setAllowRemoveSign(booleanProperty(nodeInfo.raw(), ALLOW_REMOVE_SIGN, false));
        node.setAllowReturn(booleanProperty(nodeInfo.raw(), ALLOW_RETURN, true));
        node.setSortOrder(sortOrder);
        return node;
    }

    private WfProcessTransition toProcessTransition(Long versionId, String sourceKey, WorkflowGraph.EdgeInfo edgeInfo, int priority) {
        WfProcessTransition transition = new WfProcessTransition();
        transition.setVersionId(versionId);
        transition.setFromNodeKey(sourceKey);
        transition.setToNodeKey(edgeInfo.targetKey());
        transition.setConditionType(edgeInfo.conditionType().name());
        transition.setConditionJson(conditionJson(edgeInfo));
        transition.setPriority(priority);
        return transition;
    }

    private String approverValue(WorkflowGraph.NodeInfo nodeInfo) {
        String approverType = text(nodeInfo, WorkflowGraphProperty.APPROVER_TYPE);
        if ("USER".equals(approverType)) {
            return jsonProperty(nodeInfo, WorkflowGraphProperty.ASSIGNEE_IDS);
        }
        if ("ROLE".equals(approverType)) {
            return text(nodeInfo, WorkflowGraphProperty.ROLE_CODE);
        }
        if (nodeInfo.kind() == WorkflowNodeKind.CC) {
            return jsonProperty(nodeInfo, WorkflowGraphProperty.CC_USER_IDS);
        }
        return "";
    }

    private String conditionJson(WorkflowGraph.EdgeInfo edgeInfo) {
        if (!StringUtils.hasText(edgeInfo.conditionExpression()) && !StringUtils.hasText(edgeInfo.label())) {
            return null;
        }
        Map<String, String> condition = new LinkedHashMap<>();
        if (StringUtils.hasText(edgeInfo.conditionExpression())) {
            condition.put("expression", edgeInfo.conditionExpression());
        }
        if (StringUtils.hasText(edgeInfo.label())) {
            condition.put("label", edgeInfo.label());
        }
        return jsonCodec.toJson(condition);
    }

    private String text(WorkflowGraph.NodeInfo nodeInfo, WorkflowGraphProperty property) {
        String value = nodeInfo.propertyText(property);
        return StringUtils.hasText(value) ? value : null;
    }

    private String jsonProperty(WorkflowGraph.NodeInfo nodeInfo, WorkflowGraphProperty property) {
        JsonNode value = nodeInfo.property(property);
        return value == null || value.isNull() ? "" : value.toString();
    }

    private Boolean booleanProperty(JsonNode node, String key, boolean defaultValue) {
        JsonNode properties = WorkflowGraphJsonKey.PROPERTIES.child(node);
        JsonNode value = properties == null ? null : properties.get(key);
        return value == null || value.isNull() ? defaultValue : value.asBoolean(defaultValue);
    }
}
