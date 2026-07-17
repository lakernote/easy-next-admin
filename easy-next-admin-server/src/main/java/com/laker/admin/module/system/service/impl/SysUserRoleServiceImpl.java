package com.laker.admin.module.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.config.cache.EasyCacheConfig;
import com.laker.admin.module.system.dto.RoleUserCount;
import com.laker.admin.module.system.dto.UserRoleBinding;
import com.laker.admin.module.system.entity.SysUserRole;
import com.laker.admin.module.system.mapper.SysUserRoleMapper;
import com.laker.admin.module.system.service.ISysUserRoleService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.List;

/**
 * 用户角色关系服务实现。
 *
 * @author laker
 * @since 2021-08-11
 */
@Service
public class SysUserRoleServiceImpl extends ServiceImpl<SysUserRoleMapper, SysUserRole> implements ISysUserRoleService {
    @Override
    @CacheEvict(cacheNames = EasyCacheConfig.CACHE_DATA_SCOPE_CUSTOM_DEPT_IDS,
            allEntries = true,
            condition = "#root.args[0] != null && !#root.args[0].isEmpty()")
    public boolean saveBatch(Collection<SysUserRole> entityList) {
        return super.saveBatch(entityList);
    }

    @Override
    public List<UserRoleBinding> listRoleBindingsByUserIds(Collection<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return List.of();
        }
        return baseMapper.selectRoleBindingsByUserIds(userIds);
    }

    @Override
    public List<RoleUserCount> countUsersByRoleIds(Collection<Long> roleIds) {
        if (CollectionUtils.isEmpty(roleIds)) {
            return List.of();
        }
        return baseMapper.countUsersByRoleIds(roleIds);
    }

    @Override
    public List<Long> listRoleIdsByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return baseMapper.selectRoleIdsByUserId(userId);
    }

    @Override
    public List<Long> listUserIdsByRoleId(Long roleId) {
        if (roleId == null) {
            return List.of();
        }
        return baseMapper.selectUserIdsByRoleId(roleId);
    }

    @Override
    public List<Long> listUserIdsByRoleIds(Collection<Long> roleIds) {
        if (CollectionUtils.isEmpty(roleIds)) {
            return List.of();
        }
        return baseMapper.selectUserIdsByRoleIds(roleIds);
    }

    @Override
    @CacheEvict(cacheNames = EasyCacheConfig.CACHE_DATA_SCOPE_CUSTOM_DEPT_IDS,
            key = "#root.args[0]",
            condition = "#root.args[0] != null")
    public boolean deleteByUserId(Long userId) {
        if (userId == null) {
            return true;
        }
        return baseMapper.deleteByUserId(userId) >= 0;
    }

    @Override
    @CacheEvict(cacheNames = EasyCacheConfig.CACHE_DATA_SCOPE_CUSTOM_DEPT_IDS,
            allEntries = true,
            condition = "#root.args[0] != null && !#root.args[0].isEmpty()")
    public boolean deleteByUserIds(Collection<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return true;
        }
        return baseMapper.deleteByUserIds(userIds) >= 0;
    }

    @Override
    @CacheEvict(cacheNames = EasyCacheConfig.CACHE_DATA_SCOPE_CUSTOM_DEPT_IDS,
            allEntries = true,
            condition = "#root.args[0] != null")
    public boolean deleteByRoleId(Long roleId) {
        if (roleId == null) {
            return true;
        }
        return baseMapper.deleteByRoleId(roleId) >= 0;
    }
}
