package com.laker.admin.module.audit.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.laker.admin.module.audit.entity.AuditLoginLog;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录审计对外响应模型。
 */
@Data
@Builder
public class AuditLoginLogView {
    private Long id;
    private Long userId;
    private String userName;
    private String loginResult;
    private String failReason;
    private String ip;
    private String userAgent;
    private String clientType;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime loginTime;

    public static AuditLoginLogView from(AuditLoginLog log) {
        if (log == null) {
            return null;
        }
        return AuditLoginLogView.builder()
                .id(log.getId())
                .userId(log.getUserId())
                .userName(log.getUserName())
                .loginResult(log.getLoginResult())
                .failReason(log.getFailReason())
                .ip(log.getIp())
                .userAgent(log.getUserAgent())
                .clientType(log.getClientType())
                .loginTime(log.getLoginTime())
                .build();
    }

    public static List<AuditLoginLogView> fromList(List<AuditLoginLog> logs) {
        return logs == null ? List.of() : logs.stream().map(AuditLoginLogView::from).toList();
    }
}
