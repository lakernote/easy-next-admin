package com.laker.admin.module.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.module.system.entity.SysRolePermission;
import com.laker.admin.module.system.mapper.SysRolePermissionMapper;
import com.laker.admin.module.system.service.ISysRolePermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 角色权限关系服务实现。
 *
 * @author laker
 * @since 2021-08-11
 */
@Service
public class SysRolePermissionServiceImpl extends ServiceImpl<SysRolePermissionMapper, SysRolePermission> implements ISysRolePermissionService {

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean saveRolePermissions(Long roleId, String permissionResourceIds) {
        List<String> resourceIdList = StringUtils.hasText(permissionResourceIds)
                ? Arrays.stream(permissionResourceIds.split(",")).map(String::trim).filter(StringUtils::hasText).distinct().toList()
                : List.of();
        deleteByRoleId(roleId);
        if (resourceIdList.isEmpty()) {
            return true;
        }
        List<SysRolePermission> rolePermissions = new ArrayList<>();
        resourceIdList.forEach(resourceId -> {
            SysRolePermission rolePermission = new SysRolePermission();
            rolePermission.setRoleId(roleId);
            rolePermission.setPermissionResourceId(Long.valueOf(resourceId));
            rolePermissions.add(rolePermission);
        });
        return this.saveBatch(rolePermissions);
    }

    @Override
    public List<Long> listPermissionResourceIdsByRoleId(Long roleId) {
        if (roleId == null) {
            return List.of();
        }
        return baseMapper.selectPermissionResourceIdsByRoleId(roleId);
    }

    @Override
    public List<String> listPermissionCodesByRoleId(Long roleId) {
        if (roleId == null) {
            return List.of();
        }
        return baseMapper.selectPermissionCodesByRoleId(roleId);
    }

    @Override
    public boolean deleteByRoleId(Long roleId) {
        if (roleId == null) {
            return true;
        }
        return baseMapper.deleteByRoleId(roleId) >= 0;
    }

    @Override
    public boolean deleteByPermissionResourceId(Long permissionResourceId) {
        if (permissionResourceId == null) {
            return true;
        }
        return baseMapper.delete(Wrappers.<SysRolePermission>lambdaQuery()
                .eq(SysRolePermission::getPermissionResourceId, permissionResourceId)) >= 0;
    }
}
