package com.laker.admin.infrastructure.security.datascope.mybatis;

import com.laker.admin.infrastructure.security.datascope.model.DataScopeCondition;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class DataScopeSqlRewriter {
    private static final String SCOPE_ALIAS = "ea_ds";
    private static final Pattern COLUMN_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    String rewrite(String originalSql, String deptColumn, String selfColumn, DataScopeCondition scope) {
        if (originalSql == null || originalSql.isBlank()) {
            return originalSql;
        }
        if (scope == null || scope.isDenied()) {
            return wrap(originalSql, "1 = 0");
        }
        if (scope.isAllData()) {
            return originalSql;
        }
        if (scope.isResolvedDeptSet()) {
            return wrap(originalSql, deptSetCondition(deptColumn, scope.deptIds()));
        }
        if (scope.isCurrentDeptOnly()) {
            return wrap(originalSql, idCondition(deptColumn, scope.deptId()));
        }
        if (scope.isSelfOnly()) {
            if (selfColumn == null || selfColumn.isBlank()) {
                return wrap(originalSql, "1 = 0");
            }
            return wrap(originalSql, idCondition(selfColumn, scope.userId()));
        }
        return wrap(originalSql, "1 = 0");
    }

    private String deptSetCondition(String deptColumn, Set<Long> deptIds) {
        String column = scopedColumn(deptColumn);
        if (deptIds == null || deptIds.isEmpty()) {
            return "1 = 0";
        }
        String values = deptIds.stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        return "%s IN (%s)".formatted(column, values);
    }

    private String idCondition(String columnName, Long id) {
        String column = scopedColumn(columnName);
        if (id == null) {
            return "1 = 0";
        }
        return "%s = %d".formatted(column, id);
    }

    private String scopedColumn(String columnName) {
        if (columnName == null || !COLUMN_PATTERN.matcher(columnName).matches()) {
            throw new IllegalArgumentException("Invalid data scope column: " + columnName);
        }
        // 字段名只能来自服务端注解常量或白名单格式，不能由前端参数拼接。
        return SCOPE_ALIAS + "." + columnName;
    }

    private String wrap(String originalSql, String condition) {
        // 原 SQL 包成外层子查询，避免业务 SQL 已有 where/order by 时再做脆弱字符串拼接。
        return "SELECT * FROM (%s) %s WHERE %s".formatted(trimTerminator(originalSql), SCOPE_ALIAS, condition);
    }

    private String trimTerminator(String sql) {
        String value = sql.trim();
        while (value.endsWith(";")) {
            value = value.substring(0, value.length() - 1).trim();
        }
        return value;
    }
}
