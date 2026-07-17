package com.laker.admin.module.system.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 在线会话对外响应模型，避免控制器直接暴露安全基础设施内部对象。
 */
@Data
@Builder
public class OnlineSessionView {
    private Long sessionId;
    private Long userId;
    private String userName;
    private String nickName;
    private String avatar;
    private String clientType;
    private String clientVersion;
    private String ip;
    private String userAgent;
    private String status;
    private Boolean current;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime loginTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastActiveTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime accessExpireTime;
}
