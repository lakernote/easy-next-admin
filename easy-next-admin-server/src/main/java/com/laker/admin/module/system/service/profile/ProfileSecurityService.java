package com.laker.admin.module.system.service.profile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.common.model.PageResponse;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.infrastructure.security.model.AuthSession;
import com.laker.admin.infrastructure.security.service.EasyAuthService;
import com.laker.admin.infrastructure.security.store.AuthSessionStore;
import com.laker.admin.infrastructure.security.support.EasyPasswordHasher;
import com.laker.admin.module.audit.entity.AuditLoginLog;
import com.laker.admin.module.audit.service.IAuditLoginLogService;
import com.laker.admin.module.audit.service.SensitiveAuditService;
import com.laker.admin.module.system.dto.AuthProfileDto;
import com.laker.admin.module.system.dto.OnlineSessionView;
import com.laker.admin.module.system.dto.auth.AuthUserProfile;
import com.laker.admin.module.system.dto.profile.PasswordChangeRequest;
import com.laker.admin.module.system.dto.profile.ProfileLoginHistoryView;
import com.laker.admin.module.system.dto.profile.ProfileSecurityOverview;
import com.laker.admin.module.system.dto.profile.ProfileUpdateRequest;
import com.laker.admin.module.system.entity.SysFile;
import com.laker.admin.module.system.entity.SysUser;
import com.laker.admin.module.system.service.ISysUserService;
import com.laker.admin.module.system.service.storage.EasyStorageFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class ProfileSecurityService {
    private static final long MAX_AVATAR_SIZE = 5L * 1024L * 1024L;
    private static final Set<String> AVATAR_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final EasyAuthService authService;
    private final ISysUserService userService;
    private final IAuditLoginLogService loginLogService;
    private final AuthSessionStore authSessionStore;
    private final EasyPasswordHasher passwordHasher;
    private final SensitiveAuditService sensitiveAuditService;
    private final EasyStorageFacade storageFacade;

    public ProfileSecurityService(EasyAuthService authService,
                                  ISysUserService userService,
                                  IAuditLoginLogService loginLogService,
                                  AuthSessionStore authSessionStore,
                                  EasyPasswordHasher passwordHasher,
                                  SensitiveAuditService sensitiveAuditService,
                                  EasyStorageFacade storageFacade) {
        this.authService = authService;
        this.userService = userService;
        this.loginLogService = loginLogService;
        this.authSessionStore = authSessionStore;
        this.passwordHasher = passwordHasher;
        this.sensitiveAuditService = sensitiveAuditService;
        this.storageFacade = storageFacade;
    }

    public ProfileSecurityOverview overview() {
        AuthProfileDto profile = authService.getCurrentProfile();
        return ProfileSecurityOverview.builder()
                .user(profile.getUser())
                .activeSessions(listCurrentUserSessions())
                .loginHistory(loginHistory(10))
                .build();
    }

    public List<OnlineSessionView> listCurrentUserSessions() {
        Long userId = EasySecurityContext.getUserId();
        return authSessionStore.listActive(1, 10000).stream()
                .filter(session -> Objects.equals(session.getUserId(), userId))
                .map(this::toOnlineSessionView)
                .toList();
    }

    public List<ProfileLoginHistoryView> loginHistory(int limit) {
        List<AuditLoginLog> logs = loginLogService.list(Wrappers.<AuditLoginLog>lambdaQuery()
                .eq(AuditLoginLog::getUserId, EasySecurityContext.getUserId())
                .orderByDesc(AuditLoginLog::getLoginTime)
                .last("limit " + Math.max(1, Math.min(limit, 50))));
        return ProfileLoginHistoryView.fromList(logs);
    }

    public PageResponse<ProfileLoginHistoryView> pageLoginHistory(long page, long limit, String keyword) {
        LambdaQueryWrapper<AuditLoginLog> wrapper = Wrappers.<AuditLoginLog>lambdaQuery()
                .eq(AuditLoginLog::getUserId, EasySecurityContext.getUserId())
                .and(StringUtils.hasText(keyword), query -> query
                        .like(AuditLoginLog::getIp, keyword)
                        .or()
                        .like(AuditLoginLog::getClientType, keyword)
                        .or()
                        .like(AuditLoginLog::getUserAgent, keyword))
                .orderByDesc(AuditLoginLog::getLoginTime);
        Page<AuditLoginLog> pageResult = loginLogService.page(new Page<>(page, limit), wrapper);
        return PageResponse.ok(ProfileLoginHistoryView.fromList(pageResult.getRecords()), pageResult.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthUserProfile updateProfile(ProfileUpdateRequest request) {
        Long userId = EasySecurityContext.getUserId();
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setNickName(request.getNickName());
        user.setRealName(StringUtils.hasText(request.getRealName()) ? request.getRealName() : request.getNickName());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setAvatar(request.getAvatar());
        userService.updateById(user);
        sensitiveAuditService.record("个人中心", "修改个人资料", "SYS_USER", String.valueOf(userId), "{\"profile\":\"updated\"}");
        return authService.getCurrentProfile().getUser();
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthUserProfile uploadAvatar(MultipartFile file) {
        validateAvatar(file);
        try {
            SysFile stored = storageFacade.store(file.getInputStream(), file.getSize(), file.getContentType(), avatarFilename(file));
            String avatar = resolveAvatarUrl(stored);
            userService.updateCurrentAvatar(avatar);
            sensitiveAuditService.record("个人中心", "更新头像", "SYS_USER", String.valueOf(EasySecurityContext.getUserId()), "{\"avatar\":\"updated\"}");
            return authService.getCurrentProfile().getUser();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "头像上传失败", ex);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean changePassword(PasswordChangeRequest request) {
        if (!Objects.equals(request.getNewPassword(), request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "两次输入密码不一致");
        }
        Long userId = EasySecurityContext.getUserId();
        SysUser user = userService.getById(userId);
        if (user == null || !passwordHasher.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "旧密码错误");
        }
        SysUser update = new SysUser();
        update.setUserId(userId);
        update.setPassword(passwordHasher.hash(request.getNewPassword()));
        boolean changed = userService.updateById(update);
        if (changed) {
            revokeOtherSessions();
            sensitiveAuditService.record("个人中心", "修改登录密码", "SYS_USER", String.valueOf(userId), "{\"password\":\"changed\"}");
        }
        return changed;
    }

    @Transactional(rollbackFor = Exception.class)
    public int revokeOtherSessions() {
        AuthPrincipal principal = authService.currentPrincipal();
        List<AuthSession> sessions = authSessionStore.listActive(1, 10000).stream()
                .filter(session -> Objects.equals(session.getUserId(), principal.getUserId()))
                .filter(session -> !Objects.equals(session.getSessionId(), principal.getSessionId()))
                .toList();
        sessions.forEach(session -> authSessionStore.revoke(session.getSessionId(), AuthSession.STATUS_KICKED));
        sensitiveAuditService.record("个人中心", "撤销其他登录会话", "AUTH_SESSION", String.valueOf(principal.getUserId()), "{\"revoked\":" + sessions.size() + "}");
        return sessions.size();
    }

    private OnlineSessionView toOnlineSessionView(AuthSession session) {
        return OnlineSessionView.builder()
                .sessionId(session.getSessionId())
                .userId(session.getUserId())
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
    }

    private void validateAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "头像文件不能为空");
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE, "头像图片不能超过 5MB");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!AVATAR_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.MEDIA_TYPE_NOT_SUPPORTED, "头像仅支持 JPG、PNG 或 WEBP 图片");
        }
    }

    private String avatarFilename(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (StringUtils.hasText(originalFilename) && originalFilename.contains(".")) {
            return originalFilename;
        }
        String baseName = StringUtils.hasText(originalFilename) ? originalFilename : "avatar";
        return baseName + "." + avatarExtension(file.getContentType());
    }

    private String avatarExtension(String contentType) {
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        return switch (normalizedContentType) {
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            default -> "png";
        };
    }

    private String resolveAvatarUrl(SysFile stored) {
        if ("LOCAL".equals(stored.getStorageType()) && StringUtils.hasText(stored.getStorageName())) {
            return "/storage/" + stored.getStorageName();
        }
        return stored.getFilePath();
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean revokeSession(Long sessionId) {
        AuthPrincipal principal = authService.currentPrincipal();
        AuthSession session = authSessionStore.findBySessionId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "会话不存在"));
        if (!Objects.equals(session.getUserId(), principal.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能操作其他用户会话");
        }
        if (Objects.equals(session.getSessionId(), principal.getSessionId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能下线当前会话");
        }
        authSessionStore.revoke(sessionId, AuthSession.STATUS_KICKED);
        sensitiveAuditService.record("个人中心", "撤销登录会话", "AUTH_SESSION", String.valueOf(sessionId), "{\"status\":\"KICKED\"}");
        return true;
    }
}
