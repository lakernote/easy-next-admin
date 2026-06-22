package com.laker.admin.module.system.dto;

import lombok.Data;

/**
 * 用户角色绑定关系投影。
 */
@Data
public class UserRoleBinding {
    private Long userId;
    private Long roleId;
    private String roleCode;
    private String roleName;
}
