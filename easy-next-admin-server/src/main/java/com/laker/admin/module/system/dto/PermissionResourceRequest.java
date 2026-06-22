package com.laker.admin.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 菜单权限资源保存请求。type=0 为目录，type=1 为页面，type=2 为按钮权限。
 */
@Data
public class PermissionResourceRequest {
    private Long menuId;
    private Long pid;
    @NotBlank(message = "资源名称不能为空")
    private String title;
    private String icon;
    private String href;
    private Integer sort;
    private Boolean enable;
    private String remark;
    @NotNull(message = "资源类型不能为空")
    private Integer type;
    private String powerCode;
    private String componentPath;
    private Boolean visible;
}
