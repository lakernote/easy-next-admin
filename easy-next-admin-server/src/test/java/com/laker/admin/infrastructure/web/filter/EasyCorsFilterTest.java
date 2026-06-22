package com.laker.admin.infrastructure.web.filter;

import com.laker.admin.common.constant.EasyNextAdminConstants;
import com.laker.admin.config.properties.EasyNextAdminConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EasyCorsFilterTest {

    @Test
    void shouldAllowConfiguredOriginForPreflightRequest() throws Exception {
        EasyNextAdminConfig.Web.Cors cors = new EasyNextAdminConfig.Web.Cors();
        cors.setAllowedOrigins(List.of("http://localhost:5174"));
        EasyCorsFilter filter = new EasyCorsFilter(cors);
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/sys/user");
        request.addHeader(HttpHeaders.ORIGIN, "http://localhost:5174");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo("http://localhost:5174");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isEqualTo("true");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)).contains("PUT");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)).contains("Authorization", "Content-Type");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
                .contains(EasyNextAdminConstants.TRACE_ID_HEADER);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void shouldRejectUnconfiguredOrigin() throws Exception {
        EasyNextAdminConfig.Web.Cors cors = new EasyNextAdminConfig.Web.Cors();
        cors.setAllowedOrigins(List.of("http://localhost:5174"));
        EasyCorsFilter filter = new EasyCorsFilter(cors);
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/sys/user");
        request.addHeader(HttpHeaders.ORIGIN, "https://evil.example");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void shouldContinueActualCorsRequestWhenOriginIsAllowed() throws Exception {
        EasyNextAdminConfig.Web.Cors cors = new EasyNextAdminConfig.Web.Cors();
        cors.setAllowedOrigins(List.of("http://localhost:5174"));
        EasyCorsFilter filter = new EasyCorsFilter(cors);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sys/user");
        request.addHeader(HttpHeaders.ORIGIN, "http://localhost:5174");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo("http://localhost:5174");
        assertThat(chain.getRequest()).isSameAs(request);
    }
}
