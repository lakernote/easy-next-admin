package com.laker.admin.module.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.laker.admin.common.model.PageResponse;
import com.laker.admin.module.system.dto.SystemDepartmentView;
import com.laker.admin.module.system.dto.SystemDeptQuery;
import com.laker.admin.module.system.entity.SysDept;

import java.util.Collection;
import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author laker
 * @since 2021-08-11
 */
public interface ISysDeptService extends IService<SysDept> {
    PageResponse<SystemDepartmentView> pageDepartments(SystemDeptQuery query);

    List<SysDept> tree();

    List<SysDept> enabledTree();

    boolean saveDepartment(SysDept department);

    boolean deleteDepartment(Long deptId);

    boolean deleteDepartments(Collection<Long> deptIds);

}
