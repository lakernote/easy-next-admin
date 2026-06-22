package com.laker.admin.infrastructure.security.datascope.model;

import com.laker.admin.infrastructure.security.datascope.annotation.DataScope;

import java.util.Set;

/**
 * 当前请求在某个数据资源上的可访问范围。
 *
 * <p>这里只表达权限范围，不直接拼接 SQL。具体业务表用哪个字段过滤，由 Mapper 上的
 * {@link DataScope} 显式声明，再由 MyBatis 拦截器统一落地。</p>
 */
public record DataScopeCondition(
        DataScopeType scopeType,
        Long userId,
        Long deptId,
        Set<Long> deptIds
) {
    public static DataScopeCondition all(Long userId, Long deptId, Set<Long> deptIds) {
        return new DataScopeCondition(DataScopeType.ALL, userId, deptId, normalize(deptIds));
    }

    public static DataScopeCondition denied() {
        return new DataScopeCondition(null, null, null, Set.of());
    }

    public static DataScopeCondition self(Long userId, Long deptId, Set<Long> deptIds) {
        return new DataScopeCondition(DataScopeType.SELF, userId, deptId, normalize(deptIds));
    }

    public DataScopeCondition {
        deptIds = normalize(deptIds);
    }

    public boolean isAllData() {
        return scopeType == DataScopeType.ALL;
    }

    public boolean isDenied() {
        return scopeType == null;
    }

    public boolean isSelfOnly() {
        return scopeType == DataScopeType.SELF;
    }

    public boolean isCurrentDeptOnly() {
        return scopeType == DataScopeType.DEPT;
    }

    public boolean isResolvedDeptSet() {
        return scopeType == DataScopeType.DEPT_AND_CHILDREN || scopeType == DataScopeType.DEPT_SETS;
    }

    private static Set<Long> normalize(Set<Long> deptIds) {
        return deptIds == null ? Set.of() : Set.copyOf(deptIds);
    }
}
