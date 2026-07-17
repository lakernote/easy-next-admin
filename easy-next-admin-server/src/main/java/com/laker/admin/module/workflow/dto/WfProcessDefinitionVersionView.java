package com.laker.admin.module.workflow.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.laker.admin.module.workflow.entity.WfProcessDefinitionVersion;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程定义版本响应模型。
 */
@Data
@Builder
public class WfProcessDefinitionVersionView {
    private Long id;
    private Long definitionId;
    private Integer version;
    private String graphJson;
    private String status;
    private Long publishedBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime publishedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    public static WfProcessDefinitionVersionView from(WfProcessDefinitionVersion version) {
        if (version == null) {
            return null;
        }
        return WfProcessDefinitionVersionView.builder()
                .id(version.getId())
                .definitionId(version.getDefinitionId())
                .version(version.getVersion())
                .graphJson(version.getGraphJson())
                .status(version.getStatus())
                .publishedBy(version.getPublishedBy())
                .publishedAt(version.getPublishedAt())
                .createdAt(version.getCreatedAt())
                .build();
    }
}
