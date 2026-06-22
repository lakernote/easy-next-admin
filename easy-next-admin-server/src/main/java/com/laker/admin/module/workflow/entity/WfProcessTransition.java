package com.laker.admin.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("wf_process_transition")
public class WfProcessTransition {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long versionId;
    private String fromNodeKey;
    private String toNodeKey;
    private String conditionType;
    private String conditionJson;
    private Integer priority;
}
