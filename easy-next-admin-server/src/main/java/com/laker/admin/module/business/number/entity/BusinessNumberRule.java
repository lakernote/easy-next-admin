package com.laker.admin.module.business.number.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 业务编号规则。
 *
 * <p>这里维护的是用户可见的单号规则，不替代表主键或分布式 ID。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("biz_number_rule")
public class BusinessNumberRule implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private String ruleCode;
    private String ruleName;
    private String prefix;
    private String datePattern;
    /** 避开 MySQL 关键字 separator，接口层仍使用 separator 表达业务语义。 */
    private String numberSeparator;
    private Integer sequenceWidth;
    private Integer sequenceStep;
    private Long initialValue;
    private Boolean enable;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;
    @TableField(fill = FieldFill.INSERT)
    private Long createDeptId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
    @Version
    @TableField(fill = FieldFill.INSERT)
    private Integer version;
    private String remark;
}
