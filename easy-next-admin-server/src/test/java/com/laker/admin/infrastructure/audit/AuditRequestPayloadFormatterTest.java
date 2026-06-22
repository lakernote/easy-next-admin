package com.laker.admin.infrastructure.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.laker.admin.infrastructure.security.masking.EasySensitiveDataMasker;
import com.laker.admin.module.system.dto.auth.AuthLoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AuditRequestPayloadFormatterTest {

    private final AuditRequestPayloadFormatter formatter =
            new AuditRequestPayloadFormatter(new EasySensitiveDataMasker(new ObjectMapper()));

    @Test
    void shouldSkipServletRequestAndFlattenSingleBusinessPayload() throws Exception {
        AuthLoginRequest request = new AuthLoginRequest();
        request.setUsername("admin");
        request.setPassword("admin");
        request.setCaptchaId("captcha-id");
        request.setCaptchaCode("abcd");

        String payload = formatter.format(method("login", AuthLoginRequest.class, HttpServletRequest.class),
                new Object[]{request, new MockHttpServletRequest()});

        assertThat(payload)
                .contains("\"username\":\"admin\"")
                .contains("\"password\":\"******\"")
                .contains("\"captchaId\":\"******\"")
                .contains("\"captchaCode\":\"******\"")
                .doesNotContain("servletRequest")
                .doesNotContain("MockHttpServletRequest")
                .doesNotContain("null");
    }

    @Test
    void shouldKeepNamedQueryArgumentsAndDropBlankValues() throws Exception {
        String payload = formatter.format(method("page", long.class, long.class, String.class, String.class),
                new Object[]{1L, 10L, "", null});

        assertThat(payload)
                .contains("\"page\":1")
                .contains("\"limit\":10")
                .doesNotContain("[]")
                .doesNotContain("\"\"")
                .doesNotContain("null");
    }

    private Method method(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return SampleController.class.getDeclaredMethod(name, parameterTypes);
    }

    static class SampleController {
        void login(AuthLoginRequest loginRequest, HttpServletRequest servletRequest) {
        }

        void page(long page, long limit, String keyWord, String status) {
        }
    }
}
