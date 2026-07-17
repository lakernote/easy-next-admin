package com.laker.admin.module.system.mapper;

import com.laker.admin.module.system.dto.RoleUserCount;
import com.laker.admin.module.system.dto.UserRoleBinding;
import com.laker.admin.module.system.entity.SysMenuResource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class SystemRelationMapperIntegrationTest {
    private static final long ADMIN_USER_ID = 202604280101000001L;
    private static final long ADMIN_ROLE_ID = 202604280102000005L;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;
    @Autowired
    private SysRolePermissionMapper sysRolePermissionMapper;
    @Autowired
    private SysMenuMapper sysMenuMapper;

    @Test
    void shouldQueryUserRoleBindingsAndRoleUserCountsInBatch() {
        List<UserRoleBinding> bindings = sysUserRoleMapper.selectRoleBindingsByUserIds(List.of(ADMIN_USER_ID));
        List<RoleUserCount> counts = sysUserRoleMapper.countUsersByRoleIds(List.of(ADMIN_ROLE_ID));

        assertThat(bindings)
                .extracting(UserRoleBinding::getRoleId)
                .contains(ADMIN_ROLE_ID);
        assertThat(sysUserRoleMapper.selectUserIdsByRoleIds(List.of(ADMIN_ROLE_ID)))
                .contains(ADMIN_USER_ID);
        assertThat(counts)
                .anySatisfy(count -> {
                    assertThat(count.getRoleId()).isEqualTo(ADMIN_ROLE_ID);
                    assertThat(count.getUserCount()).isGreaterThanOrEqualTo(1);
                });
    }

    @Test
    void shouldResolvePermissionCodesAndMenusWithJoinQueries() {
        List<String> permissionCodes = sysRolePermissionMapper.selectPermissionCodesByRoleId(ADMIN_ROLE_ID);
        List<SysMenuResource> menus = sysMenuMapper.findEnabledByUserId(ADMIN_USER_ID);

        assertThat(permissionCodes).contains("dashboard:view", "sys:user:list");
        assertThat(menus)
                .extracting(SysMenuResource::getPermissionCode)
                .contains("dashboard:view", "sys:user:list");
    }
}
