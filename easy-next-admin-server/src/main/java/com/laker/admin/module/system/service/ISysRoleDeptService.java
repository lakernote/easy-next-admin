package com.laker.admin.module.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.laker.admin.module.system.entity.SysRoleDept;

import java.util.List;

public interface ISysRoleDeptService extends IService<SysRoleDept> {
    List<Long> listDeptIdsByRoleId(Long roleId);

    boolean deleteByRoleId(Long roleId);
}
