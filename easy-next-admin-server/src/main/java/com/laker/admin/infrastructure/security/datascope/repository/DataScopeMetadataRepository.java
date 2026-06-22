package com.laker.admin.infrastructure.security.datascope.repository;

import com.laker.admin.infrastructure.security.datascope.model.DataScopeDeptNode;

import java.util.List;
import java.util.Set;

/**
 * 数据权限元数据读取端口。
 *
 * <p>Resolver 只依赖该端口，不直接关心 MyBatis、JdbcTemplate 或系统模块 Service。</p>
 */
public interface DataScopeMetadataRepository {

    /**
     * 读取未删除且启用的部门节点，用于计算“本部门及下级”范围。
     */
    List<DataScopeDeptNode> listActiveDeptNodes();

    /**
     * 读取当前启用角色授予用户的自定义部门集合。
     */
    Set<Long> listCustomDeptIdsByUserId(Long userId);
}
