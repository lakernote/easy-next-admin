package com.laker.admin.module.system.dto;

import lombok.Data;

/**
 * 组织架构查询条件。
 */
@Data
public class SystemDeptQuery {
    private long current = 1;
    private long size = 10;
    private String keyword;
    private Boolean status;
    private Long pid;
}
