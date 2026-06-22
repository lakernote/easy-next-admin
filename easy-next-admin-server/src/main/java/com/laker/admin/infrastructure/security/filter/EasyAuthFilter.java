package com.laker.admin.infrastructure.security.filter;

import com.laker.admin.common.constant.EasyNextAdminConstants;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.exception.EasyAuthException;
import com.laker.admin.infrastructure.security.exception.EasyForbiddenException;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.infrastructure.security.service.EasyAuthService;
import com.laker.admin.infrastructure.security.support.EasyAuthRequestAttributes;
import com.laker.admin.infrastructure.security.support.EasySecurityHeaders;
import com.laker.admin.infrastructure.observability.trace.EasyMdcContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import org.springframework.util.StringUtils;

/**
 * 请求认证过滤器。
 *
 * <p>只负责尝试把 Bearer Token 转换成当前请求的登录用户上下文，不做 Controller 注解访问决策。
 * token 无效时先记录认证失败，由 MVC Interceptor 基于 HandlerMethod 判断公开接口放行还是受保护接口拒绝。</p>
 */
public class EasyAuthFilter extends OncePerRequestFilter {
    private final EasyAuthService authService;

    public EasyAuthFilter(EasyAuthService authService) {
        this.authService = authService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            authenticateIfPresent(request);
            filterChain.doFilter(request, response);
        } finally {
            // Servlet 容器会复用线程，请求结束必须清理，避免串用户。
            EasyMdcContext.remove(EasyNextAdminConstants.USER_ID);
            EasySecurityContext.clear();
        }
    }

    private void authenticateIfPresent(HttpServletRequest request) {
        try {
            EasyMdcContext.remove(EasyNextAdminConstants.USER_ID);
            String accessToken = resolveAccessToken(request);
            if (!StringUtils.hasText(accessToken)) {
                return;
            }
            AuthPrincipal principal = authService.authenticate(accessToken);
            EasySecurityContext.setPrincipal(principal);
            EasySecurityContext.setAccessToken(accessToken);
            EasyMdcContext.putOrRemove(EasyNextAdminConstants.USER_ID,
                    principal.getUserId() == null ? null : String.valueOf(principal.getUserId()));
        } catch (EasyAuthException | EasyForbiddenException ex) {
            request.setAttribute(EasyAuthRequestAttributes.AUTH_FAILURE, ex);
        }
    }

    private String resolveAccessToken(HttpServletRequest request) {
        String authorization = request.getHeader(EasySecurityHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        if (!authorization.startsWith(EasySecurityHeaders.BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(EasySecurityHeaders.BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            throw new EasyAuthException("未登录");
        }
        return token;
    }
}
