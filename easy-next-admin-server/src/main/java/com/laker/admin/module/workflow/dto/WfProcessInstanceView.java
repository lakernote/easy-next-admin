package com.laker.admin.module.workflow.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程实例列表响应模型，避免将乐观锁和审计更新列作为列表契约暴露。
 */
@Data
@Builder
public class WfProcessInstanceView {
    private Long id;
    private Long definitionId;
    private Long versionId;
    private String processKey;
    private String businessType;
    private String businessId;
    private String title;
    private Long initiatorId;
    private String currentNodeKey;
    private String status;
    private String variablesJson;
    private String definitionSnapshotJson;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    public static WfProcessInstanceView from(WfProcessInstance instance) {
        if (instance == null) {
            return null;
        }
        return WfProcessInstanceView.builder()
                .id(instance.getId())
                .definitionId(instance.getDefinitionId())
                .versionId(instance.getVersionId())
                .processKey(instance.getProcessKey())
                .businessType(instance.getBusinessType())
                .businessId(instance.getBusinessId())
                .title(instance.getTitle())
                .initiatorId(instance.getInitiatorId())
                .currentNodeKey(instance.getCurrentNodeKey())
                .status(instance.getStatus())
                .variablesJson(instance.getVariablesJson())
                .definitionSnapshotJson(instance.getDefinitionSnapshotJson())
                .startedAt(instance.getStartedAt())
                .endedAt(instance.getEndedAt())
                .createdAt(instance.getCreatedAt())
                .build();
    }

    public static List<WfProcessInstanceView> fromList(List<WfProcessInstance> instances) {
        return instances == null ? List.of() : instances.stream().map(WfProcessInstanceView::from).toList();
    }
}
