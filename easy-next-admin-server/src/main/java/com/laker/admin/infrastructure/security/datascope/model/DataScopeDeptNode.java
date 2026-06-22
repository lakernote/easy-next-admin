package com.laker.admin.infrastructure.security.datascope.model;

/**
 * 数据权限计算用的轻量部门节点。
 *
 * <p>只保留部门树展开需要的字段，避免 Resolver 依赖系统模块实体。</p>
 */
public record DataScopeDeptNode(Long deptId, Long parentId) {
}
