package com.laker.admin.module.system.service;

import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionSeedSqlTest {
    private static final Path MYSQL_MIGRATION_DIR = Path.of("src/main/resources/db/migration");
    private static final Path H2_MIGRATION_DIR = Path.of("src/test/resources/db/migration-h2");
    private static final Path WEB_ROOT = Path.of("../easy-next-admin-web");
    private static final Path FRONTEND_PERMISSION_CODES = WEB_ROOT.resolve("src/permissions/codes.ts");
    private static final Pattern COMPONENT_PATH_PATTERN = Pattern.compile("'(@/views/[^']+\\.vue)'");

    @Test
    void builtInPermissionCodesShouldExistInBackendFrontendAndSeedSql() throws Exception {
        String mysqlSql = migrationSql(MYSQL_MIGRATION_DIR);
        String h2Sql = migrationSql(H2_MIGRATION_DIR);
        String frontendPermissionCodes = Files.readString(FRONTEND_PERMISSION_CODES);

        for (String permissionCode : builtInPermissionCodes()) {
            assertThat(mysqlSql)
                    .as("MySQL V1 should seed permission code %s", permissionCode)
                    .contains(permissionCode);
            assertThat(h2Sql)
                    .as("H2 test seed should seed permission code %s", permissionCode)
                    .contains(permissionCode);
            assertThat(frontendPermissionCodes)
                    .as("frontend permission constants should expose %s", permissionCode)
                    .contains(permissionCode);
        }
    }

    @Test
    void menuComponentPathsShouldBeDatabaseDrivenAndResolvableByFrontendRouter() throws Exception {
        String mysqlSql = migrationSql(MYSQL_MIGRATION_DIR);
        String h2Sql = migrationSql(H2_MIGRATION_DIR);
        Set<String> componentPaths = componentPaths(mysqlSql);

        assertThat(componentPaths)
                .contains(
                        "@/views/dashboard/WorkspaceView.vue",
                        "@/views/system/UserView.vue",
                        "@/views/system/RoleView.vue",
                        "@/views/system/MenuView.vue",
                        "@/views/system/DepartmentView.vue",
                        "@/views/system/FileCenterView.vue",
                        "@/views/system/BusinessNumberRuleView.vue",
                        "@/views/report/EnterpriseReportView.vue",
                        "@/views/batch/BatchTaskView.vue",
                        "@/views/monitor/CacheListView.vue",
                        "@/views/workflow/WorkflowStartView.vue",
                        "@/views/workflow/WorkflowTaskCenterView.vue"
                );
        assertThat(WEB_ROOT.resolve("src/capabilities")).doesNotExist();

        for (String componentPath : componentPaths) {
            assertThat(h2Sql)
                    .as("H2 menu seed should keep component path %s", componentPath)
                    .contains(componentPath);
            Path vueFile = WEB_ROOT.resolve(componentPath.replace("@/", "src/"));
            assertThat(vueFile)
                    .as("dynamic menu component should exist: %s", componentPath)
                    .exists();
        }
    }

    @Test
    void roleSeedPermissionsShouldUsePermissionCodesInsteadOfMenuIdLists() throws Exception {
        String mysqlSql = migrationSql(MYSQL_MIGRATION_DIR);
        String h2Sql = migrationSql(H2_MIGRATION_DIR);

        assertThat(mysqlSql)
                .contains("permission_resource.`permission_code` IN")
                .contains("child.`permission_code` IN")
                .doesNotContain("AND permission_resource.`id` IN (");
        assertThat(h2Sql)
                .contains("permission_resource.permission_code IN")
                .contains("child.permission_code IN")
                .doesNotContain("AND permission_resource.id IN (");

        for (String roleCode : Set.of("dept_manager", "staff", "ops", "auditor")) {
            assertRoleBlockContains(mysqlSql, roleCode, "workflow:instance:start");
            assertRoleBlockContains(h2Sql, roleCode, "workflow:instance:start");
            assertRoleBlockDoesNotContain(mysqlSql, roleCode, "workflow:instance:manage");
            assertRoleBlockDoesNotContain(h2Sql, roleCode, "workflow:instance:manage");
            assertRoleBlockDoesNotContain(mysqlSql, roleCode, "workflow:instance:terminate");
            assertRoleBlockDoesNotContain(h2Sql, roleCode, "workflow:instance:terminate");
        }
        for (String roleCode : Set.of("dept_manager", "auditor")) {
            assertRoleBlockContains(mysqlSql, roleCode, "report:view");
            assertRoleBlockContains(h2Sql, roleCode, "report:view");
        }
        for (String roleCode : Set.of("staff", "ops")) {
            assertRoleBlockDoesNotContain(mysqlSql, roleCode, "report:view");
            assertRoleBlockDoesNotContain(h2Sql, roleCode, "report:view");
        }
        assertThat(mysqlSql)
                .contains("DELETE /api/monitor/cache/{cacheName}/entries");
        assertThat(h2Sql)
                .contains("DELETE /api/monitor/cache/{cacheName}/entries");
    }

    private static Set<String> builtInPermissionCodes() {
        Set<String> permissionCodes = new LinkedHashSet<>();
        for (Class<?> nestedClass : EasyPermissions.class.getDeclaredClasses()) {
            for (var field : nestedClass.getDeclaredFields()) {
                if (field.getType() == String.class && Modifier.isStatic(field.getModifiers())) {
                    try {
                        permissionCodes.add((String) field.get(null));
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException("Cannot read permission constant " + field.getName(), e);
                    }
                }
            }
        }
        return permissionCodes;
    }

    private static String migrationSql(Path dir) throws Exception {
        try (var paths = Files.list(dir)) {
            return paths
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (Exception e) {
                            throw new IllegalStateException("Cannot read migration " + path, e);
                        }
                    })
                    .collect(Collectors.joining("\n"));
        }
    }

    private static Set<String> componentPaths(String sql) {
        Set<String> paths = new LinkedHashSet<>();
        Matcher matcher = COMPONENT_PATH_PATTERN.matcher(sql);
        while (matcher.find()) {
            paths.add(matcher.group(1));
        }
        return paths;
    }

    private static void assertRoleBlockContains(String sql, String roleCode, String permissionCode) {
        String block = roleSeedBlock(sql, roleCode);
        assertThat(block)
                .as("role %s should include %s", roleCode, permissionCode)
                .contains(permissionCode);
    }

    private static void assertRoleBlockDoesNotContain(String sql, String roleCode, String permissionCode) {
        String block = roleSeedBlock(sql, roleCode);
        assertThat(block)
                .as("role %s should not include %s", roleCode, permissionCode)
                .doesNotContain(permissionCode);
    }

    private static String roleSeedBlock(String sql, String roleCode) {
        int roleIndex = sql.indexOf("role_code" + (sql.contains("`role_code`") ? "` = '" : " = '") + roleCode + "'");
        if (roleIndex < 0) {
            roleIndex = sql.indexOf("role_code = '" + roleCode + "'");
        }
        assertThat(roleIndex)
                .as("role seed block should exist for %s", roleCode)
                .isGreaterThanOrEqualTo(0);
        int blockStart = Math.max(0, sql.lastIndexOf("INSERT INTO", roleIndex));
        int nextBlock = sql.indexOf("INSERT INTO", roleIndex + 1);
        return sql.substring(blockStart, nextBlock > 0 ? nextBlock : sql.length());
    }
}
