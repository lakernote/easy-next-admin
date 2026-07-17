package com.laker.admin.infrastructure.web.mvc;

import java.util.EnumSet;
import java.util.Set;

/**
 * PageRequest 可查询字段白名单。
 *
 * <p>Controller 只暴露 {@link #paramName()} 给前端，SQL 列名只能来自服务端枚举中的
 * {@link #columnName()} 映射，避免客户端直接控制数据库列名。</p>
 */
public interface PageQueryField {

    String paramName();

    String columnName();

    default boolean filterable() {
        return true;
    }

    default boolean sortable() {
        return true;
    }

    default Set<PageQueryOperator> allowedOperators() {
        return EnumSet.allOf(PageQueryOperator.class);
    }
}
