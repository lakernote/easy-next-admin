package com.laker.admin.module.schedule.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 调度实例心跳。
 *
 * <p>一条记录代表一个正在运行的应用进程，用于排查多实例下任务到底在哪个节点注册和执行。</p>
 */
@Data
@TableName("schedule_instance")
public class ScheduleInstance implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "instance_id", type = IdType.INPUT)
    private String instanceId;
    private String appName;
    private String hostName;
    private String ipAddress;
    private String processId;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime heartbeatAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
