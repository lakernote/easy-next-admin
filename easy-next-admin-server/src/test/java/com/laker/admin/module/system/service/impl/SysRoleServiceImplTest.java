package com.laker.admin.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.infrastructure.security.service.PermissionVersionService;
import com.laker.admin.infrastructure.security.datascope.policy.DataScopeAssignmentPolicy;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.infrastructure.security.datascope.model.DataScopeType;
import com.laker.admin.module.audit.service.SensitiveAuditService;
import com.laker.admin.module.system.dto.RolePermissionDto;
import com.laker.admin.module.system.entity.SysPower;
import com.laker.admin.module.system.entity.SysDept;
import com.laker.admin.module.system.entity.SysRole;
import com.laker.admin.module.system.entity.SysRoleDept;
import com.laker.admin.module.system.entity.SysRolePower;
import com.laker.admin.module.system.service.ISysDeptService;
import com.laker.admin.module.system.service.ISysMenuService;
import com.laker.admin.module.system.service.ISysRoleDeptService;
import com.laker.admin.module.system.service.ISysRolePowerService;
import com.laker.admin.module.system.service.ISysUserRoleService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysRoleServiceImplTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void saveRolePermissionsKeepsNavigationAncestorsForSelectedPages() {
        ISysMenuService sysMenuService = mock(ISysMenuService.class);
        ISysRolePowerService sysRolePowerService = mock(ISysRolePowerService.class);
        ISysRoleDeptService sysRoleDeptService = mock(ISysRoleDeptService.class);
        ISysUserRoleService sysUserRoleService = mock(ISysUserRoleService.class);
        PermissionVersionService permissionVersionService = mock(PermissionVersionService.class);
        SysRoleServiceImpl service = new TestableSysRoleService(
                sysMenuService,
                sysRolePowerService,
                sysRoleDeptService,
                sysUserRoleService,
                permissionVersionService,
                mock(SensitiveAuditService.class));
        Long roleId = 200L;

        List<SysPower> selectedPages = List.of(
                power(11L, 10L, "sys:role:list"),
                power(21L, 20L, "audit:behavior:view"),
                power(31L, 30L, "workflow:view"));
        List<SysPower> allResources = List.of(
                power(10L, 0L, null),
                power(11L, 10L, "sys:role:list"),
                power(12L, 10L, "sys:user:list"),
                power(20L, 0L, null),
                power(21L, 20L, "audit:behavior:view"),
                power(30L, 0L, null),
                power(31L, 30L, "workflow:view"));
        when(sysMenuService.list(any(Wrapper.class)))
                .thenReturn(selectedPages);
        when(sysMenuService.list()).thenReturn(allResources);
        when(sysRolePowerService.saveBatch(any(Collection.class))).thenReturn(true);

        boolean saved = service.saveRolePermissions(roleId, RolePermissionDto.builder()
                .permissionCodes(List.of("sys:role:list", "audit:behavior:view", "workflow:view"))
                .build());

        assertThat(saved).isTrue();
        ArgumentCaptor<Collection<SysRolePower>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(sysRolePowerService).saveBatch(captor.capture());
        assertThat(captor.getValue())
                .extracting(SysRolePower::getPowerId)
                .containsExactlyInAnyOrder(10L, 11L, 20L, 21L, 30L, 31L)
                .doesNotContain(12L);
        verify(permissionVersionService).increaseForRole(roleId);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void saveRolePermissionsPersistsCustomDepartmentScope() {
        ISysMenuService sysMenuService = mock(ISysMenuService.class);
        ISysRolePowerService sysRolePowerService = mock(ISysRolePowerService.class);
        ISysRoleDeptService sysRoleDeptService = mock(ISysRoleDeptService.class);
        ISysUserRoleService sysUserRoleService = mock(ISysUserRoleService.class);
        PermissionVersionService permissionVersionService = mock(PermissionVersionService.class);
        SensitiveAuditService sensitiveAuditService = mock(SensitiveAuditService.class);
        ISysDeptService sysDeptService = mock(ISysDeptService.class);
        TestableSysRoleService service = new TestableSysRoleService(
                sysMenuService,
                sysRolePowerService,
                sysRoleDeptService,
                sysUserRoleService,
                sysDeptService,
                permissionVersionService,
                sensitiveAuditService);
        when(sysRoleDeptService.saveBatch(any(Collection.class))).thenReturn(true);
        when(sysDeptService.listByIds(any(Collection.class))).thenReturn(List.of(dept(20L), dept(11L)));

        boolean saved = service.saveRolePermissions(200L, RolePermissionDto.builder()
                .dataScope("DEPT_SETS")
                .deptIds(List.of(20L, 11L, 20L))
                .permissionCodes(List.of())
                .build());

        assertThat(saved).isTrue();
        assertThat(service.updatedDataScope()).isEqualTo("DEPT_SETS");
        ArgumentCaptor<Collection<SysRoleDept>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(sysRoleDeptService).saveBatch(captor.capture());
        assertThat(captor.getValue())
                .extracting(SysRoleDept::getDeptId)
                .containsExactly(20L, 11L);
        verify(sysRoleDeptService).deleteByRoleId(200L);
        verify(permissionVersionService).increaseForRole(200L);
        verify(sensitiveAuditService).record(
                "角色权限",
                "保存角色授权",
                "ROLE",
                "200",
                "{\"permissionCount\":0,\"dataScopeBefore\":\"SELF\",\"dataScopeAfter\":\"DEPT_SETS\",\"deptCountBefore\":0,\"deptCountAfter\":2}");
    }

    @Test
    void assignableDataScopeCodesShouldNotExceedCurrentOperatorScope() {
        SysRoleServiceImpl service = new TestableSysRoleService(
                mock(ISysMenuService.class),
                mock(ISysRolePowerService.class),
                mock(ISysRoleDeptService.class),
                mock(ISysUserRoleService.class),
                mock(PermissionVersionService.class),
                mock(SensitiveAuditService.class));

        assertThat(service.assignableDataScopeCodes(AuthPrincipal.builder()
                .dataScopes(List.of(DataScopeType.DEPT))
                .build()))
                .containsExactly("DEPT", "SELF", "DEPT_SETS");
        assertThat(service.assignableDataScopeCodes(AuthPrincipal.builder()
                .dataScopes(List.of(DataScopeType.DEPT_AND_CHILDREN))
                .build()))
                .containsExactly("DEPT_AND_CHILDREN", "DEPT", "SELF", "DEPT_SETS");
        assertThat(service.assignableDataScopeCodes(AuthPrincipal.builder()
                .dataScopes(List.of(DataScopeType.DEPT_SETS))
                .build()))
                .containsExactly("SELF", "DEPT_SETS");
        assertThat(service.assignableDataScopeCodes(AuthPrincipal.builder()
                .dataScopes(List.of(DataScopeType.SELF))
                .build()))
                .containsExactly("SELF");
    }

    @Test
    void saveRolePermissionsRejectsNonStandardDataScope() {
        SysRoleServiceImpl service = new TestableSysRoleService(
                mock(ISysMenuService.class),
                mock(ISysRolePowerService.class),
                mock(ISysRoleDeptService.class),
                mock(ISysUserRoleService.class),
                mock(PermissionVersionService.class),
                mock(SensitiveAuditService.class));

        assertThatThrownBy(() -> service.saveRolePermissions(200L, RolePermissionDto.builder()
                .dataScope("ALL_DATA")
                .permissionCodes(List.of())
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数据范围不正确");
    }

    @Test
    void saveRolePermissionsRejectsUnknownDataScope() {
        SysRoleServiceImpl service = new TestableSysRoleService(
                mock(ISysMenuService.class),
                mock(ISysRolePowerService.class),
                mock(ISysRoleDeptService.class),
                mock(ISysUserRoleService.class),
                mock(PermissionVersionService.class),
                mock(SensitiveAuditService.class));

        assertThatThrownBy(() -> service.saveRolePermissions(200L, RolePermissionDto.builder()
                .dataScope("全部门")
                .permissionCodes(List.of())
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数据范围不正确");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void saveRolePermissionsRejectsUnknownPermissionCodes() {
        ISysMenuService sysMenuService = mock(ISysMenuService.class);
        SysRoleServiceImpl service = new TestableSysRoleService(
                sysMenuService,
                mock(ISysRolePowerService.class),
                mock(ISysRoleDeptService.class),
                mock(ISysUserRoleService.class),
                mock(PermissionVersionService.class),
                mock(SensitiveAuditService.class));
        when(sysMenuService.list(any(Wrapper.class))).thenReturn(List.of());

        assertThatThrownBy(() -> service.saveRolePermissions(200L, RolePermissionDto.builder()
                .permissionCodes(List.of("ghost:permission"))
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在无效权限码：ghost:permission");
    }

    @Test
    void saveRolePermissionsRejectsCustomDepartmentScopeWithoutDepartments() {
        SysRoleServiceImpl service = new TestableSysRoleService(
                mock(ISysMenuService.class),
                mock(ISysRolePowerService.class),
                mock(ISysRoleDeptService.class),
                mock(ISysUserRoleService.class),
                mock(PermissionVersionService.class),
                mock(SensitiveAuditService.class));

        assertThatThrownBy(() -> service.saveRolePermissions(200L, RolePermissionDto.builder()
                .dataScope("DEPT_SETS")
                .deptIds(List.of())
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请选择自定义部门范围");
    }

    @Test
    void saveRolePermissionsRejectsBuiltInAdminRole() {
        SysRoleServiceImpl service = new TestableSysRoleService(
                mock(ISysMenuService.class),
                mock(ISysRolePowerService.class),
                mock(ISysRoleDeptService.class),
                mock(ISysUserRoleService.class),
                mock(PermissionVersionService.class),
                mock(SensitiveAuditService.class),
                "admin");

        assertThatThrownBy(() -> service.saveRolePermissions(200L, RolePermissionDto.builder()
                .permissionCodes(List.of("sys:user:list"))
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内置超级管理员角色");
    }

    private static SysPower power(Long menuId, Long pid, String powerCode) {
        SysPower power = new SysPower();
        power.setMenuId(menuId);
        power.setPid(pid);
        power.setPowerCode(powerCode);
        return power;
    }

    private static SysDept dept(Long deptId) {
        SysDept dept = new SysDept();
        dept.setDeptId(deptId);
        dept.setStatus(true);
        return dept;
    }

    private static final class TestableSysRoleService extends SysRoleServiceImpl {
        private final String roleCode;
        private String updatedDataScope;

        private TestableSysRoleService(ISysMenuService sysMenuService,
                                       ISysRolePowerService sysRolePowerService,
                                       ISysRoleDeptService sysRoleDeptService,
                                       ISysUserRoleService sysUserRoleService,
                                       PermissionVersionService permissionVersionService,
                                       SensitiveAuditService sensitiveAuditService) {
            this(sysMenuService, sysRolePowerService, sysRoleDeptService, sysUserRoleService, mock(ISysDeptService.class), permissionVersionService, sensitiveAuditService, "staff");
        }

        private TestableSysRoleService(ISysMenuService sysMenuService,
                                       ISysRolePowerService sysRolePowerService,
                                       ISysRoleDeptService sysRoleDeptService,
                                       ISysUserRoleService sysUserRoleService,
                                       ISysDeptService sysDeptService,
                                       PermissionVersionService permissionVersionService,
                                       SensitiveAuditService sensitiveAuditService) {
            this(sysMenuService, sysRolePowerService, sysRoleDeptService, sysUserRoleService, sysDeptService, permissionVersionService, sensitiveAuditService, "staff");
        }

        private TestableSysRoleService(ISysMenuService sysMenuService,
                                       ISysRolePowerService sysRolePowerService,
                                       ISysRoleDeptService sysRoleDeptService,
                                       ISysUserRoleService sysUserRoleService,
                                       PermissionVersionService permissionVersionService,
                                       SensitiveAuditService sensitiveAuditService,
                                       String roleCode) {
            this(sysMenuService, sysRolePowerService, sysRoleDeptService, sysUserRoleService, mock(ISysDeptService.class), permissionVersionService, sensitiveAuditService, roleCode);
        }

        private TestableSysRoleService(ISysMenuService sysMenuService,
                                       ISysRolePowerService sysRolePowerService,
                                       ISysRoleDeptService sysRoleDeptService,
                                       ISysUserRoleService sysUserRoleService,
                                       ISysDeptService sysDeptService,
                                       PermissionVersionService permissionVersionService,
                                       SensitiveAuditService sensitiveAuditService,
                                       String roleCode) {
            super(sysMenuService,
                    sysRolePowerService,
                    sysRoleDeptService,
                    sysUserRoleService,
                    sysDeptService,
                    permissionVersionService,
                    new DataScopeAssignmentPolicy(),
                    sensitiveAuditService);
            this.roleCode = roleCode;
        }

        @Override
        protected void updateRoleDataScope(Long roleId, String dataScope) {
            this.updatedDataScope = dataScope;
        }

        private String updatedDataScope() {
            return updatedDataScope;
        }

        @Override
        public SysRole getById(Serializable id) {
            SysRole role = new SysRole();
            role.setRoleId((Long) id);
            role.setRoleCode(roleCode);
            return role;
        }
    }
}
