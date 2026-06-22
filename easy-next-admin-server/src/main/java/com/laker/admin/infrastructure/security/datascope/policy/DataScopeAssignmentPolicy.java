package com.laker.admin.infrastructure.security.datascope.policy;

import com.laker.admin.infrastructure.security.datascope.model.DataScopeType;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色数据范围授权策略。
 *
 * <p>规则只表达“能授予哪些范围”，不读取数据库；具体自定义部门是否在当前账号可见范围内，
 * 仍由系统服务结合数据权限查询结果校验。</p>
 */
@Component
public class DataScopeAssignmentPolicy {

    public Set<String> assignableCodes(AuthPrincipal principal) {
        if (principal == null || principal.isSuperAdmin()) {
            return orderedCodes(DataScopeType.roleDataScopeCodes());
        }
        Set<DataScopeType> ownedScopes = principal.getDataScopes() == null
                ? Set.of()
                : principal.getDataScopes().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ownedScopes.contains(DataScopeType.ALL)) {
            return orderedCodes(DataScopeType.roleDataScopeCodes());
        }
        Set<String> assignable = new LinkedHashSet<>();
        // 默认只允许向下收敛，避免部门账号把下级角色放大成更高数据范围。
        assignable.add(DataScopeType.SELF.getCode());
        if (ownedScopes.contains(DataScopeType.DEPT_AND_CHILDREN)) {
            assignable.add(DataScopeType.DEPT_AND_CHILDREN.getCode());
            assignable.add(DataScopeType.DEPT.getCode());
            assignable.add(DataScopeType.DEPT_SETS.getCode());
        } else {
            if (ownedScopes.contains(DataScopeType.DEPT)) {
                assignable.add(DataScopeType.DEPT.getCode());
                assignable.add(DataScopeType.DEPT_SETS.getCode());
            }
            if (ownedScopes.contains(DataScopeType.DEPT_SETS)) {
                assignable.add(DataScopeType.DEPT_SETS.getCode());
            }
        }
        return orderedCodes(assignable);
    }

    public boolean canAssignAll(AuthPrincipal principal) {
        return assignableCodes(principal).contains(DataScopeType.ALL.getCode());
    }

    private Set<String> orderedCodes(Set<String> allowedCodes) {
        return DataScopeType.orderedRoleDataScopeCodes().stream()
                .filter(allowedCodes::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
