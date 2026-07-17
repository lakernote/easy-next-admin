package com.laker.admin.infrastructure.web.handler;

import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.security.exception.EasyAuthException;
import com.laker.admin.infrastructure.security.exception.EasyForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldHandleBusinessExceptionAsBadRequestByDefault() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/workflow/tasks/1/approve");

        ResponseEntity<Response<Void>> response = handler.handleRRException(new BusinessException("只能处理分配给自己的流程任务"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(400100);
        assertThat(response.getBody().getMessage()).isEqualTo("只能处理分配给自己的流程任务");
    }

    @Test
    void shouldKeepHttpStatusAndBusinessCodeSeparate() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/system/files/404/download");

        ResponseEntity<Response<Void>> response = handler.handleRRException(
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在"),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(404000);
        assertThat(response.getBody().getMessage()).isEqualTo("文件不存在");
    }

    @Test
    void shouldHandleAuthExceptionWithoutRequestContextHolder() {
        RequestContextHolder.resetRequestAttributes();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/system/users");
        request.setQueryString("page=1");

        ResponseEntity<Response<Void>> response = handler.handleEasyAuthException(new EasyAuthException("未登录"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(401000);
        assertThat(response.getBody().getMessage()).isEqualTo("未登录");
    }

    @Test
    void shouldPreserveSpecificAuthErrorCode() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");

        ResponseEntity<Response<Void>> response = handler.handleEasyAuthException(
                new EasyAuthException(ErrorCode.AUTH_INVALID_CREDENTIALS, "用户名或密码不正确"),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(401001);
        assertThat(response.getBody().getMessage()).isEqualTo("用户名或密码不正确");
    }

    @Test
    void shouldRejectForbiddenCodeInAuthException() {
        assertThatThrownBy(() -> new EasyAuthException(ErrorCode.AUTH_ACCOUNT_DISABLED, "账号已被禁用"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("EasyAuthException 只能使用 401 认证错误码");
    }

    @Test
    void shouldHandleForbiddenExceptionWithoutRequestContextHolder() {
        RequestContextHolder.resetRequestAttributes();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/system/roles");

        ResponseEntity<Response<Void>> response = handler.handleEasyForbiddenException(new EasyForbiddenException("没有权限访问该资源"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(403000);
        assertThat(response.getBody().getMessage()).isEqualTo("没有权限访问该资源");
    }

    @Test
    void shouldPreserveSpecificForbiddenErrorCode() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");

        ResponseEntity<Response<Void>> response = handler.handleEasyForbiddenException(
                new EasyForbiddenException(ErrorCode.AUTH_ACCOUNT_DISABLED, "账号已被禁用"),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(403001);
        assertThat(response.getBody().getMessage()).isEqualTo("账号已被禁用");
    }
}
