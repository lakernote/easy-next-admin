/**
 * 数据权限元数据读取和缓存实现。
 *
 * <p>该包读取角色、用户角色、自定义部门和部门树等权限元数据，不读取业务数据；
 * 读取缓存走 {@code @Cacheable}，写入失效由系统模块维护入口的 {@code @CacheEvict} 触发。</p>
 */
package com.laker.admin.infrastructure.security.datascope.repository;
