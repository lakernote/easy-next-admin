package com.laker.admin.module.workflow.sequence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_request_sequence")
public class BizRequestSequence {
    @TableId
    private String sequenceKey;
    private Long currentValue;
    private LocalDateTime updatedAt;
}
