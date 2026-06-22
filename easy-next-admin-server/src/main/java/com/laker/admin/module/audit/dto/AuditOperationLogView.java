package com.laker.admin.module.audit.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.laker.admin.module.audit.entity.AuditOperationLog;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作审计对外响应模型。
 */
@Data
@Builder
public class AuditOperationLogView {
    private Long id;
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

    public static AuditOperationLogView from(AuditOperationLog log) {
        if (log == null) {
            return null;
        }
        return AuditOperationLogView.builder()
                .id(log.getId())
                .module(log.getModule())
                .action(log.getAction())
                .operatorId(log.getOperatorId())
                .operatorName(log.getOperatorName())
                .requestMethod(log.getRequestMethod())
                .requestUri(log.getRequestUri())
                .requestParams(log.getRequestParams())
                .responseStatus(log.getResponseStatus())
                .errorMessage(log.getErrorMessage())
                .ip(log.getIp())
                .userAgent(log.getUserAgent())
                .durationMs(log.getDurationMs())
                .createdAt(log.getCreatedAt())
                .build();
    }

    public static List<AuditOperationLogView> fromList(List<AuditOperationLog> logs) {
        return logs == null ? List.of() : logs.stream().map(AuditOperationLogView::from).toList();
    }
}
