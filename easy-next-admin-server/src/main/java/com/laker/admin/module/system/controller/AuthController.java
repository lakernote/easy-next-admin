package com.laker.admin.module.system.controller;

import com.laker.admin.common.model.PageResponse;
import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.audit.EasyAudit;
import com.laker.admin.infrastructure.ratelimit.EasyRateLimit;
import com.laker.admin.infrastructure.ratelimit.EasyRateLimitType;
import com.laker.admin.infrastructure.security.annotation.EasyIgnoreAuth;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.infrastructure.security.service.EasyAuthService;
import com.laker.admin.infrastructure.security.service.EasyCaptchaService;
import com.laker.admin.module.system.dto.AuthProfileDto;
import com.laker.admin.module.system.dto.OnlineSessionView;
import com.laker.admin.module.system.dto.auth.AuthLoginRequest;
import com.laker.admin.module.system.dto.auth.AuthTokenResponse;
import com.laker.admin.module.system.dto.auth.CaptchaResponse;
import com.laker.admin.module.system.dto.auth.DemoAccountResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证授权")
public class AuthController {
    private final EasyAuthService authService;
    private final EasyCaptchaService captchaService;
    private final Environment environment;

    public AuthController(EasyAuthService authService, EasyCaptchaService captchaService, Environment environment) {
        this.authService = authService;
        this.captchaService = captchaService;
        this.environment = environment;
    }

    /**
     * 登录失败后前端才拉取验证码，默认登录路径保持用户名密码直登。
     */
    @GetMapping("/captcha")
    @EasyIgnoreAuth
    @EasyRateLimit(key = "auth:captcha", limit = 30, timeWindow = 1, timeUnit = TimeUnit.MINUTES,
            type = EasyRateLimitType.CLIENT_IP, message = "验证码获取过于频繁，请稍后再试")
    @Operation(summary = "获取登录验证码")
    public Response<CaptchaResponse> generateCaptcha() {
        return Response.ok(captchaService.generate());
    }

    @GetMapping("/demo-accounts")
    @EasyIgnoreAuth
    @Operation(summary = "获取登录体验账号")
    public Response<List<DemoAccountResponse>> listDemoAccounts() {
        if (!environment.acceptsProfiles(Profiles.of("local", "demo"))) {
            return Response.ok(List.of());
        }
        return Response.ok(List.of(
                new DemoAccountResponse("超级管理员", "admin", "admin", "查看和维护全部内置能力"),
                new DemoAccountResponse("部门负责人", "manager", "easynext", "用户、组织、流程和部门数据"),
                new DemoAccountResponse("普通员工", "staff", "easynext", "工作台和个人流程入口"),
                new DemoAccountResponse("审计人员", "auditor", "easynext", "查看审计记录并处理财务复核")
        ));
    }

    @PostMapping("/login")
    @EasyIgnoreAuth
    @EasyRateLimit(key = "auth:login", limit = 10, timeWindow = 1, timeUnit = TimeUnit.MINUTES,
            type = EasyRateLimitType.CLIENT_IP, message = "登录请求过于频繁，请稍后再试")
    @Operation(summary = "登录")
    public Response<AuthTokenResponse> login(@Valid @RequestBody AuthLoginRequest loginRequest,
                                             HttpServletRequest servletRequest) {
        return Response.ok(authService.login(loginRequest, servletRequest));
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前用户、角色、权限和菜单")
    public Response<AuthProfileDto> getCurrentProfile() {
        return Response.ok(authService.getCurrentProfile());
    }

    @GetMapping("/sessions")
    @EasyPermission(EasyPermissions.Monitor.ONLINE_VIEW)
    @Operation(summary = "获取在线用户信息")
    public PageResponse<OnlineSessionView> pageOnlineSessions(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "10") int pageSize) {
        return authService.pageOnlineSessions(page, pageSize);
    }

    @DeleteMapping("/sessions/{sessionId}")
    @EasyPermission(EasyPermissions.Auth.SESSION_REVOKE)
    @EasyAudit(module = "认证授权", action = "踢人下线", dataChange = true, bizType = "ONLINE_SESSION", bizId = "#sessionId", changeType = "REVOKE")
    @Operation(summary = "踢人下线")
    public Response<Void> revokeSession(@PathVariable Long sessionId) {
        authService.revokeSession(sessionId);
        return Response.ok();
    }

    @PostMapping("/logout")
    @EasyAudit(module = "认证授权", action = "退出登录")
    @Operation(summary = "退出登录")
    public Response<Void> logoutCurrentSession() {
        authService.logoutCurrentSession();
        return Response.ok();
    }
}
