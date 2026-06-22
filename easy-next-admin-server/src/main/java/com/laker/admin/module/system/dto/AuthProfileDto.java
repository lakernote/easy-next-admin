package com.laker.admin.module.system.dto;

import com.laker.admin.module.system.dto.auth.AuthUserProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthProfileDto {
    private AuthUserProfile user;
    private List<String> roles;
    private List<String> roleNames;
    private List<String> permissions;
    private List<MenuVo> menus;
}
