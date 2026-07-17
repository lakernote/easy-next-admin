package com.laker.admin.module.workflow.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.laker.admin.module.workflow.entity.WfTask;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程任务响应模型。
 */
@Data
@Builder
public class WfTaskView {
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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime finishedAt;
    private Long createdBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;
    private Long updatedBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;

    public static WfTaskView from(WfTask task) {
        if (task == null) {
            return null;
        }
        return WfTaskView.builder()
                .id(task.getId())
                .instanceId(task.getInstanceId())
                .nodeKey(task.getNodeKey())
                .nodeName(task.getNodeName())
                .assigneeId(task.getAssigneeId())
                .assigneeDeptId(task.getAssigneeDeptId())
                .assignmentRuleType(task.getAssignmentRuleType())
                .assignmentRuleName(task.getAssignmentRuleName())
                .assignmentResolvePath(task.getAssignmentResolvePath())
                .status(task.getStatus())
                .approveComment(task.getApproveComment())
                .startedAt(task.getStartedAt())
                .finishedAt(task.getFinishedAt())
                .createdBy(task.getCreatedBy())
                .createdAt(task.getCreatedAt())
                .updatedBy(task.getUpdatedBy())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    public static List<WfTaskView> fromList(List<WfTask> tasks) {
        return tasks == null ? List.of() : tasks.stream().map(WfTaskView::from).toList();
    }
}
