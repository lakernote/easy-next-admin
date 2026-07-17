package com.laker.admin.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wf_process_definition")
public class WfProcessDefinition {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private String processKey;
    private String processName;
    private Integer currentVersion;
    private String status;
    private String remark;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
