package com.laker.admin.module.audit.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.laker.admin.module.audit.entity.AuditErrorLog;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 异常审计对外响应模型。
 */
@Data
@Builder
public class AuditErrorLogView {
    private Long id;
    private String requestUri;
    private String requestMethod;
    private String errorType;
    private String errorMessage;
    private String stackTrace;
    private Long operatorId;
    private AuditUserView operator;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    public static AuditErrorLogView from(AuditErrorLog log) {
        if (log == null) {
            return null;
        }
        return AuditErrorLogView.builder()
                .id(log.getId())
                .requestUri(log.getRequestUri())
                .requestMethod(log.getRequestMethod())
                .errorType(log.getErrorType())
                .errorMessage(log.getErrorMessage())
                .stackTrace(log.getStackTrace())
                .operatorId(log.getOperatorId())
                .operator(AuditUserView.from(log.getOperator()))
                .createdAt(log.getCreatedAt())
                .build();
    }

    public static List<AuditErrorLogView> fromList(List<AuditErrorLog> logs) {
        return logs == null ? List.of() : logs.stream().map(AuditErrorLogView::from).toList();
    }
}
