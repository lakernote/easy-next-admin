package com.laker.admin.module.system.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "登录体验账号信息")
public record DemoAccountResponse(
        @Schema(description = "角色名称", example = "超级管理员")
        String roleName,
        @Schema(description = "用户名", example = "admin")
        String username,
        @Schema(description = "演示环境默认密码", example = "admin")
        String password,
        @Schema(description = "账号说明", example = "查看和维护全部内置能力")
        String description
) {
}
