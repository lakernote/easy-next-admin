package com.laker.admin.module.system.controller;

import com.laker.admin.common.model.Response;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.infrastructure.audit.EasyAudit;
import com.laker.admin.infrastructure.observability.apilog.EasyApiAccessLog;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.system.dto.MenuVo;
import com.laker.admin.module.system.dto.PermissionResourceDto;
import com.laker.admin.module.system.dto.PermissionResourceRequest;
import com.laker.admin.module.system.entity.SysMenuResource;
import com.laker.admin.module.system.service.ISysMenuService;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 菜单资源接口。
 * 菜单页面和按钮权限统一作为权限资源维护，Controller 保持薄入口。
 */
@RestController
@RequestMapping("/api/system/menus")
@EasyApiAccessLog
public class SysMenuController {

    private final ISysMenuService sysMenuService;

    public SysMenuController(ISysMenuService sysMenuService) {
        this.sysMenuService = sysMenuService;
    }

    @GetMapping("/list")
    @EasyPermission(EasyPermissions.System.MENU_LIST)
    public Response<List<PermissionResourceDto>> list() {
        return Response.ok(sysMenuService.listResourceViews());
    }

    @PostMapping
    @EasyPermission(EasyPermissions.System.MENU_EDIT)
    @EasyAudit(module = "系统管理", action = "保存权限资源", dataChange = true, bizType = "SYS_POWER", changeType = "SAVE")
    public Response<PermissionResourceDto> saveOrUpdate(@RequestBody @Valid PermissionResourceRequest param) {
        return Response.ok(sysMenuService.saveResource(param));
    }

    @GetMapping("/{id}")
    @EasyPermission(EasyPermissions.System.MENU_LIST)
    public Response<PermissionResourceDto> get(@PathVariable Long id) {
        SysMenuResource resource = sysMenuService.getById(id);
        if (resource == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "权限资源不存在");
        }
        return Response.ok(PermissionResourceDto.from(resource));
    }

    @GetMapping("/tree")
    @EasyPermission(EasyPermissions.System.MENU_LIST)
    public Response<List<MenuVo>> tree() {
        return Response.ok(sysMenuService.menu());
    }

    @DeleteMapping("/{id}")
    @EasyPermission(EasyPermissions.System.MENU_EDIT)
    @EasyAudit(module = "系统管理", action = "删除权限资源", dataChange = true, bizType = "SYS_POWER", bizId = "#id", changeType = "DELETE")
    public Response<Boolean> delete(@PathVariable Long id) {
        return Response.ok(sysMenuService.deleteResource(id));
    }
}
