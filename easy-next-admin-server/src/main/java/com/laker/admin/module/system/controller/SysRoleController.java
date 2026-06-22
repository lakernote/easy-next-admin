package com.laker.admin.module.system.controller;

import com.laker.admin.common.model.PageResponse;
import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.audit.EasyAudit;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.system.dto.PermissionResourceDto;
import com.laker.admin.module.system.dto.RoleRequest;
import com.laker.admin.module.system.dto.RolePermissionDto;
import com.laker.admin.module.system.dto.SystemRoleQuery;
import com.laker.admin.module.system.dto.SystemRoleView;
import com.laker.admin.module.system.service.ISysMenuService;
import com.laker.admin.module.system.service.ISysRoleService;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 角色权限接口。
 * 角色分页、用户数统计、权限资源保存等业务规则统一由 Service 处理。
 */
@RestController
@RequestMapping("/api/system/roles")
public class SysRoleController {

    private final ISysRoleService sysRoleService;
    private final ISysMenuService sysMenuService;

    public SysRoleController(ISysRoleService sysRoleService, ISysMenuService sysMenuService) {
        this.sysRoleService = sysRoleService;
        this.sysMenuService = sysMenuService;
    }

    @GetMapping
    @EasyPermission(EasyPermissions.System.ROLE_LIST)
    public PageResponse<SystemRoleView> pageAll(SystemRoleQuery query) {
        return sysRoleService.pageRoles(query);
    }

    @PostMapping
    @EasyPermission(EasyPermissions.System.ROLE_EDIT)
    @EasyAudit(module = "系统管理", action = "保存角色", dataChange = true, bizType = "SYS_ROLE", changeType = "SAVE")
    public Response<SystemRoleView> saveOrUpdate(@RequestBody @Valid RoleRequest param) {
        return Response.ok(sysRoleService.saveRole(param));
    }

    @GetMapping("/permission-resources")
    @EasyPermission(EasyPermissions.System.ROLE_LIST)
    public Response<List<PermissionResourceDto>> permissionResources() {
        return Response.ok(sysMenuService.listEnabledResourceViews());
    }

    @GetMapping("/{id}")
    @EasyPermission(EasyPermissions.System.ROLE_LIST)
    public Response<SystemRoleView> get(@PathVariable Long id) {
        return Response.ok(sysRoleService.getRole(id));
    }

    @DeleteMapping("/{id}")
    @EasyPermission(EasyPermissions.System.ROLE_EDIT)
    @EasyAudit(module = "系统管理", action = "删除角色", dataChange = true, bizType = "SYS_ROLE", bizId = "#id", changeType = "DELETE")
    public Response<Boolean> delete(@PathVariable Long id) {
        return Response.ok(sysRoleService.deleteRole(id));
    }

    @GetMapping("/{roleId}/permissions")
    @EasyPermission(EasyPermissions.System.ROLE_LIST)
    public Response<RolePermissionDto> permissions(@PathVariable Long roleId) {
        return Response.ok(sysRoleService.getRolePermissions(roleId));
    }

    @PutMapping("/{roleId}/permissions")
    @EasyPermission(EasyPermissions.System.ROLE_EDIT)
    @EasyAudit(module = "系统管理", action = "保存角色权限", dataChange = true, bizType = "SYS_ROLE_PERMISSION", bizId = "#roleId", changeType = "SAVE")
    public Response<Boolean> savePermissions(@PathVariable Long roleId, @RequestBody RolePermissionDto param) {
        return Response.ok(sysRoleService.saveRolePermissions(roleId, param));
    }
}
