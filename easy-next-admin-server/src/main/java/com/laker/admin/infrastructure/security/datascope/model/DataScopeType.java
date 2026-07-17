package com.laker.admin.infrastructure.security.datascope.model;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 数据范围类型。
 *
 * <p>企业级约定：数据库、接口和审计日志只使用稳定 code；中文 label 只用于界面和报表展示。</p>
 */
@Getter
@AllArgsConstructor
public enum DataScopeType {
    ALL("ALL", "全部数据"),
    DEPT("DEPT", "本部门"),
    SELF("SELF", "本人数据"),
    DEPT_AND_CHILDREN("DEPT_AND_CHILDREN", "本部门及以下"),
    DEPT_SETS("DEPT_SETS", "自定义部门");

    @EnumValue
    private final String code;
    private final String label;

    /**
     * 角色数据范围只开放标准行级权限类型，不支持动态 SQL 这类不可控策略。
     */
    public static Set<String> roleDataScopeCodes() {
        return Set.copyOf(orderedRoleDataScopeCodes());
    }

    public static List<String> orderedRoleDataScopeCodes() {
        return List.of(
                ALL.code,
                DEPT_AND_CHILDREN.code,
                DEPT.code,
                SELF.code,
                DEPT_SETS.code
        );
    }

    public static DataScopeType fromRoleDataScope(String dataScope) {
        // 读取异常值或空值时默认收敛到本人数据，避免误放大数据范围。
        return resolveRoleDataScope(dataScope).orElse(SELF);
    }

    public static Optional<DataScopeType> resolveRoleDataScope(String dataScope) {
        if (dataScope == null || dataScope.isBlank()) {
            return Optional.of(SELF);
        }
        String normalized = dataScope.trim();
        for (DataScopeType value : values()) {
            if (value.code.equals(normalized)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
