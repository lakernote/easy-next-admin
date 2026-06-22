package com.laker.admin.infrastructure.message.local.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.util.Date;

/**
 * `event_type` VARCHAR(100) NOT NULL,
 * `next_retry_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
 * `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
 * `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
 */
@Data
@TableName("infra_local_message")
public class LocalMessage {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    @TableField(fill = FieldFill.INSERT)
    private String name;
    private String status;
    private Date createTime;
    private Date updateTime;
    private int retryCount;
    private String param;
    private String processTag;
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;
    @TableField(fill = FieldFill.INSERT)
    private Long createDeptId;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
    @Version
    @TableField(fill = FieldFill.INSERT)
    private Integer version;
    private String remark;
}
