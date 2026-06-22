package com.laker.admin.module.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.laker.admin.module.system.dto.RoleUserCount;
import com.laker.admin.module.system.dto.UserRoleBinding;
import com.laker.admin.module.system.entity.SysUserRole;

import java.util.Collection;
import java.util.List;

/**
 * 用户角色关系服务。
 *
 * @author laker
 * @since 2021-08-11
 */
public interface ISysUserRoleService extends IService<SysUserRole> {

    List<UserRoleBinding> listRoleBindingsByUserIds(Collection<Long> userIds);

    List<RoleUserCount> countUsersByRoleIds(Collection<Long> roleIds);

    List<Long> listRoleIdsByUserId(Long userId);

    List<Long> listUserIdsByRoleId(Long roleId);

    List<Long> listUserIdsByRoleIds(Collection<Long> roleIds);

    boolean deleteByUserId(Long userId);

    boolean deleteByUserIds(Collection<Long> userIds);

    boolean deleteByRoleId(Long roleId);
}
