package com.laker.admin.module.system.dto.auth;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 认证响应中的安全用户画像。
 *
 * <p>登录和 /me 接口只返回前端渲染需要的字段，不直接暴露数据库实体，避免密码、乐观锁、
 * 逻辑删除等内部字段进入公网接口契约。</p>
 */
@Data
@Builder
public class AuthUserProfile {
    private Long userId;
    private String userName;
    private String nickName;
    private String realName;
    private String employeeNo;
    private String positionName;
    private Long deptId;
    private String deptName;
    private String phone;
    private String email;
    private String avatar;
    private LocalDateTime lastLoginTime;
}
