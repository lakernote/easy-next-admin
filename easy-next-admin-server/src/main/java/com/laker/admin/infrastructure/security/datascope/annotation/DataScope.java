package com.laker.admin.infrastructure.security.datascope.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 Mapper 查询需要自动追加数据范围条件。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {

    /**
     * 类型标记时限制生效的 Mapper 方法名。为空表示全部方法生效。
     */
    String[] methods() default {};

    /**
     * 外层查询可见的组织范围字段。默认适配 MyBatis-Plus 实体属性别名 deptId。
     * 常用字段优先使用 {@link DataScopeColumns}。
     */
    String deptColumn() default DataScopeColumns.DEPT_ID;

    /**
     * 外层查询可见的本人范围字段。为空时遇到本人范围会拒绝查询，避免误放开数据。
     * 常用字段优先使用 {@link DataScopeColumns}。
     */
    String selfColumn() default "";
}
