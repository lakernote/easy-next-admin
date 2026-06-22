package com.laker.admin.module.system.controller;

import com.laker.admin.common.model.PageResponse;
import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.audit.EasyAudit;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.system.dto.OnlineSessionView;
import com.laker.admin.module.system.dto.auth.AuthUserProfile;
import com.laker.admin.module.system.dto.profile.PasswordChangeRequest;
import com.laker.admin.module.system.dto.profile.ProfileLoginHistoryView;
import com.laker.admin.module.system.dto.profile.ProfileSecurityOverview;
import com.laker.admin.module.system.dto.profile.ProfileUpdateRequest;
import com.laker.admin.module.system.service.profile.ProfileSecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "个人中心")
@RestController
@RequestMapping("/api/profile")
public class ProfileSecurityController {
    private final ProfileSecurityService profileSecurityService;

    public ProfileSecurityController(ProfileSecurityService profileSecurityService) {
        this.profileSecurityService = profileSecurityService;
    }

    @GetMapping
    @EasyPermission(EasyPermissions.Profile.VIEW)
    @Operation(summary = "查询个人中心概览")
    public Response<ProfileSecurityOverview> overview() {
        return Response.ok(profileSecurityService.overview());
    }

    @PutMapping
    @EasyPermission(EasyPermissions.Profile.EDIT)
    @EasyAudit(module = "个人中心", action = "修改个人资料", dataChange = true, bizType = "PROFILE", changeType = "UPDATE")
    @Operation(summary = "修改当前用户资料")
    public Response<AuthUserProfile> updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        return Response.ok(profileSecurityService.updateProfile(request));
    }

    @PostMapping("/avatar")
    @EasyPermission(EasyPermissions.Profile.EDIT)
    @EasyAudit(module = "个人中心", action = "更新头像", dataChange = true, bizType = "PROFILE", changeType = "AVATAR")
    @Operation(summary = "上传并更新当前用户头像")
    public Response<AuthUserProfile> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Response.ok(profileSecurityService.uploadAvatar(file));
    }

    @PutMapping("/password")
    @EasyPermission(EasyPermissions.Profile.PASSWORD_CHANGE)
    @EasyAudit(module = "个人中心", action = "修改密码", dataChange = true, bizType = "PROFILE", changeType = "PASSWORD")
    @Operation(summary = "修改当前用户密码")
    public Response<Boolean> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        return Response.ok(profileSecurityService.changePassword(request));
    }

    @GetMapping("/login-history")
    @EasyPermission(EasyPermissions.Profile.VIEW)
    @Operation(summary = "查询当前用户登录历史")
    public PageResponse<ProfileLoginHistoryView> loginHistory(@RequestParam(required = false, defaultValue = "1") long page,
                                                              @RequestParam(required = false, defaultValue = "10") long limit,
                                                              @RequestParam(required = false) String keyword) {
        return profileSecurityService.pageLoginHistory(page, limit, keyword);
    }

    @GetMapping("/sessions")
    @EasyPermission(EasyPermissions.Profile.VIEW)
    @Operation(summary = "查询当前用户登录会话")
    public Response<List<OnlineSessionView>> sessions() {
        return Response.ok(profileSecurityService.listCurrentUserSessions());
    }

    @DeleteMapping("/sessions/{sessionId}")
    @EasyPermission(EasyPermissions.Profile.SESSION_MANAGE)
    @EasyAudit(module = "个人中心", action = "撤销本人登录会话", dataChange = true, bizType = "ONLINE_SESSION", bizId = "#sessionId", changeType = "REVOKE")
    @Operation(summary = "撤销指定本人登录会话")
    public Response<Boolean> revokeSession(@PathVariable Long sessionId) {
        return Response.ok(profileSecurityService.revokeSession(sessionId));
    }

    @DeleteMapping("/sessions/others")
    @EasyPermission(EasyPermissions.Profile.SESSION_MANAGE)
    @EasyAudit(module = "个人中心", action = "撤销其他登录会话", dataChange = true, bizType = "ONLINE_SESSION", changeType = "REVOKE_OTHERS")
    @Operation(summary = "撤销其他登录会话")
    public Response<Integer> revokeOtherSessions() {
        return Response.ok(profileSecurityService.revokeOtherSessions());
    }
}
