package com.laker.admin.module.workflow.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.laker.admin.module.workflow.entity.WfCc;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.service.IWfCcService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class WorkflowCcRecordService {
    private final IWfCcService ccService;
    private final WorkflowNotificationService notificationService;

    public List<Long> createRecords(WfProcessInstance instance,
                                    String nodeKey,
                                    String nodeName,
                                    Collection<Long> userIds,
                                    Long operatorId,
                                    LocalDateTime now) {
        if (CollectionUtils.isEmpty(userIds)) {
            return List.of();
        }
        Set<Long> receiverIds = new LinkedHashSet<>(userIds);
        List<WfCc> ccList = new ArrayList<>();
        for (Long receiverId : receiverIds) {
            if (receiverId == null) {
                continue;
            }
            WfCc cc = new WfCc();
            cc.setInstanceId(instance.getId());
            cc.setNodeKey(nodeKey);
            cc.setNodeName(nodeName);
            cc.setReceiverId(receiverId);
            cc.setReadStatus(0);
            cc.setCreatedAt(now);
            ccList.add(cc);
        }
        if (!ccList.isEmpty()) {
            ccService.saveBatch(ccList);
            notificationService.notifyCcReceivers(instance, ccList, operatorId);
        }
        return ccList.stream().map(WfCc::getReceiverId).toList();
    }

    public List<Long> createRecords(WfProcessInstance instance,
                                    List<WorkflowGraph.NodeInfo> ccNodes,
                                    Long operatorId,
                                    LocalDateTime now) {
        if (CollectionUtils.isEmpty(ccNodes)) {
            return List.of();
        }
        List<Long> receiverIds = new ArrayList<>();
        for (WorkflowGraph.NodeInfo ccNode : ccNodes) {
            List<Long> nodeReceiverIds = nodeUserIds(ccNode, WorkflowGraphProperty.CC_USER_IDS);
            receiverIds.addAll(createRecords(instance, ccNode.key(), ccNode.name(), nodeReceiverIds, operatorId, now));
        }
        return distinctIds(receiverIds);
    }

    private List<Long> distinctIds(Collection<Long> ids) {
        return ids.stream()
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        ArrayList::new));
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
        if (valueNode.isTextual()) {
            String trimmed = valueNode.asText().trim();
            if (!trimmed.isEmpty()) {
                try {
                    target.add(Long.valueOf(trimmed));
                } catch (NumberFormatException ignored) {
                    // 节点人员配置允许字符串扩展，无法转成用户ID时跳过。
                }
            }
        }
    }
}
