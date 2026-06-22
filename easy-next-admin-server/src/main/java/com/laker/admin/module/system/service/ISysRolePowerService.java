package com.laker.admin.module.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.laker.admin.module.system.entity.SysRolePower;

import java.util.List;

/**
 * 角色权限关系服务。
 *
 * @author laker
 * @since 2021-08-11
 */
public interface ISysRolePowerService extends IService<SysRolePower> {

    boolean saveRolePower(Long roleId, String powerIds);

    List<Long> listPowerIdsByRoleId(Long roleId);

    List<String> listPermissionCodesByRoleId(Long roleId);

    boolean deleteByRoleId(Long roleId);

    boolean deleteByPowerId(Long powerId);
}
