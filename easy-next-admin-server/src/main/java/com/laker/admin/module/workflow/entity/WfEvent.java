package com.laker.admin.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wf_hi_event")
public class WfEvent {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long instanceId;
    private Long taskId;
    private Long operatorId;
    private String action;
    private String fromNodeKey;
    private String toNodeKey;
    private Long targetUserId;
    private String comment;
    private LocalDateTime createdAt;
}
