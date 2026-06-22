package com.laker.admin.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wf_ru_task")
public class WfTask {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
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
    @Version
    private Integer version;
}
