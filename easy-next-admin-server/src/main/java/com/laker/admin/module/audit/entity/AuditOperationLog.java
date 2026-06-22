package com.laker.admin.module.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_operation_log")
public class AuditOperationLog {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    @JsonIgnore
    private String traceId;
    private String module;
    private String action;
    private Long operatorId;
    private String operatorName;
    private String requestMethod;
    private String requestUri;
    private String requestParams;
    private String responseStatus;
    private String errorMessage;
    private String ip;
    private String userAgent;
    private Integer durationMs;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;
}
