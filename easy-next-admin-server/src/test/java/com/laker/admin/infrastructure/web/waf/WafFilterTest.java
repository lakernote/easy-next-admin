package com.laker.admin.infrastructure.web.waf;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class WafFilterTest {

    @Test
    void shouldApplyConfiguredSecurityHeaders() throws Exception {
        EasyNextAdminConfig.Web.SecurityHeaders securityHeaders = new EasyNextAdminConfig.Web.SecurityHeaders();
        securityHeaders.setContentSecurityPolicy("default-src 'self'");
        securityHeaders.setHstsEnabled(true);
        WafFilter filter = new WafFilter("", false, false, securityHeaders);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("SAMEORIGIN");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Content-Security-Policy")).isEqualTo("default-src 'self'");
        assertThat(response.getHeader("Strict-Transport-Security")).isEqualTo("max-age=31536000; includeSubDomains");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(response.getHeader("Permissions-Policy")).isEqualTo("geolocation=(), microphone=(), camera=()");
    }

    @Test
    void shouldKeepCspAndHstsOptInByDefault() throws Exception {
        WafFilter filter = new WafFilter("", false, false, new EasyNextAdminConfig.Web.SecurityHeaders());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("Content-Security-Policy")).isNull();
        assertThat(response.getHeader("Strict-Transport-Security")).isNull();
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
    }
}
