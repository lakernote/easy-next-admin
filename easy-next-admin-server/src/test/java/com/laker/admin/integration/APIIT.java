package com.laker.admin.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laker.admin.module.system.dto.auth.AuthLoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 集成测试-API集成测试
 *
 * @SpringBootTest用于加载完整的Spring上下文，进行端到端的集成测试。 可以理解为是一个正常启动的Spring Boot应用。
 * 数据库，缓存，消息队列等都会启动。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class APIIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testLogin() {
        AuthLoginRequest loginRequest = new AuthLoginRequest();
        ResponseEntity<String> response = restTemplate.postForEntity("/api/auth/login", loginRequest, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void testHelloWorld() throws Exception {
        // 发送 GET 请求
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        // 设置请求头
                        .accept(MediaType.APPLICATION_JSON)
                        // 设置请求体
                        .contentType(MediaType.APPLICATION_JSON)
                        // 设置请求体内容
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                // 验证响应状态码
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    void observabilityPageLoadsAndAuthenticatedApisReturnMetricsAndHttpTraces() throws Exception {
        mockMvc.perform(get("/easy-observability/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Easy Observability")));

        mockMvc.perform(get("/easy-observability/assets/dashboard.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/easy-observability")));

        mockMvc.perform(get("/api/easy-observability/metrics"))
                .andExpect(status().isUnauthorized());

        String loginResponse = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "192.0.2.10")
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(loginResponse).path("data").path("accessToken").asText();
        org.junit.jupiter.api.Assertions.assertFalse(accessToken.isBlank(), "login should return an access token");
        String authorization = "Bearer " + accessToken;

        String metricsResponse = mockMvc.perform(get("/api/easy-observability/metrics")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode metrics = objectMapper.readTree(metricsResponse);
        org.junit.jupiter.api.Assertions.assertTrue(metrics.isArray(), "metrics API should return a JSON array");
        org.junit.jupiter.api.Assertions.assertFalse(metrics.isEmpty(), "the application should expose Micrometer meters");

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk());

        String tracesResponse = mockMvc.perform(get("/api/easy-observability/traces")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode traces = objectMapper.readTree(tracesResponse);
        JsonNode profileTrace = null;
        for (JsonNode trace : traces) {
            if ("GET /api/auth/me".equals(trace.path("method").asText())) {
                profileTrace = trace;
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(profileTrace,
                "completed HTTP requests should be available in the standalone trace API");
        String traceId = profileTrace.path("traceId").asText();
        org.junit.jupiter.api.Assertions.assertFalse(traceId.isBlank(), "HTTP trace should retain its trace id");
        mockMvc.perform(get("/api/easy-observability/traces/{traceId}", traceId)
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("GET /api/auth/me")));
    }
}
