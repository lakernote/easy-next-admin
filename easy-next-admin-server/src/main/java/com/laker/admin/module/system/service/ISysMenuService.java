package com.laker.admin.module.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.laker.admin.module.system.dto.MenuVo;
import com.laker.admin.module.system.dto.PermissionResourceDto;
import com.laker.admin.module.system.dto.PermissionResourceRequest;
import com.laker.admin.module.system.dto.workbench.PermissionResourceSummary;
import com.laker.admin.module.system.entity.SysMenuResource;

import java.util.List;

/**
 * 菜单权限资源服务。
 */
public interface ISysMenuService extends IService<SysMenuResource> {
    List<MenuVo> menu();

    List<SysMenuResource> listResources();

    List<PermissionResourceDto> listResourceViews();

    List<PermissionResourceDto> listEnabledResourceViews();

    List<SysMenuResource> listEnabledResourcesByUserId(Long userId);

    PermissionResourceSummary countPermissionResourceSummary();

    PermissionResourceDto saveResource(PermissionResourceRequest request);

    boolean deleteResource(Long resourceId);
}
