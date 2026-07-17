package com.laker.admin.module.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.laker.admin.common.model.PageResponse;
import com.laker.admin.module.system.dto.RoleRequest;
import com.laker.admin.module.system.dto.RolePermissionDto;
import com.laker.admin.module.system.dto.SystemRoleQuery;
import com.laker.admin.module.system.dto.SystemRoleView;
import com.laker.admin.module.system.dto.workbench.EnabledCountSummary;
import com.laker.admin.module.system.entity.SysRole;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author laker
 * @since 2021-08-11
 */
public interface ISysRoleService extends IService<SysRole> {
    PageResponse<SystemRoleView> pageRoles(SystemRoleQuery query);

    SystemRoleView saveRole(RoleRequest request);

    SystemRoleView getRole(Long roleId);

    RolePermissionDto getRolePermissions(Long roleId);

    boolean saveRolePermissions(Long roleId, RolePermissionDto request);

    boolean deleteRole(Long roleId);

    EnabledCountSummary countEnabledSummary();

}
