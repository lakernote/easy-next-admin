package com.laker.admin.infrastructure.security.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.common.model.PageResponse;
import com.laker.admin.common.util.EasyTreeUtil;
import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.infrastructure.audit.AuditLogCollector;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.datascope.context.EasyDataScopeContext;
import com.laker.admin.infrastructure.security.exception.EasyAuthException;
import com.laker.admin.infrastructure.security.exception.EasyForbiddenException;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.infrastructure.security.model.AuthSession;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.infrastructure.security.store.AuthSessionStore;
import com.laker.admin.infrastructure.security.support.EasyPasswordHasher;
import com.laker.admin.infrastructure.security.support.EasySecurityToken;
import com.laker.admin.infrastructure.web.context.EasyRequestContext;
import com.laker.admin.infrastructure.security.datascope.model.DataScopeType;
import com.laker.admin.module.system.dto.AuthProfileDto;
import com.laker.admin.module.system.dto.MenuVo;
import com.laker.admin.module.system.dto.OnlineSessionView;
import com.laker.admin.module.system.dto.auth.AuthLoginRequest;
import com.laker.admin.module.system.dto.auth.AuthTokenResponse;
import com.laker.admin.module.system.dto.auth.AuthUserProfile;
import com.laker.admin.module.system.entity.SysDept;
import com.laker.admin.module.system.entity.SysPower;
import com.laker.admin.module.system.entity.SysRole;
import com.laker.admin.module.system.entity.SysUser;
import com.laker.admin.module.system.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 认证授权核心服务。
 *
 * <p>集中处理登录、登出、在线会话、当前用户画像和菜单权限组装。
 * Controller 只负责协议入口，业务模块不直接操作 token 或会话存储。</p>
 */
@Service
public class EasyAuthService {
    private static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration DEFAULT_ABSOLUTE_TIMEOUT = Duration.ofHours(8);
    private static final List<String> PROFILE_SELF_SERVICE_PERMISSIONS = List.of(
        EasyPermissions.Profile.VIEW,
        EasyPermissions.Profile.EDIT,
        EasyPermissions.Profile.PASSWORD_CHANGE,
        EasyPermissions.Profile.SESSION_MANAGE
    );

    private final ISysUserService sysUserService;
    private final ISysDeptService sysDeptService;
    private final ISysUserRoleService sysUserRoleService;
    private final ISysRoleService sysRoleService;
    private final ISysMenuService sysMenuService;
    private final AuthSessionStore authSessionStore;
    private final EasyCaptchaService captchaService;
    private final EasyPasswordHasher passwordHasher;
    private final AuditLogCollector auditLogCollector;
    private final EasyNextAdminConfig easyNextAdminConfig;

    public EasyAuthService(ISysUserService sysUserService,
                           ISysDeptService sysDeptService,
                           ISysUserRoleService sysUserRoleService,
                           ISysRoleService sysRoleService,
                           ISysMenuService sysMenuService,
                           AuthSessionStore authSessionStore,
                           EasyCaptchaService captchaService,
                           EasyPasswordHasher passwordHasher,
                           AuditLogCollector auditLogCollector,
                           EasyNextAdminConfig easyNextAdminConfig) {
        this.sysUserService = sysUserService;
        this.sysDeptService = sysDeptService;
        this.sysUserRoleService = sysUserRoleService;
        this.sysRoleService = sysRoleService;
        this.sysMenuService = sysMenuService;
        this.authSessionStore = authSessionStore;
        this.captchaService = captchaService;
        this.passwordHasher = passwordHasher;
        this.auditLogCollector = auditLogCollector;
        this.easyNextAdminConfig = easyNextAdminConfig == null ? new EasyNextAdminConfig() : easyNextAdminConfig;
    }

    @Transactional
    public AuthTokenResponse login(AuthLoginRequest request, HttpServletRequest httpRequest) {
        try {
            return doLogin(request, httpRequest);
        } catch (EasyAuthException | EasyForbiddenException e) {
            auditLogCollector.recordLogin(null, request == null ? null : request.getUsername(), false, e.getMessage(), httpRequest);
            throw e;
        }
    }

    private AuthTokenResponse doLogin(AuthLoginRequest request, HttpServletRequest httpRequest) {
        // 首次登录只校验账号密码；同一用户名和客户端 IP 失败后，后续登录才要求验证码。
        if (captchaService.isRequired(request.getUsername(), httpRequest)) {
            captchaService.validate(request.getCaptchaId(), request.getCaptchaCode());
        }
        SysUser user = EasyDataScopeContext.ignore(() -> sysUserService.getOne(
            Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUserName, request.getUsername())));
        if (user == null || !passwordHasher.matches(request.getPassword(), user.getPassword())) {
            captchaService.markRequired(request.getUsername(), httpRequest);
            throw new EasyAuthException(ErrorCode.AUTH_INVALID_CREDENTIALS, "用户名或密码不正确");
        }
        if (Objects.equals(user.getEnable(), 0)) {
            throw new EasyForbiddenException(ErrorCode.AUTH_ACCOUNT_DISABLED, "账号已被禁用");
        }
        captchaService.clearRequired(request.getUsername(), httpRequest);
        PrincipalBuildResult principalResult = buildPrincipal(user);
        AuthPrincipal principal = principalResult.principal();
        AuthSession session = createSession(user, principal, httpRequest);
        updateLastLoginTime(user.getUserId());
        auditLogCollector.recordLogin(user.getUserId(), user.getUserName(), true, null, httpRequest);
        return tokenResponse(session, principal, user, principalResult.powers());
    }

    private void updateLastLoginTime(Long userId) {
        if (userId == null) {
            return;
        }
        SysUser update = new SysUser();
        update.setUserId(userId);
        update.setLastLoginTime(LocalDateTime.now());
        sysUserService.updateById(update);
    }

    public AuthPrincipal authenticate(String accessToken) {
        String tokenHash = EasySecurityToken.sha256(accessToken);
        AuthSession session = authSessionStore.findByAccessTokenHash(tokenHash)
            .orElseThrow(() -> new EasyAuthException(ErrorCode.AUTH_SESSION_EXPIRED, "登录已过期，请重新登录"));
        LocalDateTime now = LocalDateTime.now();
        if (!AuthSession.STATUS_ACTIVE.equals(session.getStatus())) {
            throw new EasyAuthException(ErrorCode.AUTH_SESSION_EXPIRED, "登录已过期，请重新登录");
        }
        if (isAbsoluteExpired(session, now) || session.getAccessExpireTime() == null || !session.getAccessExpireTime().isAfter(now)) {
            expireSession(session);
            throw new EasyAuthException(ErrorCode.AUTH_SESSION_EXPIRED, "登录已过期，请重新登录");
        }
        if (session.getLastActiveTime() == null || session.getLastActiveTime().isBefore(now.minusSeconds(60))) {
            session.setLastActiveTime(now);
            session.setAccessExpireTime(nextAccessExpireTime(session, now));
            authSessionStore.update(session, remainingTtl(session, now));
        }
        AuthPrincipal principal = resolveSessionPrincipal(session);
        principal.setSessionId(session.getSessionId());
        return principal;
    }

    public AuthProfileDto getCurrentProfile() {
        AuthPrincipal principal = currentPrincipal();
        AuthUserProfile user = toAuthUserProfile(sysUserService.getById(principal.getUserId()), principal);
        return AuthProfileDto.builder()
            .user(user)
            .roles(principal.getRoles())
            .roleNames(principal.getRoleNames())
            .permissions(principal.getPermissions())
            .menus(resolveMenus(principal.getUserId(), principal.isSuperAdmin()))
            .build();
    }

    @Transactional
    public void logoutCurrentSession() {
        String accessToken = EasySecurityContext.getAccessToken();
        if (!StringUtils.hasText(accessToken)) {
            return;
        }
        authSessionStore.findByAccessTokenHash(EasySecurityToken.sha256(accessToken))
            .ifPresent(session -> authSessionStore.revoke(session.getSessionId(), AuthSession.STATUS_LOGOUT));
    }

    @Transactional
    public void revokeSession(Long sessionId) {
        AuthPrincipal principal = currentPrincipal();
        if (Objects.equals(principal.getSessionId(), sessionId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能踢出当前会话");
        }
        authSessionStore.revoke(sessionId, AuthSession.STATUS_KICKED);
    }

    public PageResponse<OnlineSessionView> pageOnlineSessions(int page, int pageSize) {
        List<AuthSession> sessions = authSessionStore.listActive(page, pageSize);
        List<Long> userIds = sessions.stream().map(AuthSession::getUserId).distinct().toList();
        Map<Long, SysUser> users = CollectionUtils.isEmpty(userIds)
            ? Map.of()
            : sysUserService.listByIds(userIds).stream().collect(Collectors.toMap(SysUser::getUserId, Function.identity(), (a, b) -> a));
        List<OnlineSessionView> records = sessions.stream()
            .map(session -> {
                SysUser user = users.get(session.getUserId());
                return OnlineSessionView.builder()
                    .sessionId(session.getSessionId())
                    .userId(session.getUserId())
                    .userName(user == null ? null : user.getUserName())
                    .nickName(user == null ? null : user.getNickName())
                    .avatar(user == null ? null : user.getAvatar())
                    .clientType(session.getClientType())
                    .clientVersion(session.getClientVersion())
                    .ip(session.getIp())
                    .userAgent(session.getUserAgent())
                    .status(session.getStatus())
                    .current(Objects.equals(session.getSessionId(), EasySecurityContext.getPrincipal() == null ? null : EasySecurityContext.getPrincipal().getSessionId()))
                    .loginTime(session.getLoginTime())
                    .lastActiveTime(session.getLastActiveTime())
                    .accessExpireTime(session.getAccessExpireTime())
                    .build();
            })
            .toList();
        return PageResponse.ok(records, authSessionStore.countActive());
    }

    public long countOnlineSessions() {
        return authSessionStore.countActive();
    }

    public AuthPrincipal currentPrincipal() {
        AuthPrincipal principal = EasySecurityContext.getPrincipal();
        if (principal == null) {
            throw new EasyAuthException("未登录");
        }
        return principal;
    }

    public AuthPrincipal buildPrincipal(Long userId) {
        // 每次认证后重新组装用户角色、按钮权限和数据范围，保证权限变更能尽快生效。
        SysUser user = sysUserService.getById(userId);
        return buildPrincipal(user).principal();
    }

    private LocalDateTime nextAccessExpireTime(AuthSession session, LocalDateTime now) {
        LocalDateTime loginTime = session.getLoginTime() == null ? now : session.getLoginTime();
        return nextAccessExpireTime(loginTime, now);
    }

    private LocalDateTime nextAccessExpireTime(LocalDateTime loginTime, LocalDateTime now) {
        LocalDateTime idleExpireTime = now.plus(sessionIdleTimeout());
        LocalDateTime absoluteExpireTime = loginTime.plus(sessionAbsoluteTimeout());
        return idleExpireTime.isAfter(absoluteExpireTime) ? absoluteExpireTime : idleExpireTime;
    }

    private boolean isAbsoluteExpired(AuthSession session, LocalDateTime now) {
        LocalDateTime loginTime = session.getLoginTime();
        if (loginTime == null) {
            return false;
        }
        return !loginTime.plus(sessionAbsoluteTimeout()).isAfter(now);
    }

    private Duration remainingTtl(AuthSession session, LocalDateTime now) {
        Duration ttl = Duration.between(now, session.getAccessExpireTime());
        return ttl.isNegative() || ttl.isZero() ? Duration.ofSeconds(1) : ttl;
    }

    private Duration sessionIdleTimeout() {
        EasyNextAdminConfig.Auth.Session session = authSessionConfig();
        return positiveOrDefault(session == null ? null : session.getIdleTimeout(), DEFAULT_IDLE_TIMEOUT);
    }

    private Duration sessionAbsoluteTimeout() {
        EasyNextAdminConfig.Auth.Session session = authSessionConfig();
        return positiveOrDefault(session == null ? null : session.getAbsoluteTimeout(), DEFAULT_ABSOLUTE_TIMEOUT);
    }

    private EasyNextAdminConfig.Auth.Session authSessionConfig() {
        EasyNextAdminConfig.Auth auth = easyNextAdminConfig.getAuth();
        return auth == null ? null : auth.getSession();
    }

    private Duration positiveOrDefault(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }

    private PrincipalBuildResult buildPrincipal(SysUser user) {
        if (user == null || Objects.equals(user.getEnable(), 0)) {
            throw new EasyForbiddenException(ErrorCode.AUTH_ACCOUNT_DISABLED, "账号不可用");
        }
        Long userId = user.getUserId();
        SysDept dept = user.getDeptId() == null ? null : sysDeptService.getById(user.getDeptId());
        List<Long> roleIds = sysUserRoleService.listRoleIdsByUserId(userId);
        List<SysRole> roles = CollectionUtils.isEmpty(roleIds) ? List.of() : sysRoleService.list(Wrappers.<SysRole>lambdaQuery()
                                                                                                 .in(SysRole::getRoleId, roleIds)
                                                                                                 .eq(SysRole::getEnable, true));
        List<String> roleCodes = roles.stream().map(SysRole::getRoleCode).filter(StringUtils::hasText).distinct().toList();
        List<String> roleNames = roles.stream().map(SysRole::getRoleName).filter(StringUtils::hasText).distinct().toList();
        boolean superAdmin = roleCodes.contains("admin");
        List<SysPower> powers = resolvePowers(userId, superAdmin);
        List<String> permissions = withProfileSelfServicePermissions(powers.stream()
            .map(SysPower::getPowerCode)
            .filter(StringUtils::hasText)
            .distinct()
            .toList());
        List<DataScopeType> dataScopes = roles.stream()
            .map(SysRole::getDataScope)
            .map(DataScopeType::fromRoleDataScope)
            .distinct()
            .toList();
        AuthPrincipal principal = AuthPrincipal.builder()
            .userId(userId)
            .userName(user.getUserName())
            .nickName(user.getNickName())
            .deptId(user.getDeptId())
            .deptName(dept == null ? null : dept.getDeptName())
            .permissionVersion(permissionVersion(user))
            .superAdmin(superAdmin)
            .deptIds(resolveDeptAndChildren(user.getDeptId()))
            .roles(roleCodes)
            .roleNames(roleNames)
            .permissions(permissions)
            .dataScopes(dataScopes)
            .build();
        return new PrincipalBuildResult(principal, powers);
    }

    private AuthSession createSession(SysUser user, AuthPrincipal principal, HttpServletRequest request) {
        LocalDateTime now = LocalDateTime.now();
        String accessToken = EasySecurityToken.randomToken();
        Long sessionId = IdWorker.getId();
        principal.setSessionId(sessionId);
        // 只保存 token 摘要，原始 token 只返回给本次登录响应，避免服务端运行态泄露明文令牌。
        AuthSession session = AuthSession.builder()
            .sessionId(sessionId)
            .userId(user.getUserId())
            .accessTokenHash(EasySecurityToken.sha256(accessToken))
            .clientType("web")
            .ip(EasyRequestContext.remoteIp(request))
            .userAgent(request.getHeader("User-Agent"))
            .loginTime(now)
            .lastActiveTime(now)
            .accessExpireTime(nextAccessExpireTime(now, now))
            .status(AuthSession.STATUS_ACTIVE)
            .principal(principal)
            .createBy(user.getUserId())
            .createDeptId(user.getDeptId())
            .accessToken(accessToken)
            .build();
        authSessionStore.save(session, remainingTtl(session, now));
        return session;
    }

    private AuthTokenResponse tokenResponse(AuthSession session, AuthPrincipal principal, SysUser loginUser, List<SysPower> powers) {
        AuthUserProfile user = toAuthUserProfile(loginUser, principal);
        return AuthTokenResponse.builder()
            .accessToken(session.getAccessToken())
            .accessExpiresIn(Duration.between(session.getLoginTime(), session.getAccessExpireTime()).toSeconds())
            .user(user)
            .roles(principal.getRoles())
            .roleNames(principal.getRoleNames())
            .permissions(principal.getPermissions())
            .menus(toMenuTree(powers))
            .build();
    }

    private List<MenuVo> resolveMenus(Long userId, boolean superAdmin) {
        return toMenuTree(resolvePowers(userId, superAdmin));
    }

    private List<SysPower> resolvePowers(Long userId, boolean superAdmin) {
        List<SysPower> powers;
        if (superAdmin) {
            powers = sysMenuService.list(Wrappers.<SysPower>lambdaQuery()
                .eq(SysPower::getEnable, true)
                .orderByAsc(SysPower::getPid, SysPower::getSort, SysPower::getMenuId));
        } else {
            powers = sysMenuService.listEnabledResourcesByUserId(userId);
        }
        return withProfileSelfServicePowers(powers);
    }

    private List<SysPower> withProfileSelfServicePowers(List<SysPower> powers) {
        Map<Long, SysPower> merged = new LinkedHashMap<>();
        if (powers != null) {
            powers.stream()
                .filter(power -> power.getMenuId() != null)
                .forEach(power -> merged.put(power.getMenuId(), power));
        }
        Set<String> existingCodes = merged.values().stream()
            .map(SysPower::getPowerCode)
            .filter(StringUtils::hasText)
            .collect(Collectors.toSet());
        List<String> missingCodes = PROFILE_SELF_SERVICE_PERMISSIONS.stream()
            .filter(code -> !existingCodes.contains(code))
            .toList();
        if (!missingCodes.isEmpty()) {
            sysMenuService.list(Wrappers.<SysPower>lambdaQuery()
                    .eq(SysPower::getEnable, true)
                    .in(SysPower::getPowerCode, missingCodes)
                    .orderByAsc(SysPower::getPid, SysPower::getSort, SysPower::getMenuId))
                .stream()
                .filter(power -> power.getMenuId() != null)
                .forEach(power -> merged.putIfAbsent(power.getMenuId(), power));
        }
        return new ArrayList<>(merged.values());
    }

    private List<MenuVo> toMenuVos(List<SysPower> sysPowers) {
        List<MenuVo> menuInfo = new ArrayList<>();
        for (SysPower e : sysPowers) {
            MenuVo menuVO = new MenuVo();
            menuVO.setId(e.getMenuId());
            menuVO.setPid(e.getPid());
            boolean page = e.getType() != null && e.getType() == 1;
            menuVO.setHref(page ? e.getHref() : null);
            menuVO.setTitle(e.getTitle());
            menuVO.setIcon(e.getIcon());
            menuVO.setSort(e.getSort());
            menuVO.setEnable(e.getEnable());
            menuVO.setVisible(e.getVisible());
            menuVO.setType(e.getType());
            menuVO.setPowerCode(e.getPowerCode());
            menuVO.setComponentPath(page ? e.getComponentPath() : null);
            menuInfo.add(menuVO);
        }
        return menuInfo;
    }

    private List<MenuVo> toMenuTree(List<SysPower> powers) {
        return EasyTreeUtil.toTree(toMenuVos(powers == null ? List.of() : powers), 0L);
    }

    private Set<Long> resolveDeptAndChildren(Long deptId) {
        if (deptId == null) {
            return Set.of();
        }
        List<SysDept> departments = EasyDataScopeContext.ignore(sysDeptService::list);
        Set<Long> deptIds = new HashSet<>();
        collectDeptIds(departments, deptId, deptIds);
        return deptIds;
    }

    private void collectDeptIds(List<SysDept> departments, Long currentDeptId, Set<Long> deptIds) {
        if (currentDeptId == null || !deptIds.add(currentDeptId)) {
            return;
        }
        departments.stream()
            .filter(dept -> Objects.equals(dept.getPid(), currentDeptId))
            .forEach(dept -> collectDeptIds(departments, dept.getDeptId(), deptIds));
    }

    private void expireSession(AuthSession session) {
        authSessionStore.revoke(session.getSessionId(), AuthSession.STATUS_EXPIRED);
    }

    private AuthPrincipal resolveSessionPrincipal(AuthSession session) {
        AuthPrincipal snapshot = session.getPrincipal();
        if (snapshot == null) {
            return buildPrincipal(session.getUserId());
        }
        SysUser user = sysUserService.getById(session.getUserId());
        if (user == null || Objects.equals(user.getEnable(), 0)) {
            authSessionStore.revoke(session.getSessionId(), AuthSession.STATUS_EXPIRED);
            throw new EasyForbiddenException(ErrorCode.AUTH_ACCOUNT_DISABLED, "账号不可用");
        }
        if (!Objects.equals(snapshot.getPermissionVersion(), permissionVersion(user))) {
            authSessionStore.revoke(session.getSessionId(), AuthSession.STATUS_PERMISSION_CHANGED);
            throw new EasyAuthException(ErrorCode.AUTH_PERMISSION_CHANGED, "权限已变更，请重新登录");
        }
        snapshot.setPermissions(withProfileSelfServicePermissions(snapshot.getPermissions()));
        return snapshot;
    }

    private List<String> withProfileSelfServicePermissions(List<String> permissions) {
        Set<String> merged = new java.util.LinkedHashSet<>();
        if (permissions != null) {
            merged.addAll(permissions);
        }
        merged.addAll(PROFILE_SELF_SERVICE_PERMISSIONS);
        return new ArrayList<>(merged);
    }

    private Long permissionVersion(SysUser user) {
        return user.getPermissionVersion() == null ? 1L : user.getPermissionVersion();
    }

    private AuthUserProfile toAuthUserProfile(SysUser user, AuthPrincipal principal) {
        if (user == null) {
            return null;
        }
        return AuthUserProfile.builder()
            .userId(user.getUserId())
            .userName(user.getUserName())
            .nickName(user.getNickName())
            .realName(user.getRealName())
            .employeeNo(user.getEmployeeNo())
            .positionName(user.getPositionName())
            .deptId(user.getDeptId())
            .deptName(principal.getDeptName())
            .phone(user.getPhone())
            .email(user.getEmail())
            .avatar(user.getAvatar())
            .lastLoginTime(user.getLastLoginTime())
            .build();
    }

    private record PrincipalBuildResult(AuthPrincipal principal, List<SysPower> powers) {
    }
}
