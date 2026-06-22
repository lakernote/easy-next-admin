package com.laker.admin.module.workflow.dto;

import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.entity.WfTask;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WfTaskListItem {
    private Long id;
    private Long instanceId;
    private String nodeKey;
    private String nodeName;
    private Long assigneeId;
    private Long assigneeDeptId;
    private String assignmentRuleType;
    private String assignmentRuleName;
    private String assignmentResolvePath;
    private String status;
    private String approveComment;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
    private String instanceTitle;
    private String businessType;
    private String businessId;
    private String instanceStatus;
    private Long instanceInitiatorId;

    public static WfTaskListItem from(WfTask task, WfProcessInstance instance) {
        WfTaskListItem item = new WfTaskListItem();
        item.setId(task.getId());
        item.setInstanceId(task.getInstanceId());
        item.setNodeKey(task.getNodeKey());
        item.setNodeName(task.getNodeName());
        item.setAssigneeId(task.getAssigneeId());
        item.setAssigneeDeptId(task.getAssigneeDeptId());
        item.setAssignmentRuleType(task.getAssignmentRuleType());
        item.setAssignmentRuleName(task.getAssignmentRuleName());
        item.setAssignmentResolvePath(task.getAssignmentResolvePath());
        item.setStatus(task.getStatus());
        item.setApproveComment(task.getApproveComment());
        item.setStartedAt(task.getStartedAt());
        item.setFinishedAt(task.getFinishedAt());
        item.setCreatedBy(task.getCreatedBy());
        item.setCreatedAt(task.getCreatedAt());
        item.setUpdatedBy(task.getUpdatedBy());
        item.setUpdatedAt(task.getUpdatedAt());
        item.setInstanceTitle(instance == null ? "流程 " + task.getInstanceId() : instance.getTitle());
        item.setBusinessType(instance == null ? "" : instance.getBusinessType());
        item.setBusinessId(instance == null ? "" : instance.getBusinessId());
        item.setInstanceStatus(instance == null ? "" : instance.getStatus());
        item.setInstanceInitiatorId(instance == null ? null : instance.getInitiatorId());
        return item;
    }
}
