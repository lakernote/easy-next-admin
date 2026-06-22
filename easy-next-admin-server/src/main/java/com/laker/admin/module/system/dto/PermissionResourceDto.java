package com.laker.admin.module.system.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.laker.admin.module.system.entity.SysMenuResource;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜单、页面和按钮权限资源统一响应模型。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PermissionResourceDto {
    private static final int RESOURCE_TYPE_PAGE = 1;

    private Long menuId;
    private Long pid;
    private String title;
    private String icon;
    private String href;
    private Integer sort;
    private Boolean enable;
    private String remark;
    private Integer type;
    private String permissionCode;
    private String componentPath;
    private Boolean visible;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    private List<PermissionResourceDto> children;

    public static PermissionResourceDto from(SysMenuResource resource) {
        return PermissionResourceDto.builder()
                .menuId(resource.getMenuId())
                .pid(resource.getPid())
                .title(resource.getTitle())
                .icon(resource.getIcon())
                .href(isPage(resource) ? resource.getHref() : null)
                .sort(resource.getSort())
                .enable(resource.getEnable())
                .remark(resource.getRemark())
                .type(resource.getType())
                .permissionCode(resource.getPermissionCode())
                .componentPath(isPage(resource) ? resource.getComponentPath() : null)
                .visible(resource.getVisible())
                .createTime(resource.getCreateTime())
                .build();
    }

    private static boolean isPage(SysMenuResource resource) {
        return resource != null && resource.getType() != null && resource.getType() == RESOURCE_TYPE_PAGE;
    }
}
