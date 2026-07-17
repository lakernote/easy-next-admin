package com.laker.admin.infrastructure.security.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthSession {
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_LOGOUT = "LOGOUT";
    public static final String STATUS_KICKED = "KICKED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_PERMISSION_CHANGED = "PERMISSION_CHANGED";

    private Long sessionId;
    private Long userId;
    private String accessTokenHash;
    private String clientType;
    private String clientVersion;
    private String ip;
    private String userAgent;
    private LocalDateTime loginTime;
    private LocalDateTime lastActiveTime;
    private LocalDateTime accessExpireTime;
    private String status;
    private AuthPrincipal principal;
    private Long createBy;
    private Long createDeptId;
    @JsonIgnore
    private String accessToken;
}
