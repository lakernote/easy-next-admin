package com.laker.admin.module.system.service;

import com.laker.admin.module.system.entity.SysDept;
import com.laker.admin.module.system.entity.SysUser;
import com.laker.admin.module.system.dto.SystemUserView;
import com.laker.admin.module.system.dto.UserRoleBinding;
import com.laker.admin.module.system.mapper.SysDeptMapper;
import com.laker.admin.module.system.mapper.SysUserMapper;
import com.laker.admin.infrastructure.security.datascope.context.EasyDataScopeContext;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SysUserRelationService {
    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;
    private final ISysUserRoleService sysUserRoleService;

    public SysUserRelationService(SysDeptMapper deptMapper, SysUserMapper userMapper, ISysUserRoleService sysUserRoleService) {
        this.deptMapper = deptMapper;
        this.userMapper = userMapper;
        this.sysUserRoleService = sysUserRoleService;
    }

    public void fillUserRelations(List<SysUser> users) {
        if (CollectionUtils.isEmpty(users)) {
            return;
        }
        Map<Long, String> deptNameById = loadDepartmentMap(users).values().stream()
                .collect(Collectors.toMap(SysDept::getDeptId, SysDept::getDeptName, (left, right) -> left));
        Map<Long, String> roleIdsByUserId = loadRoleIdsByUserId(users);

        users.forEach(user -> {
            if (user.getDeptId() != null) {
                user.setDeptName(deptNameById.get(user.getDeptId()));
            }
            user.setRoleIds(roleIdsByUserId.getOrDefault(user.getUserId(), ""));
        });
    }

    public List<SystemUserView> toUserViews(List<SysUser> users) {
        if (CollectionUtils.isEmpty(users)) {
            return List.of();
        }
        Map<Long, SysDept> deptById = loadDepartmentMap(users);
        Map<Long, SysDept> parentDeptById = loadParentDepartmentMap(deptById);
        Map<Long, SysUser> relatedUserById = loadRelatedUserMap(users, deptById, parentDeptById);
        Map<Long, List<UserRoleBinding>> rolesByUserId = loadRoleBindingsByUserId(users);
        return users.stream()
                .map(user -> {
                    SysDept dept = deptById.get(user.getDeptId());
                    SysDept parentDept = dept == null ? null : parentDeptById.get(dept.getPid());
                    return SystemUserView.from(
                            user,
                            dept,
                            rolesByUserId.getOrDefault(user.getUserId(), List.of()),
                            relatedUserById.get(user.getManagerUserId()),
                            relatedUserById.get(dept == null ? null : dept.getLeaderUserId()),
                            relatedUserById.get(parentDept == null ? null : parentDept.getLeaderUserId()));
                })
                .toList();
    }

    public SystemUserView toUserView(SysUser user) {
        if (user == null) {
            return null;
        }
        return toUserViews(List.of(user)).stream().findFirst().orElse(null);
    }

    public Map<Long, SysDept> loadDepartmentMap(List<SysUser> users) {
        List<Long> deptIds = users.stream()
                .map(SysUser::getDeptId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (CollectionUtils.isEmpty(deptIds)) {
            return Map.of();
        }
        return deptMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(SysDept::getDeptId, dept -> dept, (left, right) -> left));
    }

    private Map<Long, SysDept> loadParentDepartmentMap(Map<Long, SysDept> deptById) {
        List<Long> parentIds = deptById.values().stream()
                .map(SysDept::getPid)
                .filter(pid -> pid != null && pid > 0)
                .distinct()
                .toList();
        if (CollectionUtils.isEmpty(parentIds)) {
            return Map.of();
        }
        return EasyDataScopeContext.ignore(() -> deptMapper.selectBatchIds(parentIds)).stream()
                .collect(Collectors.toMap(SysDept::getDeptId, dept -> dept, (left, right) -> left));
    }

    private Map<Long, SysUser> loadRelatedUserMap(List<SysUser> users, Map<Long, SysDept> deptById, Map<Long, SysDept> parentDeptById) {
        Set<Long> userIds = new LinkedHashSet<>();
        users.stream()
                .map(SysUser::getManagerUserId)
                .filter(Objects::nonNull)
                .forEach(userIds::add);
        deptById.values().stream()
                .map(SysDept::getLeaderUserId)
                .filter(Objects::nonNull)
                .forEach(userIds::add);
        parentDeptById.values().stream()
                .map(SysDept::getLeaderUserId)
                .filter(Objects::nonNull)
                .forEach(userIds::add);
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return EasyDataScopeContext.ignore(() -> userMapper.selectBatchIds(userIds)).stream()
                .collect(Collectors.toMap(SysUser::getUserId, user -> user, (left, right) -> left));
    }

    private Map<Long, String> loadRoleIdsByUserId(List<SysUser> users) {
        return loadRoleBindingsByUserId(users).entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .map(role -> String.valueOf(role.getRoleId()))
                                .collect(Collectors.joining(","))
                ));
    }

    private Map<Long, List<UserRoleBinding>> loadRoleBindingsByUserId(List<SysUser> users) {
        List<Long> userIds = users.stream()
                .map(SysUser::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (CollectionUtils.isEmpty(userIds)) {
            return Map.of();
        }
        return sysUserRoleService.listRoleBindingsByUserIds(userIds).stream()
                .collect(Collectors.groupingBy(UserRoleBinding::getUserId));
    }
}
