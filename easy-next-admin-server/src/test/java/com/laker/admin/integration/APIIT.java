package com.laker.admin.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laker.admin.common.constant.EasyNextAdminConstants;
import com.laker.admin.module.assistant.config.AssistantRuntimeConfiguration;
import com.laker.admin.module.system.dto.auth.AuthLoginRequest;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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

    @Autowired
    @Qualifier("easyThreadPool")
    private ThreadPoolTaskExecutor easyThreadPool;

    @Autowired
    @Qualifier(AssistantRuntimeConfiguration.STREAM_EXECUTOR_BEAN)
    private ThreadPoolTaskExecutor assistantStreamExecutor;

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
    void meerkatPageLoadsAndTraceApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/meerkat/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Meerkat")));

        mockMvc.perform(get("/meerkat/assets/common.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("api/meerkat")));

        mockMvc.perform(get("/api/meerkat/traces"))
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

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk());

        String tracesResponse = mockMvc.perform(get("/api/meerkat/traces")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode traces = objectMapper.readTree(tracesResponse);
        org.junit.jupiter.api.Assertions.assertTrue(traces.isObject(), "trace API should return a page object");
        org.junit.jupiter.api.Assertions.assertTrue(traces.path("items").isArray(),
                "trace page should contain an items array");
    }

    @Test
    void meerkatDecoratesApplicationThreadPools() throws InterruptedException {
        assertMeerkatContextPropagation(easyThreadPool);
        assertMeerkatContextPropagation(assistantStreamExecutor);
    }

    private static void assertMeerkatContextPropagation(ThreadPoolTaskExecutor executor)
            throws InterruptedException {
        String traceId = "0123456789abcdef0123456789abcdef";
        String userId = "7";
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> workerTraceId = new AtomicReference<>();
        AtomicReference<String> workerUserId = new AtomicReference<>();
        try {
            MDC.put(EasyNextAdminConstants.TRACE_ID, traceId);
            MDC.put(EasyNextAdminConstants.USER_ID, userId);
            executor.execute(() -> {
                workerTraceId.set(MDC.get(EasyNextAdminConstants.TRACE_ID));
                workerUserId.set(MDC.get(EasyNextAdminConstants.USER_ID));
                latch.countDown();
            });
        } finally {
            MDC.clear();
        }

        org.junit.jupiter.api.Assertions.assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals(traceId, workerTraceId.get());
        assertEquals(userId, workerUserId.get());
    }
}
