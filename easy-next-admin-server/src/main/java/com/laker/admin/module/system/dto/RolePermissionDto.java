package com.laker.admin.module.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolePermissionDto {
    private Long roleId;
    private String dataScope;
    private List<Long> deptIds;
    private List<String> permissionCodes;
    private List<String> assignableDataScopes;
    private Long roleUserCount;
}
