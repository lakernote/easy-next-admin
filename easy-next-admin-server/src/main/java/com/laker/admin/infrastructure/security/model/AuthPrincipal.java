package com.laker.admin.infrastructure.security.model;

import com.laker.admin.infrastructure.security.datascope.model.DataScopeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthPrincipal {
    private Long sessionId;
    private Long userId;
    private String userName;
    private String nickName;
    private Long deptId;
    private String deptName;
    private Long permissionVersion;
    private boolean superAdmin;
    private Set<Long> deptIds;
    private List<String> roles;
    private List<String> roleNames;
    private List<String> permissions;
    private List<DataScopeType> dataScopes;

    public boolean hasPermission(String permission) {
        return superAdmin || (permissions != null && permissions.contains(permission));
    }
}
