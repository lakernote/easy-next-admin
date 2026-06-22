package com.laker.admin.module.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.module.system.entity.SysRolePower;
import com.laker.admin.module.system.mapper.SysRolePowerMapper;
import com.laker.admin.module.system.service.ISysRolePowerService;
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
public class SysRolePowerServiceImpl extends ServiceImpl<SysRolePowerMapper, SysRolePower> implements ISysRolePowerService {

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean saveRolePower(Long roleId, String powerIds) {
        List<String> stringList = StringUtils.hasText(powerIds)
                ? Arrays.stream(powerIds.split(",")).map(String::trim).filter(StringUtils::hasText).distinct().toList()
                : List.of();
        deleteByRoleId(roleId);
        if (stringList.isEmpty()) {
            return true;
        }
        List<SysRolePower> rolePowers = new ArrayList<>();
        stringList.forEach(powerId -> {
            SysRolePower sysRolePower = new SysRolePower();
            sysRolePower.setRoleId(roleId);
            sysRolePower.setPowerId(Long.valueOf(powerId));
            rolePowers.add(sysRolePower);
        });
        return this.saveBatch(rolePowers);
    }

    @Override
    public List<Long> listPowerIdsByRoleId(Long roleId) {
        if (roleId == null) {
            return List.of();
        }
        return baseMapper.selectPowerIdsByRoleId(roleId);
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
    public boolean deleteByPowerId(Long powerId) {
        if (powerId == null) {
            return true;
        }
        return baseMapper.delete(Wrappers.<SysRolePower>lambdaQuery()
                .eq(SysRolePower::getPowerId, powerId)) >= 0;
    }
}
