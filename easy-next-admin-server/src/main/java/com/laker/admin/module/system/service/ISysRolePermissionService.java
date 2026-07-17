package com.laker.admin.module.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.laker.admin.module.system.entity.SysRolePermission;

import java.util.List;

/**
 * 角色权限关系服务。
 *
 * @author laker
 * @since 2021-08-11
 */
public interface ISysRolePermissionService extends IService<SysRolePermission> {

    boolean saveRolePermissions(Long roleId, String permissionResourceIds);

    List<Long> listPermissionResourceIdsByRoleId(Long roleId);

    List<String> listPermissionCodesByRoleId(Long roleId);

    boolean deleteByRoleId(Long roleId);

    boolean deleteByPermissionResourceId(Long permissionResourceId);
}
