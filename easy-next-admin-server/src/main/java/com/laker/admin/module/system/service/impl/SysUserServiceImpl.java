package com.laker.admin.module.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.common.model.PageResponse;
import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.infrastructure.observability.trace.EasyTrace;
import com.laker.admin.infrastructure.observability.trace.SpanType;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.datascope.context.EasyDataScopeContext;
import com.laker.admin.infrastructure.security.service.PermissionVersionService;
import com.laker.admin.infrastructure.security.support.EasyPasswordHasher;
import com.laker.admin.module.system.dto.FlowAssigneVo;
import com.laker.admin.module.system.dto.SystemRoleView;
import com.laker.admin.module.system.dto.SystemUserQuery;
import com.laker.admin.module.system.dto.SystemUserView;
import com.laker.admin.module.system.dto.UserStatusRequest;
import com.laker.admin.module.system.dto.user.UserBO;
import com.laker.admin.module.system.dto.user.UserImportResult;
import com.laker.admin.module.system.dto.user.UserRequest;
import com.laker.admin.module.system.dto.workbench.EnabledCountSummary;
import com.laker.admin.module.system.entity.SysDept;
import com.laker.admin.module.system.entity.SysRole;
import com.laker.admin.module.system.entity.SysUser;
import com.laker.admin.module.system.entity.SysUserRole;
import com.laker.admin.module.system.mapper.SysUserMapper;
import com.laker.admin.module.system.mapstruct.UserBeanMap;
import com.laker.admin.module.system.service.ISysDeptService;
import com.laker.admin.module.system.service.ISysRoleService;
import com.laker.admin.module.system.service.ISysUserService;
import com.laker.admin.module.system.service.ISysUserRoleService;
import com.laker.admin.module.system.service.SystemUserAssignmentGuard;
import com.laker.admin.module.system.service.SysUserImportExportService;
import com.laker.admin.module.system.service.SysUserRelationService;
import com.laker.admin.module.system.support.UserQuerySupport;
import com.laker.admin.module.system.support.UserRequestNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author laker
 * @since 2021-08-05
 */
@Service
@EasyTrace(spanType = SpanType.Service)
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements ISysUserService {
    private static final String BUILT_IN_ADMIN_USER_NAME = "admin";

    private final UserBeanMap userBeanMap;
    private final ISysDeptService sysDeptService;
    private final ISysRoleService sysRoleService;
    private final ISysUserRoleService sysUserRoleService;
    private final SystemUserAssignmentGuard assignmentGuard;
    private final SysUserImportExportService userImportExportService;
    private final SysUserRelationService userRelationService;
    private final EasyNextAdminConfig easyNextAdminConfig;
    private final EasyPasswordHasher passwordHasher;
    private final PermissionVersionService permissionVersionService;

    public SysUserServiceImpl(UserBeanMap userBeanMap,
                              ISysDeptService sysDeptService,
                              ISysRoleService sysRoleService,
                              ISysUserRoleService sysUserRoleService,
                              SystemUserAssignmentGuard assignmentGuard,
                              SysUserImportExportService userImportExportService,
                              SysUserRelationService userRelationService,
                              EasyNextAdminConfig easyNextAdminConfig,
                              EasyPasswordHasher passwordHasher,
                              PermissionVersionService permissionVersionService) {
        this.userBeanMap = userBeanMap;
        this.sysDeptService = sysDeptService;
        this.sysRoleService = sysRoleService;
        this.sysUserRoleService = sysUserRoleService;
        this.assignmentGuard = assignmentGuard;
        this.userImportExportService = userImportExportService;
        this.userRelationService = userRelationService;
        this.easyNextAdminConfig = easyNextAdminConfig;
        this.passwordHasher = passwordHasher;
        this.permissionVersionService = permissionVersionService;
    }

    @Override
    public PageResponse<SystemUserView> pageUsers(SystemUserQuery query) {
        SystemUserQuery actualQuery = query == null ? new SystemUserQuery() : query;
        Page<SysUser> page = new Page<>(actualQuery.getPage(), actualQuery.getLimit());
        Page<SysUser> pageList = this.page(page, UserQuerySupport.buildUserQuery(actualQuery));
        return PageResponse.ok(userRelationService.toUserViews(pageList.getRecords()), pageList.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SystemUserView saveUser(UserRequest request) {
        if (request != null && request.getUserId() != null) {
            return updateUser(request.getUserId(), request);
        }
        return createUser(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SystemUserView createUser(UserRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "用户信息不能为空");
        }
        if (request.getUserId() != null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "新增用户不能传入用户编号");
        }
        UserRequestNormalizer.normalize(request);
        validateUserRequest(request);
        List<Long> roleIds = request.getRoleIds() == null ? null : normalizeRoleIds(request.getRoleIds());
        if (roleIds != null) {
            assignmentGuard.validateAssignableRoleIds(roleIds);
        }
        String password = StringUtils.hasText(request.getPassword()) ? request.getPassword() : easyNextAdminConfig.getDefaultPwd();
        request.setPassword(passwordHasher.hash(password));
        UserBO userBO = userBeanMap.requestToBo(request);
        boolean saved = this.save(userBO);
        if (roleIds != null) {
            saveUserRoles(userBO.getUserId(), roleIds);
        }
        permissionVersionService.increaseForUser(userBO.getUserId());
        if (!saved) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存用户失败");
        }
        return getUserAndDeptById(userBO.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SystemUserView updateUser(Long userId, UserRequest request) {
        if (userId == null || request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "用户信息不能为空");
        }
        if (request.getUserId() != null && !Objects.equals(userId, request.getUserId())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "用户编号不一致");
        }
        SysUser existingUser = loadVisibleUser(userId, "修改");
        ensureUserManageable(existingUser, "修改");
        if (Integer.valueOf(0).equals(request.getEnable())) {
            ensureMutableUser(existingUser, false);
        }
        request.setUserId(userId);
        UserRequestNormalizer.normalize(request);
        validateUserRequest(request);
        List<Long> roleIds = request.getRoleIds() == null ? null : normalizeRoleIds(request.getRoleIds());
        if (roleIds != null) {
            assignmentGuard.validateRoleUpdate(sysUserRoleService.listRoleIdsByUserId(userId), roleIds);
        }
        if (StringUtils.hasText(request.getPassword())) {
            request.setPassword(passwordHasher.hash(request.getPassword()));
        } else {
            request.setPassword(null);
        }
        boolean updated = this.saveOrUpdate(userBeanMap.requestToEntity(request));
        if (roleIds != null) {
            saveUserRoles(userId, roleIds);
        }
        if (updated) {
            permissionVersionService.increaseForUser(userId);
        }
        if (!updated) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存用户失败");
        }
        return getUserAndDeptById(userId);
    }

    private void validateUserRequest(UserRequest request) {
        if (!StringUtils.hasText(request.getUserName())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "用户名不能为空");
        }
        boolean existsUserName = this.lambdaQuery()
                .eq(SysUser::getUserName, request.getUserName())
                .ne(request.getUserId() != null, SysUser::getUserId, request.getUserId())
                .exists();
        if (existsUserName) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "用户名已存在");
        }
        if (StringUtils.hasText(request.getEmployeeNo())) {
            boolean existsEmployeeNo = this.lambdaQuery()
                    .eq(SysUser::getEmployeeNo, request.getEmployeeNo())
                    .ne(request.getUserId() != null, SysUser::getUserId, request.getUserId())
                    .exists();
            if (existsEmployeeNo) {
                throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "员工编号已存在");
            }
        }
        assignmentGuard.validateAssignableDepartment(request.getDeptId(), true);
        validateManagerUser(request);
        validateUserEnable(request.getEnable());
    }

    private void validateManagerUser(UserRequest request) {
        Long managerUserId = request.getManagerUserId();
        if (managerUserId == null) {
            return;
        }
        if (Objects.equals(request.getUserId(), managerUserId)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "直属上级不能选择当前用户");
        }
        boolean managerExists = EasyDataScopeContext.ignore(() -> this.lambdaQuery()
                .eq(SysUser::getUserId, managerUserId)
                .eq(SysUser::getEnable, 1)
                .exists());
        if (!managerExists) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "直属上级不存在或已停用");
        }
    }

    private void validateUserEnable(Integer enable) {
        if (enable == null) {
            return;
        }
        if (enable != 0 && enable != 1) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "用户状态不正确");
        }
    }

    private List<Long> normalizeRoleIds(String roleIdsText) {
        return UserQuerySupport.splitRoleIds(roleIdsText).stream()
                .map(this::parseRoleId)
                .distinct()
                .toList();
    }

    private Long parseRoleId(String roleId) {
        try {
            return Long.valueOf(roleId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "角色编号不正确");
        }
    }

    @Override
    public byte[] userImportTemplate() {
        return userImportExportService.userImportTemplate();
    }

    @Override
    public UserImportResult importUsers(MultipartFile file) {
        return userImportExportService.importUsers(file);
    }

    @Override
    public byte[] exportUsers(SystemUserQuery query) {
        return userImportExportService.exportUsers(query);
    }

    @Override
    public boolean switchUserStatus(UserStatusRequest request) {
        if (request == null || request.getUserId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "用户不存在");
        }
        if (request.getEnable() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "用户状态不能为空");
        }
        validateUserEnable(request.getEnable());
        SysUser existingUser = loadVisibleUser(request.getUserId(), "启停");
        if (request.getEnable() == 0) {
            ensureMutableUser(existingUser, false);
        }
        ensureUserManageable(existingUser, "停用");
        SysUser user = new SysUser();
        user.setUserId(request.getUserId());
        user.setEnable(request.getEnable());
        boolean updated = this.updateById(user);
        if (updated) {
            permissionVersionService.increaseForUser(user.getUserId());
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "用户不存在");
        }
        SysUser user = loadVisibleUser(userId, "删除");
        ensureMutableUser(user, true);
        ensureUserManageable(user, "删除");
        sysUserRoleService.deleteByUserId(userId);
        boolean deleted = this.removeById(userId);
        if (deleted) {
            permissionVersionService.increaseForUser(userId);
        }
        return deleted;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteUsers(Collection<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return true;
        }
        List<Long> normalizedUserIds = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (normalizedUserIds.isEmpty()) {
            return true;
        }
        List<SysUser> users = listVisibleUsers(normalizedUserIds);
        if (users.size() != normalizedUserIds.size()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "存在权限范围外或不存在的用户");
        }
        users.forEach(user -> {
            ensureMutableUser(user, true);
            ensureUserManageable(user, "删除");
        });
        sysUserRoleService.deleteByUserIds(normalizedUserIds);
        boolean deleted = this.removeByIds(normalizedUserIds);
        if (deleted) {
            normalizedUserIds.stream()
                    .forEach(permissionVersionService::increaseForUser);
        }
        return deleted;
    }

    @Override
    public boolean resetPassword(Long userId) {
        SysUser targetUser = loadVisibleUser(userId, "重置");
        Long currentUserId = EasySecurityContext.getUserId();
        if (currentUserId != null && Objects.equals(currentUserId, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能在用户管理中重置当前登录账号密码，请到个人中心修改密码");
        }
        if (BUILT_IN_ADMIN_USER_NAME.equals(targetUser.getUserName())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "内置超级管理员密码不能在用户管理中重置");
        }
        ensureUserManageable(targetUser, "重置");
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setPassword(passwordHasher.hash(easyNextAdminConfig.getDefaultPwd()));
        boolean updated = this.updateById(user);
        if (updated) {
            permissionVersionService.increaseForUser(userId);
        }
        return updated;
    }

    @Override
    public boolean updateCurrentAvatar(String avatar) {
        SysUser user = new SysUser();
        user.setAvatar(avatar);
        user.setUserId(EasySecurityContext.getUserId());
        return this.updateById(user);
    }

    @Override
    public List<SystemRoleView> listAssignableRoles(Long userId) {
        Set<Long> assignedRoleIds = userId == null ? Set.of() : Set.copyOf(sysUserRoleService.listRoleIdsByUserId(userId));
        List<SysRole> allRoles = assignmentGuard.listAssignableRoles(assignedRoleIds);
        if (userId == null) {
            return allRoles.stream().map(SystemRoleView::from).toList();
        }
        allRoles.forEach(role -> role.setChecked(assignedRoleIds.contains(role.getRoleId())));
        return allRoles.stream().map(SystemRoleView::from).toList();
    }

    private void ensureMutableUser(SysUser user, boolean deleteOperation) {
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        Long currentUserId = EasySecurityContext.getUserId();
        if (currentUserId != null && Objects.equals(currentUserId, user.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, deleteOperation ? "不能删除当前登录账号" : "不能禁用当前登录账号");
        }
        if (BUILT_IN_ADMIN_USER_NAME.equals(user.getUserName())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, deleteOperation ? "内置超级管理员账号不能删除" : "内置超级管理员账号不能禁用");
        }
    }

    private void ensureUserManageable(SysUser user, String actionName) {
        if (user == null || user.getUserId() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        if (BUILT_IN_ADMIN_USER_NAME.equals(user.getUserName())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "内置超级管理员账号不能通过用户管理" + actionName);
        }
        Long currentUserId = EasySecurityContext.getUserId();
        if ("修改".equals(actionName) && currentUserId != null && Objects.equals(currentUserId, user.getUserId())) {
            return;
        }
        assignmentGuard.validateManageableRoleIds(sysUserRoleService.listRoleIdsByUserId(user.getUserId()), actionName);
    }

    @Override
    public List<FlowAssigneVo> listWorkflowAssignees() {
        return listWorkflowAssignees(null);
    }

    @Override
    public List<FlowAssigneVo> listWorkflowAssignees(Long userId) {
        List<SysUser> users = this.lambdaQuery()
                .eq(SysUser::getEnable, 1)
                .orderByAsc(SysUser::getDeptId)
                .orderByAsc(SysUser::getUserName)
                .list();
        users = includeCurrentManagerAssignee(users, userId);
        if (CollectionUtils.isEmpty(users)) {
            return List.of();
        }
        return users.stream()
                .map(user -> FlowAssigneVo.builder()
                        .name("%s（%s）".formatted(StringUtils.hasText(user.getNickName()) ? user.getNickName() : user.getUserName(), user.getUserName()))
                        .value(String.valueOf(user.getUserId()))
                        .avatar(user.getAvatar())
                        .build())
                .toList();
    }

    private List<SysUser> includeCurrentManagerAssignee(List<SysUser> visibleUsers, Long userId) {
        if (userId == null) {
            return visibleUsers;
        }
        SysUser targetUser = loadVisibleUser(userId, "查看");
        Long managerUserId = targetUser.getManagerUserId();
        if (managerUserId == null || visibleUsers.stream().anyMatch(user -> Objects.equals(user.getUserId(), managerUserId))) {
            return visibleUsers;
        }
        SysUser manager = EasyDataScopeContext.ignore(() -> this.lambdaQuery()
                .eq(SysUser::getUserId, managerUserId)
                .eq(SysUser::getEnable, 1)
                .one());
        if (manager == null) {
            return visibleUsers;
        }
        List<SysUser> users = new ArrayList<>(visibleUsers);
        users.add(manager);
        return users;
    }

    @Override
    public SystemUserView getUserAndDeptById(Long userId) {
        SysUser user = loadVisibleUser(userId, "查看");
        if (user == null) {
            return null;
        }
        return userRelationService.toUserView(user);
    }

    @Override
    public EnabledCountSummary countEnabledSummary() {
        return baseMapper.selectEnabledCountSummary();
    }

    @Override
    public boolean updateById(SysUser entity) {
        return super.updateById(entity);
    }

    @Override
    public boolean save(UserBO userBO) {
        final SysUser sysUser = userBeanMap.boToEntity(userBO);
        boolean saved = this.save(sysUser);
        userBO.setUserId(sysUser.getUserId());
        return saved;
    }

    private boolean saveUserRoles(Long userId, List<Long> roleIds) {
        sysUserRoleService.deleteByUserId(userId);
        if (CollectionUtils.isEmpty(roleIds)) {
            return true;
        }
        List<SysUserRole> userRoles = roleIds.stream()
                .distinct()
                .map(roleId -> {
                    SysUserRole userRole = new SysUserRole();
                    userRole.setRoleId(roleId);
                    userRole.setUserId(userId);
                    return userRole;
                })
                .collect(Collectors.toCollection(ArrayList::new));
        return sysUserRoleService.saveBatch(userRoles);
    }

    private SysUser loadVisibleUser(Long userId, String actionName) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        SysUser user = this.lambdaQuery()
                .eq(SysUser::getUserId, userId)
                .one();
        if (user != null) {
            return user;
        }
        if (userExistsIgnoringScope(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权" + actionName + "权限范围外的用户");
        }
        throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
    }

    private List<SysUser> listVisibleUsers(Collection<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return List.of();
        }
        return this.lambdaQuery()
                .in(SysUser::getUserId, userIds)
                .list();
    }

    private boolean userExistsIgnoringScope(Long userId) {
        return EasyDataScopeContext.ignore(() -> this.lambdaQuery()
                .eq(SysUser::getUserId, userId)
                .exists());
    }
}
