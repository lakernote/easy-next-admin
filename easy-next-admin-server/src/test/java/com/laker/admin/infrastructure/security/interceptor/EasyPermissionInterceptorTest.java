package com.laker.admin.infrastructure.security.interceptor;

import com.laker.admin.infrastructure.security.annotation.EasyIgnoreAuth;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.exception.EasyAuthException;
import com.laker.admin.infrastructure.security.exception.EasyForbiddenException;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.infrastructure.security.support.EasyAuthRequestAttributes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EasyPermissionInterceptorTest {
    private final EasyPermissionInterceptor interceptor = new EasyPermissionInterceptor();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/system/users");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void tearDown() {
        EasySecurityContext.clear();
    }

    @Test
    void shouldAllowIgnoredEndpointWithoutLogin() throws Exception {
        assertThat(interceptor.preHandle(request, response, handlerMethod("publicEndpoint"))).isTrue();
    }

    @Test
    void shouldRejectProtectedEndpointWhenNotLoggedIn() {
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod("protectedEndpoint")))
                .isInstanceOf(EasyAuthException.class)
                .hasMessage("未登录");
    }

    @Test
    void shouldRaiseRecordedAuthenticationFailureForProtectedEndpoint() {
        request.setAttribute(EasyAuthRequestAttributes.AUTH_FAILURE, new EasyAuthException("登录已过期，请重新登录"));

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod("protectedEndpoint")))
                .isInstanceOf(EasyAuthException.class)
                .hasMessage("登录已过期，请重新登录");
    }

    @Test
    void shouldRejectLoggedInUserWithoutRequiredPermission() {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(1L)
                .permissions(List.of("sys:role:list"))
                .build());

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod("protectedEndpoint")))
                .isInstanceOf(EasyForbiddenException.class)
                .hasMessage("没有权限访问该资源");
    }

    @Test
    void shouldAllowLoggedInUserWithRequiredPermission() throws Exception {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(1L)
                .permissions(List.of("sys:user:list"))
                .build());

        assertThat(interceptor.preHandle(request, response, handlerMethod("protectedEndpoint"))).isTrue();
    }

    private HandlerMethod handlerMethod(String methodName) throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod(methodName);
        return new HandlerMethod(new TestController(), method);
    }

    static class TestController {
        @EasyIgnoreAuth
        void publicEndpoint() {
        }

        @EasyPermission("sys:user:list")
        void protectedEndpoint() {
        }
    }
}
