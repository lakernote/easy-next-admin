package com.laker.admin.infrastructure.web.context;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class EasyRequestContextTest {

    @Test
    void currentRequestShouldReturnEmptyWithoutRequestAttributes() {
        RequestContextHolder.resetRequestAttributes();

        assertThat(EasyRequestContext.currentRequest()).isEmpty();
        assertThat(EasyRequestContext.currentRequestUri()).isEqualTo("-");
        assertThat(EasyRequestContext.currentRemoteIp()).isEqualTo("-");
    }

    @Test
    void shouldResolveRequestUriAndClientIp() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/system/users");
        request.setQueryString("page=1");
        request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(EasyRequestContext.currentRequestUri()).isEqualTo("/api/system/users?page=1");
        assertThat(EasyRequestContext.currentRemoteIp()).isEqualTo("10.0.0.1");

        RequestContextHolder.resetRequestAttributes();
    }
}
