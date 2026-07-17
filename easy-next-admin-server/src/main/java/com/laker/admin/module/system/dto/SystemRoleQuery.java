package com.laker.admin.module.system.dto;

import lombok.Data;

/**
 * 角色管理分页查询条件。
 */
@Data
public class SystemRoleQuery {
    private long current = 1;
    private long size = 10;
    private String keyword;
    private Boolean enable;
}
