package com.laker.admin.module.workflow.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.laker.admin.module.workflow.entity.WfProcessDefinition;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程定义列表响应模型。
 */
@Data
@Builder
public class WfProcessDefinitionView {
    private Long id;
    private String processKey;
    private String processName;
    private Integer currentVersion;
    private String status;
    private String remark;
    private Long createdBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;
    private Long updatedBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;

    public static WfProcessDefinitionView from(WfProcessDefinition definition) {
        if (definition == null) {
            return null;
        }
        return WfProcessDefinitionView.builder()
                .id(definition.getId())
                .processKey(definition.getProcessKey())
                .processName(definition.getProcessName())
                .currentVersion(definition.getCurrentVersion())
                .status(definition.getStatus())
                .remark(definition.getRemark())
                .createdBy(definition.getCreatedBy())
                .createdAt(definition.getCreatedAt())
                .updatedBy(definition.getUpdatedBy())
                .updatedAt(definition.getUpdatedAt())
                .build();
    }

    public static List<WfProcessDefinitionView> fromList(List<WfProcessDefinition> definitions) {
        return definitions == null ? List.of() : definitions.stream().map(WfProcessDefinitionView::from).toList();
    }
}
