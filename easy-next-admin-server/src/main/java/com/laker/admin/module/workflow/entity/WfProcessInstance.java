package com.laker.admin.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wf_ru_process_instance")
public class WfProcessInstance {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
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
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
    @Version
    private Integer version;
}
