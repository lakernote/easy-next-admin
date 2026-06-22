package com.laker.admin.infrastructure.security.datascope.resolver;

import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.config.cache.EasyCacheConfig;
import com.laker.admin.infrastructure.security.datascope.model.DataScopeCondition;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.infrastructure.security.datascope.model.DataScopeType;
import com.laker.admin.infrastructure.security.datascope.repository.CachedDataScopeMetadataRepository;
import com.laker.admin.infrastructure.security.datascope.repository.DataScopeMetadataRepository;
import com.laker.admin.infrastructure.security.datascope.repository.JdbcDataScopeMetadataRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentUserDataScopeResolverTest {

    private JdbcTemplate jdbcTemplate;
    private CacheManager cacheManager;
    private AnnotationConfigApplicationContext context;
    private CurrentUserDataScopeResolver resolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:data-scope;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbcTemplate.execute("drop table if exists sys_dept");
        jdbcTemplate.execute("drop table if exists sys_role_dept");
        jdbcTemplate.execute("drop table if exists sys_user_role");
        jdbcTemplate.execute("drop table if exists sys_role");
        jdbcTemplate.execute("create table sys_dept (id bigint primary key, pid bigint, status int, deleted int)");
        jdbcTemplate.execute("create table sys_role (id bigint primary key, data_scope varchar(40), enable int, deleted int)");
        jdbcTemplate.execute("create table sys_user_role (id bigint primary key, user_id bigint, role_id bigint, deleted int)");
        jdbcTemplate.execute("create table sys_role_dept (id bigint primary key, role_id bigint, dept_id bigint, deleted int)");
        jdbcTemplate.batchUpdate(
                "insert into sys_dept (id, pid, status, deleted) values (?, ?, ?, ?)",
                List.of(
                        new Object[]{10L, 1L, 1, 0},
                        new Object[]{11L, 10L, 1, 0},
                        new Object[]{12L, 11L, 1, 0},
                        new Object[]{13L, 10L, 1, 1},
                        new Object[]{14L, 10L, 0, 0},
                        new Object[]{20L, 1L, 1, 0}
                )
        );
        context = new AnnotationConfigApplicationContext();
        context.register(TestCachingConfig.class);
        context.registerBean(CacheManager.class, () -> new EasyCacheConfig().cacheManager(null));
        context.registerBean(JdbcDataScopeMetadataRepository.class, () -> new JdbcDataScopeMetadataRepository(jdbcTemplate));
        context.registerBean(CachedDataScopeMetadataRepository.class);
        context.refresh();
        cacheManager = context.getBean(CacheManager.class);
        resolver = new CurrentUserDataScopeResolver(context.getBean("cachedDataScopeMetadataRepository", DataScopeMetadataRepository.class));
    }

    @AfterEach
    void tearDown() {
        EasySecurityContext.clear();
        if (context != null) {
            context.close();
        }
    }

    @Test
    void shouldResolveDepartmentChildrenFromCurrentDatabaseState() {
        AuthPrincipal principal = AuthPrincipal.builder()
                .userId(9L)
                .deptId(10L)
                .deptIds(Set.of(10L))
                .dataScopes(List.of(DataScopeType.DEPT_AND_CHILDREN))
                .build();
        EasySecurityContext.setPrincipal(principal);

        DataScopeCondition condition = resolver.resolveCurrentUserScope();

        assertThat(condition.scopeType()).isEqualTo(DataScopeType.DEPT_AND_CHILDREN);
        assertThat(condition.deptIds()).containsExactlyInAnyOrder(10L, 11L, 12L);
    }

    @Test
    void shouldReuseCachedDepartmentTreeUntilEvicted() {
        AuthPrincipal principal = AuthPrincipal.builder()
                .userId(9L)
                .deptId(10L)
                .deptIds(Set.of(10L))
                .dataScopes(List.of(DataScopeType.DEPT_AND_CHILDREN))
                .build();
        EasySecurityContext.setPrincipal(principal);

        DataScopeCondition firstCondition = resolver.resolveCurrentUserScope();
        jdbcTemplate.update("insert into sys_dept (id, pid, status, deleted) values (15, 10, 1, 0)");
        DataScopeCondition cachedCondition = resolver.resolveCurrentUserScope();
        cacheManager.getCache(EasyCacheConfig.CACHE_DATA_SCOPE_DEPT_TREE).evict("activeDeptNodes");
        DataScopeCondition refreshedCondition = resolver.resolveCurrentUserScope();

        assertThat(firstCondition.deptIds()).containsExactlyInAnyOrder(10L, 11L, 12L);
        assertThat(cachedCondition.deptIds()).containsExactlyInAnyOrder(10L, 11L, 12L);
        assertThat(refreshedCondition.deptIds()).containsExactlyInAnyOrder(10L, 11L, 12L, 15L);
    }

    @Test
    void shouldResolveCustomDepartmentsFromRoleDepartmentBindings() {
        jdbcTemplate.update("insert into sys_role (id, data_scope, enable, deleted) values (100, 'DEPT_SETS', 1, 0)");
        jdbcTemplate.update("insert into sys_user_role (id, user_id, role_id, deleted) values (1, 9, 100, 0)");
        jdbcTemplate.update("insert into sys_role_dept (id, role_id, dept_id, deleted) values (1, 100, 20, 0)");
        jdbcTemplate.update("insert into sys_role_dept (id, role_id, dept_id, deleted) values (2, 100, 11, 0)");
        AuthPrincipal principal = AuthPrincipal.builder()
                .userId(9L)
                .deptId(10L)
                .deptIds(Set.of(10L))
                .dataScopes(List.of(DataScopeType.DEPT_SETS))
                .build();
        EasySecurityContext.setPrincipal(principal);

        DataScopeCondition condition = resolver.resolveCurrentUserScope();

        assertThat(condition.scopeType()).isEqualTo(DataScopeType.DEPT_SETS);
        assertThat(condition.deptIds()).containsExactlyInAnyOrder(11L, 20L);
    }

    @Test
    void shouldIgnoreDisabledRoleDepartmentBindings() {
        jdbcTemplate.update("insert into sys_role (id, data_scope, enable, deleted) values (100, 'DEPT_SETS', 1, 0)");
        jdbcTemplate.update("insert into sys_role (id, data_scope, enable, deleted) values (101, 'DEPT_SETS', 0, 0)");
        jdbcTemplate.update("insert into sys_user_role (id, user_id, role_id, deleted) values (1, 9, 100, 0)");
        jdbcTemplate.update("insert into sys_user_role (id, user_id, role_id, deleted) values (2, 9, 101, 0)");
        jdbcTemplate.update("insert into sys_role_dept (id, role_id, dept_id, deleted) values (1, 100, 20, 0)");
        jdbcTemplate.update("insert into sys_role_dept (id, role_id, dept_id, deleted) values (2, 101, 11, 0)");
        AuthPrincipal principal = AuthPrincipal.builder()
                .userId(9L)
                .deptId(10L)
                .deptIds(Set.of(10L))
                .dataScopes(List.of(DataScopeType.DEPT_SETS))
                .build();
        EasySecurityContext.setPrincipal(principal);

        DataScopeCondition condition = resolver.resolveCurrentUserScope();

        assertThat(condition.deptIds()).containsExactly(20L);
    }

    @Test
    void shouldReuseCachedCustomDepartmentsUntilUserCacheIsEvicted() {
        jdbcTemplate.update("insert into sys_role (id, data_scope, enable, deleted) values (100, 'DEPT_SETS', 1, 0)");
        jdbcTemplate.update("insert into sys_user_role (id, user_id, role_id, deleted) values (1, 9, 100, 0)");
        jdbcTemplate.update("insert into sys_role_dept (id, role_id, dept_id, deleted) values (1, 100, 20, 0)");
        AuthPrincipal principal = AuthPrincipal.builder()
                .userId(9L)
                .deptId(10L)
                .deptIds(Set.of(10L))
                .dataScopes(List.of(DataScopeType.DEPT_SETS))
                .build();
        EasySecurityContext.setPrincipal(principal);

        DataScopeCondition firstCondition = resolver.resolveCurrentUserScope();
        jdbcTemplate.update("insert into sys_role_dept (id, role_id, dept_id, deleted) values (2, 100, 11, 0)");
        DataScopeCondition cachedCondition = resolver.resolveCurrentUserScope();
        cacheManager.getCache(EasyCacheConfig.CACHE_DATA_SCOPE_CUSTOM_DEPT_IDS).evict(9L);
        DataScopeCondition refreshedCondition = resolver.resolveCurrentUserScope();

        assertThat(firstCondition.deptIds()).containsExactlyInAnyOrder(20L);
        assertThat(cachedCondition.deptIds()).containsExactlyInAnyOrder(20L);
        assertThat(refreshedCondition.deptIds()).containsExactlyInAnyOrder(11L, 20L);
    }

    @Test
    void shouldUnionDepartmentScopesWhenUserHasMultipleRoles() {
        jdbcTemplate.update("insert into sys_role (id, data_scope, enable, deleted) values (100, 'DEPT_SETS', 1, 0)");
        jdbcTemplate.update("insert into sys_user_role (id, user_id, role_id, deleted) values (1, 9, 100, 0)");
        jdbcTemplate.update("insert into sys_role_dept (id, role_id, dept_id, deleted) values (1, 100, 20, 0)");
        AuthPrincipal principal = AuthPrincipal.builder()
                .userId(9L)
                .deptId(10L)
                .deptIds(Set.of(10L))
                .dataScopes(List.of(DataScopeType.DEPT_SETS, DataScopeType.DEPT_AND_CHILDREN))
                .build();
        EasySecurityContext.setPrincipal(principal);

        DataScopeCondition condition = resolver.resolveCurrentUserScope();

        assertThat(condition.scopeType()).isEqualTo(DataScopeType.DEPT_SETS);
        assertThat(condition.deptIds()).containsExactlyInAnyOrder(10L, 11L, 12L, 20L);
    }

    @Test
    void shouldDefaultToSelfWhenNoDataScopeIsConfigured() {
        AuthPrincipal principal = AuthPrincipal.builder()
                .userId(9L)
                .deptId(10L)
                .deptIds(Set.of(10L))
                .dataScopes(List.of())
                .build();
        EasySecurityContext.setPrincipal(principal);

        DataScopeCondition condition = resolver.resolveCurrentUserScope();

        assertThat(condition.scopeType()).isEqualTo(DataScopeType.SELF);
        assertThat(condition.userId()).isEqualTo(9L);
        assertThat(condition.deptId()).isEqualTo(10L);
    }

    @Test
    void shouldApplyRoleDataScopeStrategyWithoutResourceCode() {
        AuthPrincipal principal = AuthPrincipal.builder()
                .userId(9L)
                .deptId(10L)
                .deptIds(Set.of(10L))
                .dataScopes(List.of(DataScopeType.ALL))
                .build();
        EasySecurityContext.setPrincipal(principal);

        DataScopeCondition condition = resolver.resolveCurrentUserScope();

        assertThat(condition.scopeType()).isEqualTo(DataScopeType.ALL);
    }

    @Configuration
    @EnableCaching
    static class TestCachingConfig {
    }
}
