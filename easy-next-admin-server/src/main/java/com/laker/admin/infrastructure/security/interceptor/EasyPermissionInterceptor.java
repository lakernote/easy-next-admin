package com.laker.admin.infrastructure.security.interceptor;

import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.annotation.EasyPermissionMode;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.exception.EasyAuthException;
import com.laker.admin.infrastructure.security.exception.EasyForbiddenException;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.infrastructure.security.support.EasyAuthAnnotations;
import com.laker.admin.infrastructure.security.support.EasyAuthRequestAttributes;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;

/**
 * Controller 权限拦截器。
 *
 * <p>只读取 Controller 类或方法上的权限注解，并基于 {@link EasySecurityContext}
 * 中的登录用户判断是否放行，不关心 token 如何解析。</p>
 */
@Component
public class EasyPermissionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if (isIgnored(handlerMethod)) {
            return true;
        }
        RuntimeException authFailure = authenticationFailure(request);
        if (authFailure != null) {
            throw authFailure;
        }
        AuthPrincipal principal = EasySecurityContext.getPrincipal();
        if (principal == null) {
            throw new EasyAuthException("未登录");
        }
        checkPermission(handlerMethod, principal);
        return true;
    }

    private boolean isIgnored(HandlerMethod handlerMethod) {
        return EasyAuthAnnotations.isIgnored(handlerMethod);
    }

    private RuntimeException authenticationFailure(HttpServletRequest request) {
        Object failure = request.getAttribute(EasyAuthRequestAttributes.AUTH_FAILURE);
        if (failure instanceof EasyAuthException authException) {
            return authException;
        }
        if (failure instanceof EasyForbiddenException forbiddenException) {
            return forbiddenException;
        }
        return null;
    }

    private void checkPermission(HandlerMethod handlerMethod, AuthPrincipal principal) {
        EasyPermission permission = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), EasyPermission.class);
        if (permission == null) {
            permission = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), EasyPermission.class);
        }
        if (permission == null || permission.value().length == 0 || principal.isSuperAdmin()) {
            return;
        }
        boolean allowed = permission.mode() == EasyPermissionMode.ANY
                ? Arrays.stream(permission.value()).anyMatch(principal::hasPermission)
                : Arrays.stream(permission.value()).allMatch(principal::hasPermission);
        if (!allowed) {
            throw new EasyForbiddenException("没有权限访问该资源");
        }
    }
}
