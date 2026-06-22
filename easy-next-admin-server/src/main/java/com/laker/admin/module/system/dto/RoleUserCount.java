package com.laker.admin.module.system.dto;

import lombok.Data;

/**
 * 角色绑定用户数聚合结果。
 */
@Data
public class RoleUserCount {
    private Long roleId;
    private Long userCount;
}
