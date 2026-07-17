package com.laker.admin.infrastructure.message.local.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 本地事务消息。消息表本身就是待投递队列，状态和租约共同决定当前所有者。
 */
@Data
@TableName("infra_local_message")
public class LocalMessage {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String messageKey;
    private String operationName;
    private String payloadJson;
    private String status;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String workerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String leaseToken;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime leaseUntil;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lastError;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime sentAt;
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
    @Version
    @TableField(fill = FieldFill.INSERT)
    private Integer version;
    private String remark;
}
