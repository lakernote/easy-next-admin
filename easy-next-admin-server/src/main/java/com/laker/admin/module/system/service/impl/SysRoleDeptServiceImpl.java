package com.laker.admin.module.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.config.cache.EasyCacheConfig;
import com.laker.admin.module.system.entity.SysRoleDept;
import com.laker.admin.module.system.mapper.SysRoleDeptMapper;
import com.laker.admin.module.system.service.ISysRoleDeptService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public class SysRoleDeptServiceImpl extends ServiceImpl<SysRoleDeptMapper, SysRoleDept> implements ISysRoleDeptService {
    @Override
    @CacheEvict(cacheNames = EasyCacheConfig.CACHE_DATA_SCOPE_CUSTOM_DEPT_IDS,
            allEntries = true,
            condition = "#root.args[0] != null && !#root.args[0].isEmpty()")
    public boolean saveBatch(Collection<SysRoleDept> entityList) {
        return super.saveBatch(entityList);
    }

    @Override
    public List<Long> listDeptIdsByRoleId(Long roleId) {
        if (roleId == null) {
            return List.of();
        }
        return baseMapper.selectDeptIdsByRoleId(roleId);
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
