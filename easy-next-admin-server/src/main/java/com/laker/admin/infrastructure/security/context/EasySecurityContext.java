package com.laker.admin.infrastructure.security.context;

import com.laker.admin.infrastructure.security.model.AuthPrincipal;

/**
 * 当前请求安全上下文。
 *
 * <p>认证过滤器识别出登录态后写入，Service、数据权限拦截器等下游组件按需读取。
 * 这里必须保持请求级生命周期，不能把 ThreadLocal 当缓存使用。</p>
 */
public final class EasySecurityContext {
    private static final ThreadLocal<AuthPrincipal> PRINCIPAL_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> ACCESS_TOKEN_HOLDER = new ThreadLocal<>();

    private EasySecurityContext() {
    }

    public static void setPrincipal(AuthPrincipal principal) {
        PRINCIPAL_HOLDER.set(principal);
    }

    public static AuthPrincipal getPrincipal() {
        return PRINCIPAL_HOLDER.get();
    }

    public static Long getUserId() {
        AuthPrincipal principal = getPrincipal();
        return principal == null ? null : principal.getUserId();
    }

    public static void setAccessToken(String accessToken) {
        ACCESS_TOKEN_HOLDER.set(accessToken);
    }

    public static String getAccessToken() {
        return ACCESS_TOKEN_HOLDER.get();
    }

    public static void clear() {
        // Tomcat 线程会复用，必须在请求结束时清理，避免串用户。
        PRINCIPAL_HOLDER.remove();
        ACCESS_TOKEN_HOLDER.remove();
    }
}
