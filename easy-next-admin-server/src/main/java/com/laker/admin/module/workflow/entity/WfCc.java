package com.laker.admin.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wf_ru_cc")
public class WfCc {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long instanceId;
    private String nodeKey;
    private String nodeName;
    private Long receiverId;
    private Integer readStatus;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
