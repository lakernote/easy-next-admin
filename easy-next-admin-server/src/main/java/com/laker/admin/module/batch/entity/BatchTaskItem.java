package com.laker.admin.module.batch.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
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
 * 批处理任务明细。
 *
 * <p>一条明细对应一条业务数据，失败原因和重试次数都落在这里，方便只补跑失败项。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("batch_task_item")
public class BatchTaskItem implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long taskId;
    private String itemKey;
    private String itemName;
    private String status;
    private Integer retryCount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String payload;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String errorMessage;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String resultMessage;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime startedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime finishedAt;

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
