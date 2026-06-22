package com.laker.admin.infrastructure.security.datascope.repository;

import com.laker.admin.infrastructure.security.datascope.model.DataScopeDeptNode;
import com.laker.admin.infrastructure.security.datascope.model.DataScopeType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 基于 JDBC 的数据权限元数据读取实现。
 *
 * <p>这里有意不走 MyBatis Mapper，避免在 MyBatis 数据权限拦截器内部再次触发插件链。</p>
 */
@Repository
public class JdbcDataScopeMetadataRepository implements DataScopeMetadataRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcDataScopeMetadataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<DataScopeDeptNode> listActiveDeptNodes() {
        return jdbcTemplate.query(
                "select id, pid from sys_dept where deleted = 0 and status = 1",
                (rs, rowNum) -> new DataScopeDeptNode(requiredLong(rs, "id"), nullableLong(rs, "pid"))
        );
    }

    @Override
    public Set<Long> listCustomDeptIdsByUserId(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        // 自定义部门范围只认标准 code，避免存储契约漂移。
        List<Long> deptIds = jdbcTemplate.queryForList("""
                select distinct rd.dept_id
                  from sys_role_dept rd
                 inner join sys_user_role ur on ur.role_id = rd.role_id
                 inner join sys_role r on r.id = rd.role_id
                 where ur.user_id = ?
                   and coalesce(r.data_scope, '') = ?
                   and rd.deleted = 0
                   and ur.deleted = 0
                   and r.deleted = 0
                   and r.enable = 1
                """, Long.class, userId, DataScopeType.DEPT_SETS.getCode());
        return new HashSet<>(deptIds);
    }

    private static Long requiredLong(ResultSet rs, String column) throws SQLException {
        return rs.getLong(column);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
