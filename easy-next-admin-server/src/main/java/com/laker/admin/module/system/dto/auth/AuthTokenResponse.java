package com.laker.admin.module.system.dto.auth;

import com.laker.admin.module.system.dto.MenuVo;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AuthTokenResponse {
    private String accessToken;
    private long accessExpiresIn;
    private AuthUserProfile user;
    private List<String> roles;
    private List<String> roleNames;
    private List<String> permissions;
    private List<MenuVo> menus;
}
