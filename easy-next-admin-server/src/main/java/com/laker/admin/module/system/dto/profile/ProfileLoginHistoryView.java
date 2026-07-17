package com.laker.admin.module.system.dto.profile;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.laker.admin.module.audit.entity.AuditLoginLog;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 个人中心登录历史响应模型。当前用户上下文已限定身份，不再暴露用户主键和链路追踪号。
 */
@Data
@Builder
public class ProfileLoginHistoryView {
    private Long id;
    private String loginResult;
    private String failReason;
    private String ip;
    private String userAgent;
    private String clientType;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime loginTime;

    public static ProfileLoginHistoryView from(AuditLoginLog log) {
        if (log == null) {
            return null;
        }
        return ProfileLoginHistoryView.builder()
                .id(log.getId())
                .loginResult(log.getLoginResult())
                .failReason(log.getFailReason())
                .ip(log.getIp())
                .userAgent(log.getUserAgent())
                .clientType(log.getClientType())
                .loginTime(log.getLoginTime())
                .build();
    }

    public static List<ProfileLoginHistoryView> fromList(List<AuditLoginLog> logs) {
        return logs == null ? List.of() : logs.stream().map(ProfileLoginHistoryView::from).toList();
    }
}
