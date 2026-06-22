package com.laker.admin.module.workflow.dto;

import com.laker.admin.module.workflow.entity.WfCc;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WfCcListItem {
    private Long id;
    private Long instanceId;
    private String nodeKey;
    private String nodeName;
    private Long receiverId;
    private Integer readStatus;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
    private String instanceTitle;
    private String businessType;
    private String businessId;
    private String instanceStatus;
    private Boolean historic;

    public static WfCcListItem from(WfCc cc, WfProcessInstance instance, boolean historic) {
        WfCcListItem item = new WfCcListItem();
        item.setId(cc.getId());
        item.setInstanceId(cc.getInstanceId());
        item.setNodeKey(cc.getNodeKey());
        item.setNodeName(cc.getNodeName());
        item.setReceiverId(cc.getReceiverId());
        item.setReadStatus(cc.getReadStatus());
        item.setReadAt(cc.getReadAt());
        item.setCreatedAt(cc.getCreatedAt());
        item.setInstanceTitle(instance == null ? "流程 " + cc.getInstanceId() : instance.getTitle());
        item.setBusinessType(instance == null ? "" : instance.getBusinessType());
        item.setBusinessId(instance == null ? "" : instance.getBusinessId());
        item.setInstanceStatus(instance == null ? "" : instance.getStatus());
        item.setHistoric(historic);
        return item;
    }
}
