package com.laker.admin.module.system.dto;

import com.laker.admin.module.system.entity.SysDept;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 组织对外响应模型。隐藏树路径、审计列、软删和乐观锁等内部字段。
 */
@Data
@Builder
public class SystemDepartmentView {
    private Long deptId;
    private String deptName;
    private String fullName;
    private String address;
    private Long pid;
    private Long leaderUserId;
    private Boolean status;
    private Integer sort;

    public static SystemDepartmentView from(SysDept department) {
        if (department == null) {
            return null;
        }
        return SystemDepartmentView.builder()
                .deptId(department.getDeptId())
                .deptName(department.getDeptName())
                .fullName(department.getFullName())
                .address(department.getAddress())
                .pid(department.getPid())
                .leaderUserId(department.getLeaderUserId())
                .status(department.getStatus())
                .sort(department.getSort())
                .build();
    }

    public static List<SystemDepartmentView> fromList(List<SysDept> departments) {
        return departments == null ? List.of() : departments.stream().map(SystemDepartmentView::from).toList();
    }
}
