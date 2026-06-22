package com.laker.admin.infrastructure.security.masking;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EasySensitiveDataMaskerTest {

    private final EasySensitiveDataMasker masker = new EasySensitiveDataMasker(new ObjectMapper());

    @Test
    void shouldMaskSensitiveJsonFieldsForSharedInfrastructureUse() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("username", "admin");
        payload.put("password", "admin");
        payload.put("phone", "13800000000");
        payload.put("email", "admin@example.com");
        payload.put("realName", "陈经理");
        payload.put("idCard", "110101199001011234");
        payload.put("bankCard", "6200000000000000000");

        String json = masker.toSanitizedJson(payload);

        assertThat(json)
                .contains("\"username\":\"admin\"")
                .contains("\"password\":\"******\"")
                .contains("\"phone\":\"138****0000\"")
                .contains("\"email\":\"a***@example.com\"")
                .contains("\"realName\":\"陈*\"")
                .contains("\"idCard\":\"110101********1234\"")
                .contains("\"bankCard\":\"6200***********0000\"");
    }

    @Test
    void shouldCompactEmptyValuesForAuditRequestPayloads() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("page", "1");
        payload.put("limit", "10");
        payload.put("keyWord", "");
        payload.put("status", null);

        String json = masker.toSanitizedCompactJson(payload);

        assertThat(json)
                .isEqualTo("{\"page\":\"1\",\"limit\":\"10\"}")
                .doesNotContain("null");
    }

    @Test
    void shouldMaskSensitiveUriQueryAndIdentifyFrameworkValues() {
        assertThat(masker.maskUri("/api/auth/login?token=abc&keyword=admin"))
                .isEqualTo("/api/auth/login?token=******&keyword=admin");
        assertThat(masker.isRequestInfrastructureValue(new MockHttpServletRequest()))
                .isTrue();
    }

    @Test
    void shouldSanitizeJsonTextBeforeWritingApiResponseLogs() {
        String response = """
                {"success":true,"data":{"accessToken":"abc","idCard":"110101199001011234"}}
                """;

        String json = masker.sanitizeJsonText(response);

        assertThat(json)
                .contains("\"accessToken\":\"******\"")
                .contains("\"idCard\":\"110101********1234\"")
                .doesNotContain("110101199001011234");
    }

    @Test
    void shouldMaskSensitiveFieldsInPlainTextFallback() {
        String text = "realName=陈经理,idCard=110101199001011234,bankCard=6200000000000000000";

        String masked = masker.maskText(text);

        assertThat(masked)
                .contains("realName=******")
                .contains("idCard=******")
                .contains("bankCard=******")
                .doesNotContain("陈经理")
                .doesNotContain("110101199001011234")
                .doesNotContain("6200000000000000000");
    }

    @Test
    void shouldMaskFieldsAnnotatedOnDtoDuringJacksonSerialization() throws Exception {
        UserProfileView view = new UserProfileView(
                "陈经理",
                "13800000001",
                "manager@example.com",
                "6200000000000000000",
                "session-token"
        );

        String json = new ObjectMapper().writeValueAsString(view);

        assertThat(json)
                .contains("\"realName\":\"陈*\"")
                .contains("\"phone\":\"138****0001\"")
                .contains("\"email\":\"m***@example.com\"")
                .contains("\"bankCard\":\"6200***********0000\"")
                .contains("\"token\":\"******\"");
    }

    private record UserProfileView(
            @EasyMask(type = EasyMaskType.NAME) String realName,
            @EasyMask(type = EasyMaskType.PHONE) String phone,
            @EasyMask(type = EasyMaskType.EMAIL) String email,
            @EasyMask(type = EasyMaskType.BANK_CARD) String bankCard,
            @EasyMask String token) {
    }
}
