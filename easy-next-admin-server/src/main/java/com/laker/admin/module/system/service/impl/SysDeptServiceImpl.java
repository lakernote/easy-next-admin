package com.laker.admin.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
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
import com.laker.admin.infrastructure.security.datascope.model.DataScopeType;
import com.laker.admin.module.system.dto.SystemDepartmentView;
import com.laker.admin.module.system.dto.SystemDeptQuery;
import com.laker.admin.module.system.entity.SysDept;
import com.laker.admin.module.system.entity.SysUser;
import com.laker.admin.module.system.mapper.SysDeptMapper;
import com.laker.admin.module.system.mapper.SysUserMapper;
import com.laker.admin.module.system.service.ISysDeptService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 组织架构服务实现。
 *
 * @author laker
 * @since 2021-08-11
 */
@Service
public class SysDeptServiceImpl extends ServiceImpl<SysDeptMapper, SysDept> implements ISysDeptService {
    private static final long ROOT_PARENT_ID = 0L;
    private static final int DEFAULT_SORT = 99;

    private final SysUserMapper sysUserMapper;

    public SysDeptServiceImpl(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public PageResponse<SystemDepartmentView> pageDepartments(SystemDeptQuery query) {
        SystemDeptQuery actualQuery = query == null ? new SystemDeptQuery() : query;
        Page<SysDept> page = new Page<>(actualQuery.getCurrent(), actualQuery.getSize());
        LambdaQueryWrapper<SysDept> queryWrapper = new QueryWrapper<SysDept>().lambda();
        queryWrapper.eq(actualQuery.getPid() != null, SysDept::getPid, actualQuery.getPid())
                .eq(actualQuery.getStatus() != null, SysDept::getStatus, actualQuery.getStatus())
                .and(StringUtils.hasText(actualQuery.getKeyword()), wrapper -> wrapper
                        .like(SysDept::getDeptName, actualQuery.getKeyword())
                        .or().like(SysDept::getFullName, actualQuery.getKeyword()))
                .orderByAsc(SysDept::getTreePath, SysDept::getSort, SysDept::getDeptId);
        Page<SysDept> pageList = this.page(page, queryWrapper);
        return PageResponse.ok(SystemDepartmentView.fromList(pageList.getRecords()), pageList.getTotal());
    }

    @Override
    public List<SysDept> tree() {
        LambdaQueryWrapper<SysDept> queryWrapper = Wrappers.<SysDept>lambdaQuery()
                .orderByAsc(SysDept::getTreePath, SysDept::getSort, SysDept::getDeptId);
        return this.list(queryWrapper);
    }

    @Override
    public List<SysDept> enabledTree() {
        LambdaQueryWrapper<SysDept> queryWrapper = Wrappers.<SysDept>lambdaQuery()
                .eq(SysDept::getStatus, true)
                .orderByAsc(SysDept::getTreePath, SysDept::getSort, SysDept::getDeptId);
        return this.list(queryWrapper);
    }

    @Override
    @CacheEvict(cacheNames = EasyCacheConfig.CACHE_DATA_SCOPE_DEPT_TREE, key = "'activeDeptNodes'")
    @Transactional(rollbackFor = Exception.class)
    public boolean saveDepartment(SysDept department) {
        if (department == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "部门信息不能为空");
        }
        boolean creating = department.getDeptId() == null;
        normalizeDepartment(department);
        if (department.getDeptId() == null) {
            department.setDeptId(IdWorker.getId());
        }
        SysDept existing = creating ? null : loadVisibleDepartment(department.getDeptId(), "维护");
        validateLeaderUser(department.getLeaderUserId());
        applyHierarchy(department);
        boolean saved = this.saveOrUpdate(department);
        if (saved && existing != null && hierarchyMayAffectDescendants(existing, department)) {
            refreshDescendants(department);
        }
        return saved;
    }

    @Override
    @CacheEvict(cacheNames = EasyCacheConfig.CACHE_DATA_SCOPE_DEPT_TREE,
            key = "'activeDeptNodes'",
            condition = "#root.args[0] != null")
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDepartment(Long deptId) {
        if (deptId == null) {
            return true;
        }
        assertDepartmentCanBeDeleted(List.of(deptId));
        return this.removeById(deptId);
    }

    @Override
    @CacheEvict(cacheNames = EasyCacheConfig.CACHE_DATA_SCOPE_DEPT_TREE,
            key = "'activeDeptNodes'",
            condition = "#root.args[0] != null && !#root.args[0].isEmpty()")
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDepartments(Collection<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return true;
        }
        assertDepartmentCanBeDeleted(deptIds);
        return this.removeByIds(deptIds);
    }

    void normalizeDepartment(SysDept department) {
        department.setDeptName(trimToNull(department.getDeptName()));
        department.setFullName(trimToNull(department.getFullName()));
        department.setAddress(trimToNull(department.getAddress()));
        department.setRemark(trimToNull(department.getRemark()));
        if (department.getPid() == null) {
            department.setPid(ROOT_PARENT_ID);
        }
        if (department.getStatus() == null) {
            department.setStatus(true);
        }
        if (department.getSort() == null) {
            department.setSort(DEFAULT_SORT);
        }
    }

    private void validateLeaderUser(Long leaderUserId) {
        if (leaderUserId == null) {
            return;
        }
        Long count = sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUserId, leaderUserId)
                .eq(SysUser::getEnable, 1));
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "部门负责人不存在或已停用");
        }
    }

    private void applyHierarchy(SysDept department) {
        if (!StringUtils.hasText(department.getDeptName())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "部门名称不能为空");
        }
        Long deptId = department.getDeptId();
        Long parentId = department.getPid() == null ? ROOT_PARENT_ID : department.getPid();
        if (Objects.equals(deptId, parentId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上级部门不能选择自己");
        }
        if (parentId <= ROOT_PARENT_ID) {
            ensureRootParentAllowed();
            department.setPid(ROOT_PARENT_ID);
            department.setFullName(department.getDeptName());
            department.setTreePath(pathOf(deptId));
            return;
        }
        SysDept parent = loadVisibleDepartment(parentId, "选择上级");
        String parentPath = normalizePath(parent.getTreePath(), parent.getDeptId());
        if (parentPath.contains(pathOf(deptId))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上级部门不能选择当前部门的下级部门");
        }
        String parentFullName = StringUtils.hasText(parent.getFullName()) ? parent.getFullName() : parent.getDeptName();
        department.setFullName(parentFullName + " / " + department.getDeptName());
        department.setTreePath(parentPath + deptId + "/");
    }

    private boolean hierarchyMayAffectDescendants(SysDept before, SysDept after) {
        return !Objects.equals(before.getPid(), after.getPid())
                || !Objects.equals(before.getDeptName(), after.getDeptName())
                || !Objects.equals(before.getFullName(), after.getFullName())
                || !Objects.equals(before.getTreePath(), after.getTreePath());
    }

    private void refreshDescendants(SysDept parent) {
        List<SysDept> children = EasyDataScopeContext.ignore(() -> this.list(Wrappers.<SysDept>lambdaQuery()
                .eq(SysDept::getPid, parent.getDeptId())
                .orderByAsc(SysDept::getSort, SysDept::getDeptId)));
        for (SysDept child : children) {
            child.setFullName(parent.getFullName() + " / " + child.getDeptName());
            child.setTreePath(normalizePath(parent.getTreePath(), parent.getDeptId()) + child.getDeptId() + "/");
            this.updateById(child);
            refreshDescendants(child);
        }
    }

    private void assertDepartmentCanBeDeleted(Collection<Long> deptIds) {
        List<Long> actualIds = deptIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (actualIds.isEmpty()) {
            return;
        }
        actualIds.forEach(deptId -> loadVisibleDepartment(deptId, "删除"));
        Long childCount = EasyDataScopeContext.ignore(() -> this.count(Wrappers.<SysDept>lambdaQuery()
                .in(SysDept::getPid, actualIds)));
        if (childCount != null && childCount > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "存在下级部门，不能删除");
        }
        Long userCount = sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .in(SysUser::getDeptId, actualIds));
        if (userCount != null && userCount > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "部门下存在用户，不能删除");
        }
    }

    private String normalizePath(String treePath, Long deptId) {
        if (StringUtils.hasText(treePath)) {
            String actualPath = treePath.trim();
            return actualPath.endsWith("/") ? actualPath : actualPath + "/";
        }
        return pathOf(deptId);
    }

    private String pathOf(Long deptId) {
        return "/" + deptId + "/";
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String trimToNull(String value) {
        return blankToNull(value);
    }

    private SysDept loadVisibleDepartment(Long deptId, String actionName) {
        if (deptId == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "部门不存在");
        }
        SysDept department = this.lambdaQuery()
                .eq(SysDept::getDeptId, deptId)
                .one();
        if (department != null) {
            return department;
        }
        if (departmentExistsIgnoringScope(deptId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权" + actionName + "权限范围外的部门");
        }
        throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "部门不存在");
    }

    private boolean departmentExistsIgnoringScope(Long deptId) {
        return EasyDataScopeContext.ignore(() -> this.lambdaQuery()
                .eq(SysDept::getDeptId, deptId)
                .exists());
    }

    private void ensureRootParentAllowed() {
        AuthPrincipal principal = EasySecurityContext.getPrincipal();
        if (principal == null || principal.isSuperAdmin()
                || (principal.getDataScopes() != null && principal.getDataScopes().contains(DataScopeType.ALL))) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "不能在权限范围外的根组织下维护部门");
    }
}
