package com.laker.admin.module.monitor;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeDiagnosisMigrationTest {
    private static final Path MYSQL_V3 = Path.of("src/main/resources/db/migration/V3__add_runtime_diagnosis.sql");
    private static final String H2_MIGRATIONS = "classpath:db/migration-h2";
    private static final long RUNTIME_DIAGNOSIS_MENU_ID = 202610140104000205L;
    private static final long ADVANCED_DIAGNOSIS_MENU_ID = 202610140104000208L;

    @Test
    void publishedMysqlV3ChecksumMustRemainCompatible() throws Exception {
        CRC32 checksum = new CRC32();
        try (BufferedReader reader = Files.newBufferedReader(MYSQL_V3, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                checksum.update(line.getBytes(StandardCharsets.UTF_8));
            }
        }

        assertThat((int) checksum.getValue()).isEqualTo(1972982906);
    }

    @Test
    void upgradingPublishedV3ShouldMatchFreshV5Database() throws Exception {
        String upgradeUrl = databaseUrl("upgrade");
        migrate(upgradeUrl, MigrationVersion.fromVersion("3"));

        DiagnosisSeedSnapshot publishedV3 = snapshot(upgradeUrl);
        assertThat(publishedV3.menus())
                .containsExactly(new MenuSeed(
                        RUNTIME_DIAGNOSIS_MENU_ID,
                        202604280104000200L,
                        "monitor:diagnosis:view",
                        "@/views/monitor/RuntimeDiagnosisView.vue",
                        "查看 Metrics、Events、Slow、Errors 和可关联的 Trace 调用树。"
                ));
        assertThat(publishedV3.rolePermissions())
                .containsExactly(
                        new RolePermissionSeed("admin", "monitor:diagnosis:view"),
                        new RolePermissionSeed("ops", "monitor:diagnosis:view")
                );

        migrate(upgradeUrl, null);
        DiagnosisSeedSnapshot upgraded = snapshot(upgradeUrl);

        String freshUrl = databaseUrl("fresh");
        migrate(freshUrl, null);
        DiagnosisSeedSnapshot fresh = snapshot(freshUrl);

        assertThat(upgraded).isEqualTo(fresh);
        assertThat(upgraded.menus())
                .extracting(MenuSeed::id)
                .containsExactly(
                        202610140104000205L,
                        202610140104000206L,
                        202610140104000207L,
                        202610140104000208L,
                        202610140104000209L
                );
        assertThat(upgraded.menus())
                .filteredOn(menu -> menu.id() == 202610140104000206L || menu.id() == 202610140104000207L
                        || menu.id() == 202610140104000209L)
                .allMatch(menu -> menu.pid() == ADVANCED_DIAGNOSIS_MENU_ID);
        assertThat(upgraded.menus())
                .filteredOn(menu -> menu.id() == RUNTIME_DIAGNOSIS_MENU_ID)
                .extracting(MenuSeed::remark)
                .containsExactly("查看 Metrics、Slow、Errors，并从慢调用或错误打开关联 Trace。");
        assertThat(upgraded.rolePermissions()).hasSize(10);
        assertThat(upgraded.rolePermissions())
                .extracting(RolePermissionSeed::permissionCode)
                .containsOnly(
                        "monitor:diagnosis:view",
                        "monitor:diagnosis:capture",
                        "monitor:diagnosis:jfr-download",
                        "monitor:diagnosis:advanced-view",
                        "monitor:diagnosis:jfr-control"
                );
    }

    private static void migrate(String url, MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(url, "sa", "")
                .locations(H2_MIGRATIONS);
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private static String databaseUrl(String name) {
        return "jdbc:h2:mem:runtime_diagnosis_" + name + "_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";
    }

    private static DiagnosisSeedSnapshot snapshot(String url) throws SQLException {
        List<MenuSeed> menus = new ArrayList<>();
        List<RolePermissionSeed> rolePermissions = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("""
                    SELECT id, pid, permission_code, component_path, remark
                    FROM sys_menu
                    WHERE id BETWEEN 202610140104000205 AND 202610140104000209
                    ORDER BY id
                    """)) {
                while (rows.next()) {
                    menus.add(new MenuSeed(
                            rows.getLong("id"),
                            rows.getLong("pid"),
                            rows.getString("permission_code"),
                            rows.getString("component_path"),
                            rows.getString("remark")
                    ));
                }
            }
            try (ResultSet rows = statement.executeQuery("""
                    SELECT role_table.role_code, menu.permission_code
                    FROM sys_role_permission role_permission
                    JOIN sys_role role_table ON role_table.id = role_permission.role_id
                    JOIN sys_menu menu ON menu.id = role_permission.permission_resource_id
                    WHERE role_table.role_code IN ('admin', 'ops')
                      AND menu.id BETWEEN 202610140104000205 AND 202610140104000209
                      AND role_permission.deleted = 0
                    ORDER BY role_table.role_code, menu.permission_code
                    """)) {
                while (rows.next()) {
                    rolePermissions.add(new RolePermissionSeed(
                            rows.getString("role_code"),
                            rows.getString("permission_code")
                    ));
                }
            }
        }
        return new DiagnosisSeedSnapshot(menus, rolePermissions);
    }

    private record DiagnosisSeedSnapshot(
            List<MenuSeed> menus,
            List<RolePermissionSeed> rolePermissions
    ) {
    }

    private record MenuSeed(
            long id,
            long pid,
            String permissionCode,
            String componentPath,
            String remark
    ) {
    }

    private record RolePermissionSeed(
            String roleCode,
            String permissionCode
    ) {
    }
}
