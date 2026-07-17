package com.laker.admin.module.system.dto;

import com.laker.admin.module.system.entity.SysDept;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 组织保存请求。只允许页面维护企业组织业务字段，层级路径和审计字段由服务端生成。
 */
@Data
public class SystemDepartmentRequest {
    private Long deptId;
    @NotBlank(message = "部门名称不能为空")
    private String deptName;
    private String address;
    private Long pid;
    private Long leaderUserId;
    private Boolean status;
    private Integer sort;

    public SysDept toEntity() {
        SysDept department = new SysDept();
        department.setDeptId(deptId);
        department.setDeptName(deptName);
        department.setAddress(address);
        department.setPid(pid);
        department.setLeaderUserId(leaderUserId);
        department.setStatus(status);
        department.setSort(sort);
        return department;
    }
}
