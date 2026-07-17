package com.laker.admin.module.workflow.support;

import com.laker.admin.module.system.entity.SysDept;
import com.laker.admin.module.system.entity.SysRole;
import com.laker.admin.module.system.entity.SysUser;
import com.laker.admin.module.system.service.ISysDeptService;
import com.laker.admin.module.system.service.ISysRoleService;
import com.laker.admin.module.system.service.ISysUserRoleService;
import com.laker.admin.module.system.service.ISysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class WorkflowAssigneeResolver {
    private final ISysUserService userService;
    private final ISysDeptService deptService;
    private final ISysRoleService roleService;
    private final ISysUserRoleService userRoleService;

    public List<Long> resolve(WorkflowAssigneeRule rule, Long initiatorId) {
        return resolveWithContext(rule, initiatorId).assigneeIds();
    }

    public WorkflowAssigneeResolution resolveWithContext(WorkflowAssigneeRule rule, Long initiatorId) {
        if (rule == null) {
            return WorkflowAssigneeResolution.empty(WorkflowApproverType.UNKNOWN, "未配置审批人");
        }
        return switch (rule.approverType()) {
            case USER -> userAssignees(rule.assigneeIds());
            case ROLE -> roleAssignees(rule.roleCode());
            case INITIATOR -> initiatorAssignee(initiatorId);
            case INITIATOR_SELECTED -> WorkflowAssigneeResolution.empty(WorkflowApproverType.INITIATOR_SELECTED, "发起人选择");
            case MANAGER -> managerOfUser(initiatorId);
            case DEPT_LEADER -> deptLeaderOfUser(initiatorId, false);
            case UPPER_DEPT_LEADER -> deptLeaderOfUser(initiatorId, true);
            case UNKNOWN -> WorkflowAssigneeResolution.empty(WorkflowApproverType.UNKNOWN, "未配置审批人");
        };
    }

    private WorkflowAssigneeResolution userAssignees(List<Long> assigneeIds) {
        if (assigneeIds == null || assigneeIds.isEmpty()) {
            return WorkflowAssigneeResolution.empty(WorkflowApproverType.USER, "指定成员");
        }
        List<Long> activeAssigneeIds = activeUserIds(assigneeIds);
        if (activeAssigneeIds.size() != new LinkedHashSet<>(assigneeIds).size()) {
            return WorkflowAssigneeResolution.empty(WorkflowApproverType.USER, "指定成员");
        }
        return new WorkflowAssigneeResolution(activeAssigneeIds, WorkflowApproverType.USER.name(), "指定成员",
                "固定成员：" + userNames(activeAssigneeIds));
    }

    private WorkflowAssigneeResolution roleAssignees(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return WorkflowAssigneeResolution.empty(WorkflowApproverType.ROLE, "职能角色");
        }
        SysRole role = roleService.lambdaQuery()
                .eq(SysRole::getRoleCode, roleCode.trim())
                .eq(SysRole::getEnable, true)
                .list()
                .stream()
                .findFirst()
                .orElse(null);
        if (role == null) {
            return WorkflowAssigneeResolution.empty(WorkflowApproverType.ROLE, "职能角色");
        }
        List<Long> userIds = userRoleService.listUserIdsByRoleId(role.getRoleId());
        if (userIds.isEmpty()) {
            return WorkflowAssigneeResolution.empty(WorkflowApproverType.ROLE, "职能角色");
        }
        List<Long> assigneeIds = userService.listByIds(userIds)
                .stream()
                .filter(user -> user.getEnable() == null || user.getEnable() == 1)
                .map(SysUser::getUserId)
                .distinct()
                .toList();
        return new WorkflowAssigneeResolution(assigneeIds, WorkflowApproverType.ROLE.name(), "职能角色：" + role.getRoleName(),
                "角色 " + role.getRoleCode() + " -> " + userNames(assigneeIds));
    }

    private WorkflowAssigneeResolution initiatorAssignee(Long initiatorId) {
        if (initiatorId == null) {
            return WorkflowAssigneeResolution.empty(WorkflowApproverType.INITIATOR, "发起人本人");
        }
        return new WorkflowAssigneeResolution(single(initiatorId), WorkflowApproverType.INITIATOR.name(), "发起人本人",
                "发起人：" + userName(initiatorId));
    }

    private WorkflowAssigneeResolution managerOfUser(Long userId) {
        if (userId == null) {
            return WorkflowAssigneeResolution.empty(WorkflowApproverType.MANAGER, "发起人直属上级");
        }
        SysUser user = userService.getById(userId);
        if (user == null || user.getManagerUserId() == null) {
            return WorkflowAssigneeResolution.empty(WorkflowApproverType.MANAGER, "发起人直属上级");
        }
        SysUser manager = activeUser(user.getManagerUserId());
        if (manager == null) {
            return WorkflowAssigneeResolution.empty(WorkflowApproverType.MANAGER, "发起人直属上级");
        }
        return new WorkflowAssigneeResolution(single(manager.getUserId()), WorkflowApproverType.MANAGER.name(), "发起人直属上级",
                userName(user) + " -> 直属上级 -> " + userName(manager));
    }

    private WorkflowAssigneeResolution deptLeaderOfUser(Long userId, boolean parentDept) {
        if (userId == null) {
            return WorkflowAssigneeResolution.empty(parentDept ? WorkflowApproverType.UPPER_DEPT_LEADER : WorkflowApproverType.DEPT_LEADER,
                    parentDept ? "发起人上级部门负责人" : "发起人部门负责人");
        }
        SysUser user = userService.getById(userId);
        if (user == null || user.getDeptId() == null) {
            return WorkflowAssigneeResolution.empty(parentDept ? WorkflowApproverType.UPPER_DEPT_LEADER : WorkflowApproverType.DEPT_LEADER,
                    parentDept ? "发起人上级部门负责人" : "发起人部门负责人");
        }
        SysDept dept = deptService.getById(user.getDeptId());
        if (dept == null) {
            return WorkflowAssigneeResolution.empty(parentDept ? WorkflowApproverType.UPPER_DEPT_LEADER : WorkflowApproverType.DEPT_LEADER,
                    parentDept ? "发起人上级部门负责人" : "发起人部门负责人");
        }
        if (parentDept && dept.getPid() != null && dept.getPid() > 0) {
            SysDept parent = deptService.getById(dept.getPid());
            if (parent != null) {
                dept = parent;
            }
        }
        SysUser leader = activeUser(dept.getLeaderUserId());
        if (leader == null) {
            return WorkflowAssigneeResolution.empty(parentDept ? WorkflowApproverType.UPPER_DEPT_LEADER : WorkflowApproverType.DEPT_LEADER,
                    parentDept ? "发起人上级部门负责人" : "发起人部门负责人");
        }
        if (!parentDept && leader.getUserId().equals(userId)) {
            WorkflowAssigneeResolution upper = deptLeaderOfUser(userId, true);
            if (!upper.assigneeIds().isEmpty()) {
                return new WorkflowAssigneeResolution(upper.assigneeIds(), WorkflowApproverType.DEPT_LEADER.name(), "发起人部门负责人（自审上跳）",
                        userName(user) + " -> " + dept.getDeptName() + "负责人为发起人，自动上跳 -> " + upper.resolvePath());
            }
        }
        return new WorkflowAssigneeResolution(single(leader.getUserId()),
                parentDept ? WorkflowApproverType.UPPER_DEPT_LEADER.name() : WorkflowApproverType.DEPT_LEADER.name(),
                parentDept ? "发起人上级部门负责人" : "发起人部门负责人",
                userName(user) + " -> " + dept.getDeptName() + " -> " + userName(leader));
    }

    private List<Long> single(Long value) {
        return value == null ? List.of() : List.of(value);
    }

    private SysUser activeUser(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = userService.getById(userId);
        if (user == null || (user.getEnable() != null && user.getEnable() != 1)) {
            return null;
        }
        return user;
    }

    private List<Long> activeUserIds(List<Long> userIds) {
        Set<Long> configuredIds = new LinkedHashSet<>(userIds);
        if (configuredIds.isEmpty()) {
            return List.of();
        }
        Set<Long> activeIds = userService.listByIds(configuredIds)
                .stream()
                .filter(user -> user.getEnable() != null && user.getEnable() == 1)
                .map(SysUser::getUserId)
                .collect(java.util.stream.Collectors.toSet());
        if (!activeIds.containsAll(configuredIds)) {
            return List.of();
        }
        return configuredIds.stream().toList();
    }

    private String userNames(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return "未解析到成员";
        }
        return userService.listByIds(userIds).stream()
                .map(this::userName)
                .toList()
                .stream()
                .reduce((left, right) -> left + "、" + right)
                .orElse("未解析到成员");
    }

    private String userName(Long userId) {
        return userName(userService.getById(userId));
    }

    private String userName(SysUser user) {
        if (user == null) {
            return "未知用户";
        }
        String name = StringUtils.hasText(user.getNickName()) ? user.getNickName() : user.getUserName();
        if (!StringUtils.hasText(user.getUserName()) || user.getUserName().equals(name)) {
            return name;
        }
        return name + "（" + user.getUserName() + "）";
    }
}
