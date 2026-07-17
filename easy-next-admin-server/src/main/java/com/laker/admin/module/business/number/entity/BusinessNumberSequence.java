package com.laker.admin.module.business.number.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务编号计数器。
 *
 * <p>一条规则在不同日期段拥有独立计数器，通过数据库行锁保证多实例并发取号不重复。</p>
 */
@Data
@TableName("biz_number_sequence")
public class BusinessNumberSequence {
    @TableId
    private String sequenceKey;
    private String ruleCode;
    private String segment;
    private Long currentValue;
    private LocalDateTime updatedAt;
}
