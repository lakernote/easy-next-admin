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
 * 批处理任务主表。
 *
 * <p>记录一次批量执行的治理视角：谁触发、处理多少、当前状态和是否请求取消。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("batch_task")
public class BatchTask implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private String taskType;
    private String taskName;
    private String businessKey;
    private String triggerType;
    private String triggerRefId;
    private String status;
    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
    private Integer skippedCount;
    private Integer progressPercent;
    private Boolean cancelRequested;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime startedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime finishedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String traceId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String errorMessage;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String resultMessage;

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
