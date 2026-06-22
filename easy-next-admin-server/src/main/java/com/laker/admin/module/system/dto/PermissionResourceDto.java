package com.laker.admin.module.system.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.laker.admin.module.system.entity.SysPower;
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
    private String powerCode;
    private String componentPath;
    private Boolean visible;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    private List<PermissionResourceDto> children;

    public static PermissionResourceDto from(SysPower power) {
        return PermissionResourceDto.builder()
                .menuId(power.getMenuId())
                .pid(power.getPid())
                .title(power.getTitle())
                .icon(power.getIcon())
                .href(isPage(power) ? power.getHref() : null)
                .sort(power.getSort())
                .enable(power.getEnable())
                .remark(power.getRemark())
                .type(power.getType())
                .powerCode(power.getPowerCode())
                .componentPath(isPage(power) ? power.getComponentPath() : null)
                .visible(power.getVisible())
                .createTime(power.getCreateTime())
                .build();
    }

    private static boolean isPage(SysPower power) {
        return power != null && power.getType() != null && power.getType() == RESOURCE_TYPE_PAGE;
    }
}
