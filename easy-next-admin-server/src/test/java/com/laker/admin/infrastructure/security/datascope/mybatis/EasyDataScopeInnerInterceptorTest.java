package com.laker.admin.infrastructure.security.datascope.mybatis;

import com.laker.admin.infrastructure.security.datascope.annotation.DataScope;
import com.laker.admin.infrastructure.security.datascope.annotation.DataScopeColumns;
import com.laker.admin.infrastructure.security.datascope.annotation.DataScopeMapperMethods;
import com.laker.admin.infrastructure.security.datascope.context.EasyDataScopeContext;
import com.laker.admin.infrastructure.security.datascope.model.DataScopeCondition;
import com.laker.admin.infrastructure.security.datascope.model.DataScopeType;
import com.laker.admin.module.system.mapper.SysUserMapper;
import com.laker.admin.module.workflow.mapper.WfTaskMapper;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EasyDataScopeInnerInterceptorTest {

    private final EasyDataScopeInnerInterceptor interceptor = new EasyDataScopeInnerInterceptor(DataScopeCondition::denied);

    @Test
    void shouldApplyClassScopeOnlyToConfiguredMethods() {
        Optional<EasyDataScopeInnerInterceptor.DataScopeTarget> target = interceptor.resolveTarget(statementId(UserMapper.class, "selectPage"));

        assertThat(target).isPresent();
        assertThat(target.get().annotation().deptColumn()).isEqualTo(DataScopeColumns.DEPT_ID);
        assertThat(target.get().annotation().selfColumn()).isEqualTo(DataScopeColumns.USER_ID);
        assertThat(interceptor.resolveTarget(statementId(UserMapper.class, "loadProfile"))).isEmpty();
    }

    @Test
    void shouldUseDefaultProjectedDepartmentColumn() {
        Optional<EasyDataScopeInnerInterceptor.DataScopeTarget> target = interceptor.resolveTarget(statementId(DefaultColumnMapper.class, "selectPage"));

        assertThat(target).isPresent();
        assertThat(target.get().annotation().deptColumn()).isEqualTo(DataScopeColumns.DEPT_ID);
        assertThat(target.get().annotation().selfColumn()).isEqualTo(DataScopeColumns.USER_ID);
    }

    @Test
    void shouldApplyClassScopeToMultipleConfiguredMapperMethods() {
        Optional<EasyDataScopeInnerInterceptor.DataScopeTarget> pageTarget = interceptor.resolveTarget(statementId(DeptMapper.class, "selectPage"));
        Optional<EasyDataScopeInnerInterceptor.DataScopeTarget> listTarget = interceptor.resolveTarget(statementId(DeptMapper.class, "selectList"));

        assertThat(pageTarget).isPresent();
        assertThat(listTarget).isPresent();
        assertThat(pageTarget.get().annotation().selfColumn()).isEqualTo(DataScopeColumns.DEPT_ID);
        assertThat(listTarget.get().annotation().selfColumn()).isEqualTo(DataScopeColumns.DEPT_ID);
        assertThat(interceptor.resolveTarget(statementId(DeptMapper.class, "selectBatchIds"))).isEmpty();
    }

    @Test
    void shouldApplyUserPageScopeToMybatisPlusSelectList() {
        Optional<EasyDataScopeInnerInterceptor.DataScopeTarget> target =
                interceptor.resolveTarget(statementId(SysUserMapper.class, "selectList"));

        assertThat(target).isPresent();
        assertThat(target.get().annotation().deptColumn()).isEqualTo(DataScopeColumns.DB_DEPT_ID);
        assertThat(target.get().annotation().selfColumn()).isEqualTo(DataScopeColumns.USER_ID);
    }

    @Test
    void shouldApplyWorkflowTaskScopeToRuntimeTaskQueries() {
        Optional<EasyDataScopeInnerInterceptor.DataScopeTarget> target =
                interceptor.resolveTarget(statementId(WfTaskMapper.class, "selectPage"));

        assertThat(target).isPresent();
        assertThat(target.get().annotation().deptColumn()).isEqualTo(DataScopeColumns.DB_ASSIGNEE_DEPT_ID);
        assertThat(target.get().annotation().selfColumn()).isEqualTo(DataScopeColumns.DB_ASSIGNEE_ID);
    }

    @Test
    void shouldPreferMethodScopeOverClassScope() {
        Optional<EasyDataScopeInnerInterceptor.DataScopeTarget> target = interceptor.resolveTarget(statementId(CustomMapper.class, "search"));

        assertThat(target).isPresent();
        assertThat(target.get().annotation().deptColumn()).isEqualTo("org_id");
        assertThat(target.get().annotation().selfColumn()).isEqualTo("owner_id");
    }

    @Test
    void shouldRestoreIgnoredContextAfterInternalQuery() {
        assertThat(EasyDataScopeContext.ignored()).isFalse();

        String result = EasyDataScopeContext.ignore(() -> {
            assertThat(EasyDataScopeContext.ignored()).isTrue();
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(EasyDataScopeContext.ignored()).isFalse();
    }

    @Test
    void shouldRewriteAnnotatedSelectSqlBeforeQuery() throws Exception {
        EasyDataScopeInnerInterceptor rewritingInterceptor = new EasyDataScopeInnerInterceptor(() ->
                new DataScopeCondition(DataScopeType.DEPT, 9L, 10L, Set.of(10L)));
        BoundSql boundSql = boundSql(UserMapper.class, "selectPage", "select * from sys_user where deleted = 0");

        rewritingInterceptor.beforeQuery(null, statement(UserMapper.class, "selectPage"), null, RowBounds.DEFAULT, null, boundSql);

        assertThat(boundSql.getSql()).isEqualTo("SELECT * FROM (select * from sys_user where deleted = 0) ea_ds WHERE ea_ds.deptId = 10");
    }

    @Test
    void shouldSkipRewriteWhenContextIsIgnored() {
        EasyDataScopeInnerInterceptor rewritingInterceptor = new EasyDataScopeInnerInterceptor(DataScopeCondition::denied);
        BoundSql boundSql = boundSql(UserMapper.class, "selectPage", "select * from sys_user");

        EasyDataScopeContext.ignore(() -> {
            try {
                rewritingInterceptor.beforeQuery(null, statement(UserMapper.class, "selectPage"), null, RowBounds.DEFAULT, null, boundSql);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            return null;
        });

        assertThat(boundSql.getSql()).isEqualTo("select * from sys_user");
    }

    private String statementId(Class<?> mapperClass, String methodName) {
        return mapperClass.getName() + "." + methodName;
    }

    private MappedStatement statement(Class<?> mapperClass, String methodName) {
        Configuration configuration = new Configuration();
        StaticSqlSource sqlSource = new StaticSqlSource(configuration, "select 1");
        return new MappedStatement.Builder(configuration, statementId(mapperClass, methodName), sqlSource, SqlCommandType.SELECT).build();
    }

    private BoundSql boundSql(Class<?> mapperClass, String methodName, String sql) {
        Configuration configuration = new Configuration();
        StaticSqlSource sqlSource = new StaticSqlSource(configuration, sql);
        MappedStatement statement = new MappedStatement.Builder(configuration, statementId(mapperClass, methodName), sqlSource, SqlCommandType.SELECT).build();
        return statement.getBoundSql(null);
    }

    @DataScope(methods = DataScopeMapperMethods.SELECT_PAGE, deptColumn = DataScopeColumns.DEPT_ID, selfColumn = DataScopeColumns.USER_ID)
    interface UserMapper {
    }

    @DataScope(methods = DataScopeMapperMethods.SELECT_PAGE, selfColumn = DataScopeColumns.USER_ID)
    interface DefaultColumnMapper {
    }

    @DataScope(
            methods = {DataScopeMapperMethods.SELECT_LIST, DataScopeMapperMethods.SELECT_PAGE},
            deptColumn = DataScopeColumns.DEPT_ID,
            selfColumn = DataScopeColumns.DEPT_ID
    )
    interface DeptMapper {
    }

    @DataScope(methods = "search", deptColumn = DataScopeColumns.DB_DEPT_ID, selfColumn = "user_id")
    interface CustomMapper {
        @DataScope(deptColumn = "org_id", selfColumn = "owner_id")
        void search();
    }
}
