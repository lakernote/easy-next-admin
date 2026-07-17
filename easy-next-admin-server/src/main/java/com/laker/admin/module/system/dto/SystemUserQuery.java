package com.laker.admin.module.system.dto;

import lombok.Data;

/**
 * 用户管理分页查询条件。
 */
@Data
public class SystemUserQuery {
    private long page = 1;
    private long limit = 10;
    private Long deptId;
    private Integer enable;
    private String keyWord;
}
