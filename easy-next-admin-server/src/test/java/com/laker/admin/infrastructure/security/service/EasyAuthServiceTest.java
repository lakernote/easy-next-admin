package com.laker.admin.infrastructure.security.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.infrastructure.security.exception.EasyAuthException;
import com.laker.admin.infrastructure.security.exception.EasyForbiddenException;
import com.laker.admin.infrastructure.audit.AuditLogCollector;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.datascope.context.EasyDataScopeContext;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.infrastructure.security.model.AuthSession;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.infrastructure.security.store.AuthSessionStore;
import com.laker.admin.infrastructure.security.support.EasyPasswordHasher;
import com.laker.admin.infrastructure.security.support.EasySecurityToken;
import com.laker.admin.module.system.dto.auth.AuthLoginRequest;
import com.laker.admin.module.system.dto.auth.AuthTokenResponse;
import com.laker.admin.module.system.entity.SysDept;
import com.laker.admin.module.system.entity.SysMenuResource;
import com.laker.admin.module.system.entity.SysRole;
import com.laker.admin.module.system.entity.SysUser;
import com.laker.admin.module.system.service.ISysDeptService;
import com.laker.admin.module.system.service.ISysMenuService;
import com.laker.admin.module.system.service.ISysRoleService;
import com.laker.admin.module.system.service.ISysUserRoleService;
import com.laker.admin.module.system.service.ISysUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EasyAuthServiceTest {

    private final ISysUserService sysUserService = mock(ISysUserService.class);
    private final AuthSessionStore authSessionStore = mock(AuthSessionStore.class);
    private final EasyAuthService authService = new EasyAuthService(
            sysUserService,
            mock(ISysDeptService.class),
            mock(ISysUserRoleService.class),
            mock(ISysRoleService.class),
            mock(ISysMenuService.class),
            authSessionStore,
            mock(EasyCaptchaService.class),
            mock(EasyPasswordHasher.class),
            mock(AuditLogCollector.class),
            authConfig(Duration.ofMinutes(30), Duration.ofHours(8))
    );

    @Test
    void authenticateShouldUseCachedLoginUserWhenPermissionVersionMatches() {
        AuthPrincipal snapshot = AuthPrincipal.builder()
                .userId(1L)
                .userName("admin")
                .permissionVersion(3L)
                .permissions(List.of("sys:user:list"))
                .build();
        AuthSession session = activeSession(snapshot);
        SysUser user = enabledUser(3L);
        when(authSessionStore.findByAccessTokenHash(EasySecurityToken.sha256("access-token"))).thenReturn(Optional.of(session));
        when(sysUserService.getById(1L)).thenReturn(user);

        AuthPrincipal principal = authService.authenticate("access-token");

        assertThat(principal).isSameAs(snapshot);
        assertThat(principal.getSessionId()).isEqualTo(10L);
        assertThat(principal.getPermissions())
                .contains(
                        EasyPermissions.Profile.VIEW,
                        EasyPermissions.Profile.EDIT,
                        EasyPermissions.Profile.PASSWORD_CHANGE,
                        EasyPermissions.Profile.SESSION_MANAGE
                );
    }

    @Test
    void authenticateShouldRejectSessionWhenPermissionVersionChanged() {
        AuthPrincipal snapshot = AuthPrincipal.builder()
                .userId(1L)
                .userName("admin")
                .permissionVersion(3L)
                .build();
        AuthSession session = activeSession(snapshot);
        SysUser user = enabledUser(4L);
        when(authSessionStore.findByAccessTokenHash(EasySecurityToken.sha256("access-token"))).thenReturn(Optional.of(session));
        when(sysUserService.getById(1L)).thenReturn(user);

        assertThatThrownBy(() -> authService.authenticate("access-token"))
                .isInstanceOf(EasyAuthException.class)
                .hasMessage("权限已变更，请重新登录")
                .satisfies(error -> assertThat(((EasyAuthException) error).getErrorCode()).isEqualTo(ErrorCode.AUTH_PERMISSION_CHANGED));
        verify(authSessionStore).revoke(10L, AuthSession.STATUS_PERMISSION_CHANGED);
    }

    @Test
    void loginShouldLookupUserWithoutDataScope() {
        ISysUserService localUserService = mock(ISysUserService.class);
        EasyPasswordHasher passwordHasher = mock(EasyPasswordHasher.class);
        EasyAuthService localAuthService = new EasyAuthService(
                localUserService,
                mock(ISysDeptService.class),
                mock(ISysUserRoleService.class),
                mock(ISysRoleService.class),
                mock(ISysMenuService.class),
                mock(AuthSessionStore.class),
                mock(EasyCaptchaService.class),
                passwordHasher,
                mock(AuditLogCollector.class),
                authConfig(Duration.ofMinutes(30), Duration.ofHours(8))
        );
        AtomicBoolean ignoredDuringLookup = new AtomicBoolean(false);
        SysUser user = enabledUser(1L);
        user.setUserName("manager");
        user.setPassword("hash");
        when(localUserService.getOne(any())).thenAnswer(invocation -> {
            ignoredDuringLookup.set(EasyDataScopeContext.ignored());
            return user;
        });
        when(passwordHasher.matches("easynext", "hash")).thenReturn(false);
        AuthLoginRequest request = new AuthLoginRequest();
        request.setUsername("manager");
        request.setPassword("easynext");

        assertThatThrownBy(() -> localAuthService.login(request, null))
                .isInstanceOf(EasyAuthException.class)
                .hasMessage("用户名或密码不正确")
                .satisfies(error -> assertThat(((EasyAuthException) error).getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
        assertThat(ignoredDuringLookup).isTrue();
    }

    @Test
    void loginShouldReturnForbiddenWhenAccountDisabled() {
        ISysUserService localUserService = mock(ISysUserService.class);
        EasyPasswordHasher passwordHasher = mock(EasyPasswordHasher.class);
        EasyAuthService localAuthService = new EasyAuthService(
                localUserService,
                mock(ISysDeptService.class),
                mock(ISysUserRoleService.class),
                mock(ISysRoleService.class),
                mock(ISysMenuService.class),
                mock(AuthSessionStore.class),
                mock(EasyCaptchaService.class),
                passwordHasher,
                mock(AuditLogCollector.class),
                authConfig(Duration.ofMinutes(30), Duration.ofHours(8))
        );
        SysUser user = enabledUser(1L);
        user.setEnable(0);
        user.setUserName("manager");
        user.setPassword("hash");
        when(localUserService.getOne(any())).thenReturn(user);
        when(passwordHasher.matches("easynext", "hash")).thenReturn(true);
        AuthLoginRequest request = new AuthLoginRequest();
        request.setUsername("manager");
        request.setPassword("easynext");

        assertThatThrownBy(() -> localAuthService.login(request, null))
                .isInstanceOf(EasyForbiddenException.class)
                .hasMessage("账号已被禁用")
                .satisfies(error -> assertThat(((EasyForbiddenException) error).getErrorCode()).isEqualTo(ErrorCode.AUTH_ACCOUNT_DISABLED));
    }

    @Test
    void loginShouldReuseLoadedUserAndMenuPermissionResources() {
        ISysUserService localUserService = mock(ISysUserService.class);
        ISysDeptService deptService = mock(ISysDeptService.class);
        ISysUserRoleService userRoleService = mock(ISysUserRoleService.class);
        ISysRoleService roleService = mock(ISysRoleService.class);
        ISysMenuService menuService = mock(ISysMenuService.class);
        AuthSessionStore sessionStore = mock(AuthSessionStore.class);
        EasyPasswordHasher passwordHasher = mock(EasyPasswordHasher.class);
        EasyAuthService localAuthService = new EasyAuthService(
                localUserService,
                deptService,
                userRoleService,
                roleService,
                menuService,
                sessionStore,
                mock(EasyCaptchaService.class),
                passwordHasher,
                mock(AuditLogCollector.class),
                authConfig(Duration.ofMinutes(30), Duration.ofHours(8))
        );
        SysUser user = enabledUser(7L);
        user.setUserId(1L);
        user.setUserName("admin");
        user.setNickName("超级管理员");
        user.setPassword("hash");
        user.setDeptId(10L);
        SysDept dept = new SysDept();
        dept.setDeptId(10L);
        dept.setDeptName("总经办");
        SysRole role = new SysRole();
        role.setRoleId(100L);
        role.setRoleCode("admin");
        role.setRoleName("超级管理员");
        role.setEnable(true);
        role.setDataScope("ALL");
        SysMenuResource menu = new SysMenuResource();
        menu.setMenuId(1000L);
        menu.setPid(0L);
        menu.setTitle("工作台");
        menu.setType(1);
        menu.setPermissionCode(EasyPermissions.Dashboard.VIEW);
        menu.setEnable(true);
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn("JUnit");
        when(localUserService.getOne(any())).thenReturn(user);
        when(passwordHasher.matches("easynext", "hash")).thenReturn(true);
        when(deptService.getById(10L)).thenReturn(dept);
        when(deptService.list()).thenReturn(List.of(dept));
        when(userRoleService.listRoleIdsByUserId(1L)).thenReturn(List.of(100L));
        when(roleService.list(org.mockito.ArgumentMatchers.<Wrapper<SysRole>>any())).thenReturn(List.of(role));
        when(menuService.list(org.mockito.ArgumentMatchers.<Wrapper<SysMenuResource>>any())).thenReturn(List.of(menu));
        AuthLoginRequest request = new AuthLoginRequest();
        request.setUsername("admin");
        request.setPassword("easynext");

        AuthTokenResponse response = localAuthService.login(request, httpRequest);

        assertThat(response.getUser().getUserId()).isEqualTo(1L);
        assertThat(response.getMenus()).hasSize(1);
        verify(localUserService, never()).getById(any());
        verify(menuService, times(2)).list(org.mockito.ArgumentMatchers.<Wrapper<SysMenuResource>>any());
    }

    @Test
    void loginShouldUseConfiguredIdleTimeout() {
        ISysUserService localUserService = mock(ISysUserService.class);
        ISysDeptService deptService = mock(ISysDeptService.class);
        ISysUserRoleService userRoleService = mock(ISysUserRoleService.class);
        ISysRoleService roleService = mock(ISysRoleService.class);
        ISysMenuService menuService = mock(ISysMenuService.class);
        AuthSessionStore sessionStore = mock(AuthSessionStore.class);
        EasyPasswordHasher passwordHasher = mock(EasyPasswordHasher.class);
        EasyAuthService localAuthService = new EasyAuthService(
                localUserService,
                deptService,
                userRoleService,
                roleService,
                menuService,
                sessionStore,
                mock(EasyCaptchaService.class),
                passwordHasher,
                mock(AuditLogCollector.class),
                authConfig(Duration.ofMinutes(5), Duration.ofHours(8))
        );
        SysUser user = enabledUser(1L);
        user.setUserId(1L);
        user.setUserName("admin");
        user.setPassword("hash");
        when(localUserService.getOne(any())).thenReturn(user);
        when(passwordHasher.matches("easynext", "hash")).thenReturn(true);
        when(userRoleService.listRoleIdsByUserId(1L)).thenReturn(List.of());
        when(menuService.list(org.mockito.ArgumentMatchers.<Wrapper<SysMenuResource>>any())).thenReturn(List.of());
        AuthLoginRequest request = new AuthLoginRequest();
        request.setUsername("admin");
        request.setPassword("easynext");

        AuthTokenResponse response = localAuthService.login(request, mock(HttpServletRequest.class));

        ArgumentCaptor<AuthSession> sessionCaptor = ArgumentCaptor.forClass(AuthSession.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(sessionStore).save(sessionCaptor.capture(), ttlCaptor.capture());
        assertThat(response.getAccessExpiresIn()).isEqualTo(Duration.ofMinutes(5).toSeconds());
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofMinutes(5));
        AuthSession session = sessionCaptor.getValue();
        assertThat(Duration.between(session.getLoginTime(), session.getAccessExpireTime()))
                .isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void authenticateShouldRejectSessionBeyondConfiguredAbsoluteTimeout() {
        AuthPrincipal snapshot = AuthPrincipal.builder()
                .userId(1L)
                .userName("admin")
                .permissionVersion(1L)
                .build();
        AuthSession session = activeSession(snapshot);
        session.setLoginTime(LocalDateTime.now().minusHours(9));
        session.setAccessExpireTime(LocalDateTime.now().plusMinutes(10));
        when(authSessionStore.findByAccessTokenHash(EasySecurityToken.sha256("access-token"))).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> authService.authenticate("access-token"))
                .isInstanceOf(EasyAuthException.class)
                .hasMessage("登录已过期，请重新登录")
                .satisfies(error -> assertThat(((EasyAuthException) error).getErrorCode()).isEqualTo(ErrorCode.AUTH_SESSION_EXPIRED));
        verify(authSessionStore).revoke(10L, AuthSession.STATUS_EXPIRED);
        verify(sysUserService, never()).getById(any());
    }

    @Test
    void logoutCurrentSessionShouldRevokeServerSession() {
        EasySecurityContext.setAccessToken("access-token");
        when(authSessionStore.findByAccessTokenHash(EasySecurityToken.sha256("access-token")))
                .thenReturn(Optional.of(activeSession(AuthPrincipal.builder().userId(1L).userName("admin").build())));

        try {
            authService.logoutCurrentSession();
        } finally {
            EasySecurityContext.clear();
        }

        verify(authSessionStore).revoke(10L, AuthSession.STATUS_LOGOUT);
    }

    private AuthSession activeSession(AuthPrincipal principal) {
        return AuthSession.builder()
                .sessionId(10L)
                .userId(1L)
                .accessTokenHash(EasySecurityToken.sha256("access-token"))
                .status(AuthSession.STATUS_ACTIVE)
                .lastActiveTime(LocalDateTime.now())
                .accessExpireTime(LocalDateTime.now().plusMinutes(10))
                .principal(principal)
                .build();
    }

    private EasyNextAdminConfig authConfig(Duration idleTimeout, Duration absoluteTimeout) {
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        EasyNextAdminConfig.Auth auth = new EasyNextAdminConfig.Auth();
        EasyNextAdminConfig.Auth.Session session = new EasyNextAdminConfig.Auth.Session();
        session.setIdleTimeout(idleTimeout);
        session.setAbsoluteTimeout(absoluteTimeout);
        auth.setSession(session);
        config.setAuth(auth);
        return config;
    }

    private SysUser enabledUser(Long permissionVersion) {
        SysUser user = new SysUser();
        user.setUserId(1L);
        user.setEnable(1);
        user.setPermissionVersion(permissionVersion);
        return user;
    }
}
