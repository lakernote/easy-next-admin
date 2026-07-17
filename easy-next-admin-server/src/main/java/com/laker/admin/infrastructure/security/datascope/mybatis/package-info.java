/**
 * MyBatis 数据权限执行层。
 *
 * <p>拦截器只识别带 {@code @DataScope} 的查询，并在分页插件之前完成 SQL 包装。</p>
 */
package com.laker.admin.infrastructure.security.datascope.mybatis;
