package com.laker.admin.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.common.model.PageResponse;
import com.laker.admin.config.cache.EasyCacheConfig;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.datascope.context.EasyDataScopeContext;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.infrastructure.security.service.PermissionVersionService;
import com.laker.admin.infrastructure.security.datascope.policy.DataScopeAssignmentPolicy;
import com.laker.admin.infrastructure.security.datascope.model.DataScopeType;
import com.laker.admin.module.audit.service.SensitiveAuditService;
import com.laker.admin.module.system.dto.RoleRequest;
import com.laker.admin.module.system.dto.RoleUserCount;
import com.laker.admin.module.system.dto.RolePermissionDto;
import com.laker.admin.module.system.dto.SystemRoleQuery;
import com.laker.admin.module.system.dto.SystemRoleView;
import com.laker.admin.module.system.dto.workbench.EnabledCountSummary;
import com.laker.admin.module.system.entity.SysPower;
import com.laker.admin.module.system.entity.SysDept;
import com.laker.admin.module.system.entity.SysRole;
import com.laker.admin.module.system.entity.SysRoleDept;
import com.laker.admin.module.system.entity.SysRolePower;
import com.laker.admin.module.system.mapper.SysRoleMapper;
import com.laker.admin.module.system.service.ISysDeptService;
import com.laker.admin.module.system.service.ISysMenuService;
import com.laker.admin.module.system.service.ISysRoleDeptService;
import com.laker.admin.module.system.service.ISysRolePowerService;
import com.laker.admin.module.system.service.ISysRoleService;
import com.laker.admin.module.system.service.ISysUserRoleService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author laker
 * @since 2021-08-11
 */
@Service
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements ISysRoleService {
    private static final String DATA_SCOPE_CUSTOM_DEPT = DataScopeType.DEPT_SETS.getCode();
    private static final String BUILT_IN_ADMIN_ROLE_CODE = "admin";
    private static final int LOWEST_ROLE_LEVEL = Integer.MAX_VALUE;

    private final ISysMenuService sysMenuService;
    private final ISysRolePowerService sysRolePowerService;
    private final ISysRoleDeptService sysRoleDeptService;
    private final ISysUserRoleService sysUserRoleService;
    private final ISysDeptService sysDeptService;
    private final PermissionVersionService permissionVersionService;
    private final DataScopeAssignmentPolicy dataScopeAssignmentPolicy;
    private final SensitiveAuditService sensitiveAuditService;

    public SysRoleServiceImpl(ISysMenuService sysMenuService,
                              ISysRolePowerService sysRolePowerService,
                              ISysRoleDeptService sysRoleDeptService,
                              ISysUserRoleService sysUserRoleService,
                              ISysDeptService sysDeptService,
                              PermissionVersionService permissionVersionService,
                              DataScopeAssignmentPolicy dataScopeAssignmentPolicy,
                              SensitiveAuditService sensitiveAuditService) {
        this.sysMenuService = sysMenuService;
        this.sysRolePowerService = sysRolePowerService;
        this.sysRoleDeptService = sysRoleDeptService;
        this.sysUserRoleService = sysUserRoleService;
        this.sysDeptService = sysDeptService;
        this.permissionVersionService = permissionVersionService;
        this.dataScopeAssignmentPolicy = dataScopeAssignmentPolicy;
        this.sensitiveAuditService = sensitiveAuditService;
    }

    @Override
    @CacheEvict(cacheNames = EasyCacheConfig.CACHE_DATA_SCOPE_CUSTOM_DEPT_IDS,
            allEntries = true,
            condition = "#root.args[0] != null && #root.args[0].dataScope != null")
    @Transactional(rollbackFor = Exception.class)
    public boolean saveOrUpdate(SysRole entity) {
        boolean saved = super.saveOrUpdate(entity);
        if (saved && entity.getRoleId() != null) {
            permissionVersionService.increaseForRole(entity.getRoleId());
            sensitiveAuditService.record("角色权限", "保存角色", "ROLE", String.valueOf(entity.getRoleId()),
                    "{\"roleCode\":\"" + safe(entity.getRoleCode()) + "\"}");
        }
        return saved;
    }

    @Override
    public PageResponse<SystemRoleView> pageRoles(SystemRoleQuery query) {
        SystemRoleQuery actualQuery = query == null ? new SystemRoleQuery() : query;
        Page<SysRole> page = new Page<>(actualQuery.getCurrent(), actualQuery.getSize());
        LambdaQueryWrapper<SysRole> queryWrapper = new QueryWrapper<SysRole>().lambda();
        queryWrapper.eq(actualQuery.getEnable() != null, SysRole::getEnable, actualQuery.getEnable())
                .and(StringUtils.isNotBlank(actualQuery.getKeyword()), wrapper -> wrapper
                        .like(SysRole::getRoleName, actualQuery.getKeyword())
                        .or()
                        .like(SysRole::getRoleCode, actualQuery.getKeyword()));
        Page<SysRole> pageList = this.page(page, queryWrapper);
        fillRoleUserCount(pageList.getRecords());
        return PageResponse.ok(pageList.getRecords().stream().map(SystemRoleView::from).toList(), pageList.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SystemRoleView saveRole(RoleRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "角色信息不能为空");
        }
        validateRoleRequest(request);
        SysRole role = new SysRole();
        role.setRoleId(request.getRoleId());
        role.setRoleName(request.getRoleName());
        role.setRoleCode(request.getRoleCode());
        role.setDetails(request.getDetails());
        role.setEnable(request.getEnable());
        role.setRoleLevel(request.getRoleLevel());
        boolean saved = saveOrUpdate(role);
        if (!saved) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "角色保存失败");
        }
        return SystemRoleView.from(this.getById(role.getRoleId()));
    }

    private void validateRoleRequest(RoleRequest request) {
        if (StringUtils.isBlank(request.getRoleName())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "角色名称不能为空");
        }
        if (StringUtils.isBlank(request.getRoleCode())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "权限编码不能为空");
        }
        if (request.getRoleId() != null) {
            SysRole existingRole = this.getById(request.getRoleId());
            if (existingRole == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "角色不存在");
            }
            ensureManageableRole(existingRole, "修改");
        }
        validateRoleLevelAssignable(request.getRoleLevel(), request.getRoleId() == null ? "创建" : "修改");
        boolean exists = this.lambdaQuery()
                .eq(SysRole::getRoleCode, request.getRoleCode())
                .ne(request.getRoleId() != null, SysRole::getRoleId, request.getRoleId())
                .exists();
        if (exists) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "角色编码已存在");
        }
    }

    @Override
    public SystemRoleView getRole(Long roleId) {
        SysRole role = this.getById(roleId);
        if (role == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "角色不存在");
        }
        return SystemRoleView.from(role);
    }

    @Override
    public RolePermissionDto getRolePermissions(Long roleId) {
        SysRole role = this.getById(roleId);
        if (role == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "角色不存在");
        }
        return RolePermissionDto.builder()
                .roleId(roleId)
                .dataScope(normalizeDataScope(role.getDataScope()))
                .deptIds(roleDeptIds(roleId))
                .permissionCodes(sysRolePowerService.listPermissionCodesByRoleId(roleId))
                .assignableDataScopes(new ArrayList<>(assignableDataScopeCodes(EasySecurityContext.getPrincipal())))
                .roleUserCount(countRoleUsers(roleId))
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveRolePermissions(Long roleId, RolePermissionDto request) {
        RolePermissionDto actualRequest = request == null ? new RolePermissionDto() : request;
        SysRole role = this.getById(roleId);
        if (role == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "角色不存在");
        }
        ensureManageableRole(role, "授权配置");
        String previousDataScope = normalizeDataScope(role.getDataScope());
        List<Long> previousDeptIds = roleDeptIds(roleId);
        validateDataScope(actualRequest);
        List<String> permissionCodes = actualRequest.getPermissionCodes() == null ? List.of() : actualRequest.getPermissionCodes().stream()
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        List<SysPower> powers = List.of();
        if (!permissionCodes.isEmpty()) {
            powers = sysMenuService.list(Wrappers.<SysPower>lambdaQuery()
                    .in(SysPower::getPowerCode, permissionCodes)
                    .eq(SysPower::getEnable, true));
            validatePermissionCodes(permissionCodes, powers);
            validateAssignablePermissionCodes(permissionCodes);
        }
        if (StringUtils.isNotBlank(actualRequest.getDataScope())) {
            updateRoleDataScope(roleId, actualRequest.getDataScope());
        }
        saveRoleDepartments(roleId, actualRequest);
        sysRolePowerService.deleteByRoleId(roleId);
        if (permissionCodes.isEmpty()) {
            permissionVersionService.increaseForRole(roleId);
            recordRoleAuthorization(roleId, permissionCodes.size(), previousDataScope, previousDeptIds, actualRequest);
            return true;
        }
        List<Long> powerIds = expandPowerIdsWithAncestors(powers);
        List<SysRolePower> rolePowers = new ArrayList<>();
        powerIds.forEach(powerId -> {
            SysRolePower rolePower = new SysRolePower();
            rolePower.setRoleId(roleId);
            rolePower.setPowerId(powerId);
            rolePowers.add(rolePower);
        });
        boolean saved = rolePowers.isEmpty() || sysRolePowerService.saveBatch(rolePowers);
        if (saved) {
            permissionVersionService.increaseForRole(roleId);
            recordRoleAuthorization(roleId, permissionCodes.size(), previousDataScope, previousDeptIds, actualRequest);
        }
        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteRole(Long roleId) {
        SysRole role = this.getById(roleId);
        if (role == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "角色不存在");
        }
        ensureManageableRole(role, "删除");
        long userCount = countRoleUsers(roleId);
        if (userCount > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "角色下存在用户，不能删除");
        }
        permissionVersionService.increaseForRole(roleId);
        sysRolePowerService.deleteByRoleId(roleId);
        sysRoleDeptService.deleteByRoleId(roleId);
        sysUserRoleService.deleteByRoleId(roleId);
        boolean deleted = this.removeById(roleId);
        if (deleted) {
            sensitiveAuditService.record("角色权限", "删除角色", "ROLE", String.valueOf(roleId), "{\"deleted\":true}");
        }
        return deleted;
    }

    @Override
    public EnabledCountSummary countEnabledSummary() {
        return baseMapper.selectEnabledCountSummary();
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void recordRoleAuthorization(Long roleId,
                                         int permissionCount,
                                         String previousDataScope,
                                         List<Long> previousDeptIds,
                                         RolePermissionDto request) {
        String nextDataScope = StringUtils.isBlank(request.getDataScope()) ? previousDataScope : request.getDataScope();
        int previousDeptCount = normalizedDeptIds(previousDeptIds).size();
        int nextDeptCount = StringUtils.isBlank(request.getDataScope())
                ? previousDeptCount
                : (DATA_SCOPE_CUSTOM_DEPT.equals(nextDataScope) ? normalizedDeptIds(request.getDeptIds()).size() : 0);
        sensitiveAuditService.record("角色权限", "保存角色授权", "ROLE", String.valueOf(roleId),
                "{\"permissionCount\":" + permissionCount
                        + ",\"dataScopeBefore\":\"" + safe(previousDataScope) + "\""
                        + ",\"dataScopeAfter\":\"" + safe(nextDataScope) + "\""
                        + ",\"deptCountBefore\":" + previousDeptCount
                        + ",\"deptCountAfter\":" + nextDeptCount + "}");
    }

    private List<Long> roleDeptIds(Long roleId) {
        if (roleId == null) {
            return List.of();
        }
        return sysRoleDeptService.listDeptIdsByRoleId(roleId);
    }

    private void validateDataScope(RolePermissionDto request) {
        if (StringUtils.isBlank(request.getDataScope())) {
            return;
        }
        // 入库前统一校验并归一为标准 code；中文 label 不进入数据库和审计日志。
        String normalizedDataScope = normalizeDataScope(request.getDataScope());
        if (normalizedDataScope == null || !DataScopeType.roleDataScopeCodes().contains(normalizedDataScope)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "数据范围不正确");
        }
        request.setDataScope(normalizedDataScope);
        validateAssignableDataScope(request.getDataScope());
        if (DATA_SCOPE_CUSTOM_DEPT.equals(request.getDataScope()) && normalizedDeptIds(request.getDeptIds()).isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请选择自定义部门范围");
        }
        if (DATA_SCOPE_CUSTOM_DEPT.equals(request.getDataScope())) {
            validateCustomDeptIds(request.getDeptIds());
        }
    }

    private void validateCustomDeptIds(List<Long> deptIds) {
        List<Long> normalizedDeptIds = normalizedDeptIds(deptIds);
        List<SysDept> departments = EasyDataScopeContext.ignore(() -> sysDeptService.listByIds(normalizedDeptIds));
        Set<Long> enabledDeptIds = departments.stream()
                .filter(dept -> Boolean.TRUE.equals(dept.getStatus()))
                .map(SysDept::getDeptId)
                .collect(Collectors.toSet());
        List<Long> invalidDeptIds = normalizedDeptIds.stream()
                .filter(deptId -> !enabledDeptIds.contains(deptId))
                .toList();
        if (!invalidDeptIds.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "存在无效或已停用部门：" + invalidDeptIds);
        }
        if (canAssignAllDataScope()) {
            return;
        }
        Set<Long> visibleDeptIds = sysDeptService.list(Wrappers.<SysDept>lambdaQuery()
                        .in(SysDept::getDeptId, normalizedDeptIds))
                .stream()
                .map(SysDept::getDeptId)
                .collect(Collectors.toSet());
        List<Long> forbiddenDeptIds = normalizedDeptIds.stream()
                .filter(deptId -> !visibleDeptIds.contains(deptId))
                .toList();
        if (!forbiddenDeptIds.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能授权权限范围外的自定义部门：" + forbiddenDeptIds);
        }
    }

    private void ensureMutableRole(SysRole role, String actionName) {
        if (BUILT_IN_ADMIN_ROLE_CODE.equals(role.getRoleCode())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "内置超级管理员角色不能" + actionName);
        }
    }

    private void ensureManageableRole(SysRole role, String actionName) {
        ensureMutableRole(role, actionName);
        if (isUnrestrictedOperator()) {
            return;
        }
        Long currentUserId = EasySecurityContext.getUserId();
        if (currentUserId != null && sysUserRoleService.listRoleIdsByUserId(currentUserId).contains(role.getRoleId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能" + actionName + "当前登录账号持有的角色");
        }
        if (roleLevel(role) <= currentMinimumRoleLevel()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能" + actionName + "高于或等于当前账号级别的角色");
        }
    }

    private void validatePermissionCodes(List<String> permissionCodes, List<SysPower> powers) {
        Set<String> existingCodes = powers.stream()
                .map(SysPower::getPowerCode)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
        List<String> invalidCodes = permissionCodes.stream()
                .filter(code -> !existingCodes.contains(code))
                .toList();
        if (!invalidCodes.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "存在无效权限码：" + String.join("、", invalidCodes));
        }
    }

    private void validateAssignablePermissionCodes(List<String> permissionCodes) {
        if (isUnrestrictedOperator()) {
            return;
        }
        AuthPrincipal principal = EasySecurityContext.getPrincipal();
        Set<String> ownedPermissions = principal == null || principal.getPermissions() == null
                ? Set.of()
                : Set.copyOf(principal.getPermissions());
        List<String> forbiddenCodes = permissionCodes.stream()
                .filter(code -> !ownedPermissions.contains(code))
                .toList();
        if (!forbiddenCodes.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能授予当前账号未持有的权限：" + String.join("、", forbiddenCodes));
        }
    }

    private void validateAssignableDataScope(String dataScope) {
        if (StringUtils.isBlank(dataScope)) {
            return;
        }
        if (!assignableDataScopeCodes(EasySecurityContext.getPrincipal()).contains(dataScope)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能授予高于当前账号的数据范围");
        }
    }

    private void validateRoleLevelAssignable(Integer roleLevel, String actionName) {
        if (isUnrestrictedOperator()) {
            return;
        }
        int requestedLevel = roleLevel == null ? LOWEST_ROLE_LEVEL : roleLevel;
        if (requestedLevel <= currentMinimumRoleLevel()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能" + actionName + "高于或等于当前账号级别的角色");
        }
    }

    private long countRoleUsers(Long roleId) {
        List<RoleUserCount> counts = sysUserRoleService.countUsersByRoleIds(List.of(roleId));
        if (counts == null) {
            return 0L;
        }
        return counts.stream()
                .findFirst()
                .map(RoleUserCount::getUserCount)
                .orElse(0L);
    }

    private void saveRoleDepartments(Long roleId, RolePermissionDto request) {
        if (StringUtils.isBlank(request.getDataScope())) {
            return;
        }
        sysRoleDeptService.deleteByRoleId(roleId);
        if (!DATA_SCOPE_CUSTOM_DEPT.equals(request.getDataScope())) {
            return;
        }
        List<Long> deptIds = normalizedDeptIds(request.getDeptIds());
        if (deptIds.isEmpty()) {
            return;
        }
        List<SysRoleDept> roleDepts = deptIds.stream()
                .map(deptId -> {
                    SysRoleDept roleDept = new SysRoleDept();
                    roleDept.setRoleId(roleId);
                    roleDept.setDeptId(deptId);
                    return roleDept;
                })
                .toList();
        sysRoleDeptService.saveBatch(roleDepts);
    }

    protected void updateRoleDataScope(Long roleId, String dataScope) {
        this.update(Wrappers.<SysRole>lambdaUpdate()
                .set(SysRole::getDataScope, dataScope)
                .eq(SysRole::getRoleId, roleId));
    }

    private String normalizeDataScope(String dataScope) {
        // 只接受 DataScopeType 暴露的标准 code，避免二开时把中文展示值当作业务枚举存储。
        return DataScopeType.resolveRoleDataScope(dataScope)
                .map(DataScopeType::getCode)
                .orElse(null);
    }

    private List<Long> normalizedDeptIds(List<Long> deptIds) {
        if (deptIds == null) {
            return List.of();
        }
        return deptIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private boolean isUnrestrictedOperator() {
        AuthPrincipal principal = EasySecurityContext.getPrincipal();
        return principal == null || principal.isSuperAdmin();
    }

    private boolean canAssignAllDataScope() {
        return dataScopeAssignmentPolicy.canAssignAll(EasySecurityContext.getPrincipal());
    }

    Set<String> assignableDataScopeCodes(AuthPrincipal principal) {
        return dataScopeAssignmentPolicy.assignableCodes(principal);
    }

    private int currentMinimumRoleLevel() {
        AuthPrincipal principal = EasySecurityContext.getPrincipal();
        if (principal == null || principal.getUserId() == null) {
            return LOWEST_ROLE_LEVEL;
        }
        List<Long> roleIds = sysUserRoleService.listRoleIdsByUserId(principal.getUserId());
        if (roleIds.isEmpty()) {
            return LOWEST_ROLE_LEVEL;
        }
        return this.list(Wrappers.<SysRole>lambdaQuery()
                        .in(SysRole::getRoleId, roleIds))
                .stream()
                .map(this::roleLevel)
                .min(Integer::compareTo)
                .orElse(LOWEST_ROLE_LEVEL);
    }

    private int roleLevel(SysRole role) {
        return role == null || role.getRoleLevel() == null ? LOWEST_ROLE_LEVEL : role.getRoleLevel();
    }

    private List<Long> expandPowerIdsWithAncestors(List<SysPower> powers) {
        if (powers == null || powers.isEmpty()) {
            return List.of();
        }
        Map<Long, SysPower> resourceMap = sysMenuService.list()
                .stream()
                .filter(power -> power.getMenuId() != null)
                .collect(Collectors.toMap(SysPower::getMenuId, power -> power, (left, right) -> left));
        Set<Long> powerIds = new LinkedHashSet<>();
        powers.forEach(power -> addPowerAndAncestors(power, resourceMap, powerIds));
        return new ArrayList<>(powerIds);
    }

    private void addPowerAndAncestors(SysPower power, Map<Long, SysPower> resourceMap, Set<Long> powerIds) {
        SysPower current = power;
        while (current != null && current.getMenuId() != null) {
            powerIds.add(current.getMenuId());
            Long parentId = current.getPid();
            if (parentId == null || parentId <= 0) {
                return;
            }
            current = resourceMap.get(parentId);
        }
    }

    private void fillRoleUserCount(List<SysRole> roles) {
        if (roles == null || roles.isEmpty()) {
            return;
        }
        List<Long> roleIds = roles.stream()
                .map(SysRole::getRoleId)
                .filter(Objects::nonNull)
                .toList();
        if (roleIds.isEmpty()) {
            return;
        }
        Map<Long, Long> countMap = sysUserRoleService.countUsersByRoleIds(roleIds).stream()
                .collect(Collectors.toMap(RoleUserCount::getRoleId, RoleUserCount::getUserCount, (left, right) -> left));
        roles.forEach(role -> role.setUserCount(countMap.getOrDefault(role.getRoleId(), 0L)));
    }

}
