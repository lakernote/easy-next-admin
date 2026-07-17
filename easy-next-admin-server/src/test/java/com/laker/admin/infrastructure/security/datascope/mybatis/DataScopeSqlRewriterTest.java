package com.laker.admin.infrastructure.security.datascope.mybatis;

import com.laker.admin.infrastructure.security.datascope.annotation.DataScopeColumns;
import com.laker.admin.infrastructure.security.datascope.model.DataScopeCondition;
import com.laker.admin.infrastructure.security.datascope.model.DataScopeType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataScopeSqlRewriterTest {

    private final DataScopeSqlRewriter rewriter = new DataScopeSqlRewriter();

    @Test
    void shouldKeepOriginalSqlWhenScopeIsAll() {
        String sql = "select user_id, dept_id from sys_user where deleted = 0";

        String scopedSql = rewriter.rewrite(sql, "dept_id", "user_id", DataScopeCondition.all(7L, 2L, Set.of(2L, 3L)));

        assertThat(scopedSql).isEqualTo(sql);
    }

    @Test
    void shouldDenyQueryWhenScopeIsMissing() {
        String scopedSql = rewriter.rewrite("select * from sys_user", "dept_id", "user_id", DataScopeCondition.denied());

        assertThat(scopedSql).isEqualTo("SELECT * FROM (select * from sys_user) ea_ds WHERE 1 = 0");
    }

    @Test
    void shouldFilterByDepartmentSet() {
        DataScopeCondition scope = new DataScopeCondition(DataScopeType.DEPT_SETS, 8L, 10L, Set.of(20L, 10L));

        String scopedSql = rewriter.rewrite("select * from sys_user", "deptId", "userId", scope);

        assertThat(scopedSql).isEqualTo("SELECT * FROM (select * from sys_user) ea_ds WHERE ea_ds.deptId IN (10,20)");
    }

    @Test
    void shouldFilterBySelfColumn() {
        DataScopeCondition scope = new DataScopeCondition(DataScopeType.SELF, 8L, 10L, Set.of(10L));

        String scopedSql = rewriter.rewrite("select * from sys_user", DataScopeColumns.DEPT_ID, DataScopeColumns.USER_ID, scope);

        assertThat(scopedSql).isEqualTo("SELECT * FROM (select * from sys_user) ea_ds WHERE ea_ds.userId = 8");
    }

    @Test
    void shouldDenySelfScopeWhenSelfColumnIsBlank() {
        DataScopeCondition scope = new DataScopeCondition(DataScopeType.SELF, 8L, 10L, Set.of(10L));

        String scopedSql = rewriter.rewrite("select * from sys_user", DataScopeColumns.DEPT_ID, "", scope);

        assertThat(scopedSql).isEqualTo("SELECT * FROM (select * from sys_user) ea_ds WHERE 1 = 0");
    }

    @Test
    void shouldFilterByProjectedAliasColumnFromMybatisPlusSelect() {
        DataScopeCondition scope = new DataScopeCondition(DataScopeType.DEPT_SETS, 8L, 10L, Set.of(202604280103000103L));
        String sql = "SELECT id AS deptId,dept_name FROM sys_dept WHERE deleted=0 ORDER BY sort ASC";

        String scopedSql = rewriter.rewrite(sql, DataScopeColumns.DEPT_ID, DataScopeColumns.DEPT_ID, scope);

        assertThat(scopedSql).isEqualTo("SELECT * FROM (SELECT id AS deptId,dept_name FROM sys_dept WHERE deleted=0 ORDER BY sort ASC) ea_ds WHERE ea_ds.deptId IN (202604280103000103)");
    }

    @Test
    void shouldRejectUnsafeColumnName() {
        DataScopeCondition scope = new DataScopeCondition(DataScopeType.DEPT, 8L, 10L, Set.of(10L));

        assertThatThrownBy(() -> rewriter.rewrite("select * from sys_user", "dept_id;drop table sys_user", "user_id", scope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid data scope column");
    }
}
