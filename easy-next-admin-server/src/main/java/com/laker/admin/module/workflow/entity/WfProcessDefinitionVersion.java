package com.laker.admin.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wf_process_definition_version")
public class WfProcessDefinitionVersion {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long definitionId;
    private Integer version;
    private String graphJson;
    private String status;
    private Long publishedBy;
    private LocalDateTime publishedAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
