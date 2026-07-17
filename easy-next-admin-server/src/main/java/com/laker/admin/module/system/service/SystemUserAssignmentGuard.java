package com.laker.admin.module.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.module.system.entity.SysDept;
import com.laker.admin.module.system.entity.SysRole;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户管理里的部门、角色分配边界集中在这里，避免导入、创建、编辑各自散落校验规则。
 */
@Service
public class SystemUserAssignmentGuard {
    private static final String BUILT_IN_ADMIN_ROLE_CODE = "admin";
    private static final int LOWEST_ROLE_LEVEL = Integer.MAX_VALUE;

    private final ISysDeptService sysDeptService;
    private final ISysRoleService sysRoleService;
    private final ISysUserRoleService sysUserRoleService;

    public SystemUserAssignmentGuard(ISysDeptService sysDeptService,
                                     ISysRoleService sysRoleService,
                                     ISysUserRoleService sysUserRoleService) {
        this.sysDeptService = sysDeptService;
        this.sysRoleService = sysRoleService;
        this.sysUserRoleService = sysUserRoleService;
    }

    public void validateAssignableDepartment(Long deptId, boolean required) {
        if (deptId == null) {
            if (required) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请选择部门");
            }
            return;
        }
        SysDept dept = sysDeptService.getById(deptId);
        if (dept == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "部门不存在");
        }
        if (!Boolean.TRUE.equals(dept.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "部门已停用");
        }
        var principal = EasySecurityContext.getPrincipal();
        if (principal == null || principal.isSuperAdmin()) {
            return;
        }
        Set<Long> allowedDeptIds = principal.getDeptIds();
        if (CollectionUtils.isEmpty(allowedDeptIds) || !allowedDeptIds.contains(deptId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能分配权限范围外的部门");
        }
    }

    public void validateAssignableRoleIds(Collection<Long> roleIds) {
        Set<Long> normalizedRoleIds = normalizeRoleIds(roleIds);
        if (normalizedRoleIds.isEmpty()) {
            return;
        }
        List<SysRole> roles = validateExistingRoles(normalizedRoleIds);
        validateEnabledRoles(roles);
        validateAssignableRoles(roles);
    }

    public void validateRoleUpdate(Collection<Long> existingRoleIds, Collection<Long> requestedRoleIds) {
        Set<Long> requested = normalizeRoleIds(requestedRoleIds);
        if (requested.isEmpty()) {
            return;
        }
        List<SysRole> requestedRoles = validateExistingRoles(requested);
        Set<Long> existing = normalizeRoleIds(existingRoleIds);
        Set<Long> added = requested.stream()
                .filter(roleId -> !existing.contains(roleId))
                .collect(Collectors.toSet());
        if (!added.isEmpty()) {
            List<SysRole> addedRoles = requestedRoles.stream()
                    .filter(role -> added.contains(role.getRoleId()))
                    .toList();
            validateEnabledRoles(addedRoles);
            validateAssignableRoles(addedRoles);
        }
        if (isUnrestrictedOperator() || existing.isEmpty()) {
            return;
        }
        Set<Long> removed = existing.stream()
                .filter(roleId -> !requested.contains(roleId))
                .collect(Collectors.toSet());
        List<String> protectedRemovedRoleCodes = listRolesByIds(removed).stream()
                .filter(role -> BUILT_IN_ADMIN_ROLE_CODE.equals(role.getRoleCode()) || !isAssignableRole(role))
                .map(SysRole::getRoleCode)
                .filter(Objects::nonNull)
                .toList();
        if (!protectedRemovedRoleCodes.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能移除高权限账号的既有角色：" + String.join("、", protectedRemovedRoleCodes));
        }
    }

    private List<SysRole> validateExistingRoles(Collection<Long> normalizedRoleIds) {
        List<SysRole> roles = listRolesByIds(normalizedRoleIds);
        Set<Long> existingRoleIds = roles.stream().map(SysRole::getRoleId).collect(Collectors.toSet());
        List<Long> missingRoleIds = normalizedRoleIds.stream()
                .filter(roleId -> !existingRoleIds.contains(roleId))
                .toList();
        if (!missingRoleIds.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "存在无效角色：" + missingRoleIds);
        }
        return roles;
    }

    private void validateEnabledRoles(List<SysRole> roles) {
        List<Long> disabledRoleIds = roles.stream()
                .filter(role -> !Boolean.TRUE.equals(role.getEnable()))
                .map(SysRole::getRoleId)
                .toList();
        if (!disabledRoleIds.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "存在已停用角色：" + disabledRoleIds);
        }
    }

    private void validateAssignableRoles(List<SysRole> roles) {
        List<String> forbiddenRoleCodes = roles.stream()
                .filter(role -> !isAssignableRole(role))
                .map(SysRole::getRoleCode)
                .filter(Objects::nonNull)
                .toList();
        if (!forbiddenRoleCodes.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能分配高于或等于当前账号级别的角色：" + String.join("、", forbiddenRoleCodes));
        }
    }

    public void validateManageableRoleIds(Collection<Long> roleIds, String actionName) {
        if (CollectionUtils.isEmpty(roleIds) || isUnrestrictedOperator()) {
            return;
        }
        List<SysRole> roles = listRolesByIds(roleIds);
        List<String> protectedRoleCodes = roles.stream()
                .filter(role -> BUILT_IN_ADMIN_ROLE_CODE.equals(role.getRoleCode()) || !isAssignableRole(role))
                .map(SysRole::getRoleCode)
                .filter(Objects::nonNull)
                .toList();
        if (!protectedRoleCodes.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能" + actionName + "高权限账号");
        }
    }

    public List<SysRole> listAssignableRoles(Collection<Long> assignedRoleIds) {
        Set<Long> assigned = assignedRoleIds == null ? Set.of() : assignedRoleIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return sysRoleService.list(Wrappers.<SysRole>lambdaQuery()
                        .and(!assigned.isEmpty(), wrapper -> wrapper
                                .eq(SysRole::getEnable, true)
                                .or()
                                .in(SysRole::getRoleId, assigned))
                        .eq(assigned.isEmpty(), SysRole::getEnable, true)
                        .orderByAsc(SysRole::getRoleLevel, SysRole::getRoleId))
                .stream()
                .filter(role -> assigned.contains(role.getRoleId()) || isAssignableRole(role))
                .toList();
    }

    private List<SysRole> listRolesByIds(Collection<Long> roleIds) {
        if (CollectionUtils.isEmpty(roleIds)) {
            return List.of();
        }
        return sysRoleService.list(Wrappers.<SysRole>lambdaQuery()
                .in(SysRole::getRoleId, roleIds));
    }

    private Set<Long> normalizeRoleIds(Collection<Long> roleIds) {
        if (CollectionUtils.isEmpty(roleIds)) {
            return Set.of();
        }
        return roleIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private boolean isAssignableRole(SysRole role) {
        if (role == null) {
            return false;
        }
        if (isUnrestrictedOperator()) {
            return true;
        }
        if (BUILT_IN_ADMIN_ROLE_CODE.equals(role.getRoleCode())) {
            return false;
        }
        return roleLevel(role) > currentMinimumRoleLevel();
    }

    private boolean isUnrestrictedOperator() {
        var principal = EasySecurityContext.getPrincipal();
        return principal == null || principal.isSuperAdmin();
    }

    private int currentMinimumRoleLevel() {
        var principal = EasySecurityContext.getPrincipal();
        if (principal == null || principal.getUserId() == null) {
            return LOWEST_ROLE_LEVEL;
        }
        List<Long> currentRoleIds = sysUserRoleService.listRoleIdsByUserId(principal.getUserId());
        if (CollectionUtils.isEmpty(currentRoleIds)) {
            return LOWEST_ROLE_LEVEL;
        }
        return listRolesByIds(currentRoleIds).stream()
                .map(this::roleLevel)
                .min(Comparator.naturalOrder())
                .orElse(LOWEST_ROLE_LEVEL);
    }

    private int roleLevel(SysRole role) {
        return role.getRoleLevel() == null ? LOWEST_ROLE_LEVEL : role.getRoleLevel();
    }
}
