package com.laker.admin.module.system.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.laker.admin.infrastructure.security.datascope.model.DataScopeType;
import com.laker.admin.module.system.entity.SysRole;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色管理对外响应模型。
 */
@Data
@Builder
public class SystemRoleView {
    private Long roleId;
    private String roleName;
    private String roleCode;
    private String details;
    private Boolean enable;
    private Integer roleLevel;
    private String dataScope;
    private Long userCount;
    private Boolean checked;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    public static SystemRoleView from(SysRole role) {
        return SystemRoleView.builder()
                .roleId(role.getRoleId())
                .roleName(role.getRoleName())
                .roleCode(role.getRoleCode())
                .details(role.getDetails())
                .enable(role.getEnable())
                .roleLevel(role.getRoleLevel())
                .dataScope(DataScopeType.fromRoleDataScope(role.getDataScope()).getCode())
                .userCount(role.getUserCount())
                .checked(role.isChecked())
                .createTime(role.getCreateTime())
                .build();
    }
}
