package com.laker.admin.infrastructure.web.mvc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明 {@link PageRequest} 允许使用的查询字段。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface PageQuery {

    /**
     * 字段白名单枚举。枚举项必须实现 {@link PageQueryField}。
     */
    Class<? extends PageQueryField> fields();

    /**
     * 未传 size 时的默认每页条数。
     */
    int defaultSize() default 10;

    /**
     * 允许的最大每页条数，防止一次查询过多数据。
     */
    int maxSize() default 100;
}
