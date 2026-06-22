package com.laker.admin.infrastructure.security.filter;

import com.laker.admin.common.constant.EasyNextAdminConstants;
import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.exception.EasyAuthException;
import com.laker.admin.infrastructure.security.interceptor.EasyPermissionInterceptor;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.infrastructure.security.service.EasyCaptchaService;
import com.laker.admin.infrastructure.security.service.EasyAuthService;
import com.laker.admin.infrastructure.security.support.EasyAuthRequestAttributes;
import com.laker.admin.infrastructure.security.support.EasySecurityHeaders;
import com.laker.admin.module.system.controller.AuthController;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EasyAuthFilterTest {
    private final EasyAuthService authService = mock(EasyAuthService.class);
    private final EasyAuthFilter filter = new EasyAuthFilter(authService);

    @AfterEach
    void tearDown() {
        EasySecurityContext.clear();
        MDC.clear();
    }

    @Test
    void shouldAuthenticateBearerTokenAndClearContextAfterRequest() throws Exception {
        AuthPrincipal principal = AuthPrincipal.builder()
                .userId(1L)
                .userName("admin")
                .permissions(List.of("sys:user:list"))
                .build();
        when(authService.authenticate("access-token")).thenReturn(principal);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/system/users");
        request.addHeader(EasySecurityHeaders.AUTHORIZATION, EasySecurityHeaders.BEARER_PREFIX + "access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
            assertThat(EasySecurityContext.getPrincipal()).isSameAs(principal);
            assertThat(EasySecurityContext.getAccessToken()).isEqualTo("access-token");
            assertThat(MDC.get(EasyNextAdminConstants.USER_ID)).isEqualTo("1");
        };

        filter.doFilter(request, response, chain);

        verify(authService).authenticate("access-token");
        assertThat(EasySecurityContext.getPrincipal()).isNull();
        assertThat(EasySecurityContext.getAccessToken()).isNull();
        assertThat(MDC.get(EasyNextAdminConstants.USER_ID)).isNull();
    }

    @Test
    void shouldSkipAuthenticationWhenBearerTokenIsMissing() throws Exception {
        MDC.put(EasyNextAdminConstants.USER_ID, "stale-user");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/system/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            assertThat(EasySecurityContext.getPrincipal()).isNull();
            assertThat(MDC.get(EasyNextAdminConstants.USER_ID)).isNull();
        });

        verify(authService, never()).authenticate(org.mockito.ArgumentMatchers.anyString());
        assertThat(EasySecurityContext.getPrincipal()).isNull();
        assertThat(MDC.get(EasyNextAdminConstants.USER_ID)).isNull();
    }

    @Test
    void shouldClearUserIdWhenAuthenticatedPrincipalHasNoUserId() throws Exception {
        MDC.put(EasyNextAdminConstants.USER_ID, "stale-user");
        AuthPrincipal principal = AuthPrincipal.builder()
                .userName("anonymous")
                .permissions(List.of("sys:user:list"))
                .build();
        when(authService.authenticate("access-token")).thenReturn(principal);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/system/users");
        request.addHeader(EasySecurityHeaders.AUTHORIZATION, EasySecurityHeaders.BEARER_PREFIX + "access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertThat(MDC.get(EasyNextAdminConstants.USER_ID)).isNull());

        assertThat(MDC.get(EasyNextAdminConstants.USER_ID)).isNull();
    }

    @Test
    void shouldAuthenticateBearerTokenWithoutResolvingMvcHandler() throws Exception {
        AuthPrincipal principal = AuthPrincipal.builder()
                .userId(1L)
                .userName("admin")
                .permissions(List.of("sys:user:list"))
                .build();
        when(authService.authenticate("access-token")).thenReturn(principal);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.addHeader(EasySecurityHeaders.AUTHORIZATION, EasySecurityHeaders.BEARER_PREFIX + "access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertThat(EasySecurityContext.getPrincipal()).isSameAs(principal));

        verify(authService).authenticate("access-token");
    }

    @Test
    void shouldLetIgnoredHandlerProceedWhenRequestCarriesInvalidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/demo-accounts");
        request.addHeader(EasySecurityHeaders.AUTHORIZATION, EasySecurityHeaders.BEARER_PREFIX + "stale-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        EasyAuthException authFailure = new EasyAuthException(ErrorCode.AUTH_SESSION_EXPIRED, "登录已过期，请重新登录");
        when(authService.authenticate("stale-token")).thenThrow(authFailure);
        EasyPermissionInterceptor interceptor = new EasyPermissionInterceptor();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        Object ignoredHandler = ignoreAuthHandler("listDemoAccounts").getHandler();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            chainCalled.set(true);
            assertThat(interceptor.preHandle(request, response, ignoredHandler)).isTrue();
        });

        assertThat(chainCalled).isTrue();
        assertThat(request.getAttribute(EasyAuthRequestAttributes.AUTH_FAILURE)).isSameAs(authFailure);
        assertThat(EasySecurityContext.getPrincipal()).isNull();
    }

    @Test
    void shouldSkipOptionsPreflightRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/system/users");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    private HandlerExecutionChain ignoreAuthHandler(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        AuthController controller = new AuthController(authService, mock(EasyCaptchaService.class), new MockEnvironment());
        Method method = AuthController.class.getMethod(methodName, parameterTypes);
        return new HandlerExecutionChain(new HandlerMethod(controller, method));
    }
}
