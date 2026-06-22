package com.laker.admin.infrastructure.security.datascope.annotation;

/**
 * MyBatis-Plus 常用 Mapper 方法名常量。
 *
 * <p>{@link DataScope#methods()} 需要编译期常量，统一放这里避免散写字符串。</p>
 */
public final class DataScopeMapperMethods {
    public static final String SELECT_LIST = "selectList";
    public static final String SELECT_PAGE = "selectPage";

    private DataScopeMapperMethods() {
    }
}
