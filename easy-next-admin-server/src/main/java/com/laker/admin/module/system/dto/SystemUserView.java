package com.laker.admin.module.system.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.laker.admin.module.system.entity.SysDept;
import com.laker.admin.module.system.entity.SysUser;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户管理对外响应模型，避免把数据库实体、逻辑删除和密码字段暴露给前端。
 */
@Data
@Builder
public class SystemUserView {
    private Long userId;
    private String userName;
    private String nickName;
    private String realName;
    private String employeeNo;
    private String positionName;
    private Long deptId;
    private String deptName;
    private Long managerUserId;
    private String managerName;
    private Long departmentLeaderUserId;
    private String departmentLeaderName;
    private Long upperDepartmentLeaderUserId;
    private String upperDepartmentLeaderName;
    private List<Long> roleIds;
    private List<String> roleNames;
    private String phone;
    private String email;
    private String avatar;
    private Integer enable;
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastLoginTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    public static SystemUserView from(SysUser user, SysDept dept, List<UserRoleBinding> roles) {
        return from(user, dept, roles, null, null, null);
    }

    public static SystemUserView from(SysUser user,
                                      SysDept dept,
                                      List<UserRoleBinding> roles,
                                      SysUser manager,
                                      SysUser departmentLeader,
                                      SysUser upperDepartmentLeader) {
        return SystemUserView.builder()
                .userId(user.getUserId())
                .userName(user.getUserName())
                .nickName(user.getNickName())
                .realName(user.getRealName())
                .employeeNo(user.getEmployeeNo())
                .positionName(user.getPositionName())
                .deptId(user.getDeptId())
                .deptName(dept == null ? user.getDeptName() : dept.getDeptName())
                .managerUserId(user.getManagerUserId())
                .managerName(userDisplayName(manager))
                .departmentLeaderUserId(departmentLeader == null ? null : departmentLeader.getUserId())
                .departmentLeaderName(userDisplayName(departmentLeader))
                .upperDepartmentLeaderUserId(upperDepartmentLeader == null ? null : upperDepartmentLeader.getUserId())
                .upperDepartmentLeaderName(userDisplayName(upperDepartmentLeader))
                .roleIds(roles.stream().map(UserRoleBinding::getRoleId).distinct().toList())
                .roleNames(roles.stream().map(UserRoleBinding::getRoleName).filter(name -> name != null && !name.isBlank()).distinct().toList())
                .phone(user.getPhone())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .enable(user.getEnable())
                .remark(user.getRemark())
                .lastLoginTime(user.getLastLoginTime())
                .createTime(user.getCreateTime())
                .build();
    }

    private static String userDisplayName(SysUser user) {
        if (user == null) {
            return null;
        }
        String name = user.getNickName();
        if (name == null || name.isBlank()) {
            name = user.getUserName();
        }
        if (user.getUserName() == null || user.getUserName().isBlank() || user.getUserName().equals(name)) {
            return name;
        }
        return name + "（" + user.getUserName() + "）";
    }
}
