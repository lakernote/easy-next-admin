package com.laker.admin.module.schedule.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.laker.admin.module.schedule.enums.JobStateEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 *
 * </p>
 *
 * @author easynext
 * @since 2021-08-17
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("schedule_job")
public class ScheduleJob implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "job_id", type = IdType.ASSIGN_ID)
    private Long jobId;

    /**
     * 任务的编码 必须全局唯一
     */
    private String jobCode;

    /**
     * 任务的名称
     */
    private String jobName;

    /**
     * 任务的类名称
     */
    private String jobClassName;

    /**
     * 任务的cron表达式
     */
    private String cronExpression;

    /**
     * 任务创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 是否启用
     */
    private Boolean enable;


    private JobStateEnum jobState;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private Long createDeptId;

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
