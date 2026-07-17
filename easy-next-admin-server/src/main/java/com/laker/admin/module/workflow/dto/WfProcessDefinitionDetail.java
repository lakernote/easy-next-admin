package com.laker.admin.module.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WfProcessDefinitionDetail {
    private Long id;
    private String processKey;
    private String processName;
    private Integer currentVersion;
    private String status;
    private String remark;
    private String graphJson;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
