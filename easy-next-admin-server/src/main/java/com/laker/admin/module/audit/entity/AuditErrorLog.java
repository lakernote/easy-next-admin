package com.laker.admin.module.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.laker.admin.module.system.entity.SysUser;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_error_log")
public class AuditErrorLog {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    @JsonIgnore
    private String traceId;
    private String requestUri;
    private String requestMethod;
    private String errorType;
    private String errorMessage;
    private String stackTrace;
    private Long operatorId;
    @TableField(exist = false)
    private SysUser operator;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;
}
