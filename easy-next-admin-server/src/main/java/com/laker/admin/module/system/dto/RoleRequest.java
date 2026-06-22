package com.laker.admin.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 角色保存请求。角色授权、数据范围配置走独立接口维护。
 */
@Data
public class RoleRequest {
    private Long roleId;
    @NotBlank(message = "角色名称不能为空")
    private String roleName;
    @NotBlank(message = "权限编码不能为空")
    private String roleCode;
    private String details;
    private Boolean enable;
    private Integer roleLevel;
}
