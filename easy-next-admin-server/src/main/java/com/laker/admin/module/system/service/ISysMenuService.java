package com.laker.admin.module.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.laker.admin.module.system.dto.MenuVo;
import com.laker.admin.module.system.dto.PermissionResourceDto;
import com.laker.admin.module.system.dto.PermissionResourceRequest;
import com.laker.admin.module.system.dto.workbench.PermissionResourceSummary;
import com.laker.admin.module.system.entity.SysPower;

import java.util.List;

/**
 * <p>
 * 系统菜单表 服务类
 * </p>
 *
 * @author laker
 * @since 2021-08-04
 */
public interface ISysMenuService extends IService<SysPower> {
    List<MenuVo> menu();

    List<SysPower> listResources();

    List<PermissionResourceDto> listResourceViews();

    List<PermissionResourceDto> listEnabledResourceViews();

    List<SysPower> listEnabledResourcesByUserId(Long userId);

    PermissionResourceSummary countPermissionResourceSummary();

    PermissionResourceDto saveResource(PermissionResourceRequest request);

    boolean deleteResource(Long resourceId);
}
