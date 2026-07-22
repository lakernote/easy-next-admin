package com.laker.admin.module.assistant.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "easy.assistant.model")
@Validated
public class AssistantModelProperties {
    public static final int MAX_MODEL_CALLS_LIMIT = 16;
    public static final int MAX_TOOL_CALLS_LIMIT = 32;
    public static final int MAX_READ_TOOL_ROUNDS_LIMIT = 8;
    /**
     * OpenAI-compatible Chat Completions 基础地址，例如 https://dashscope.aliyuncs.com/compatible-mode/v1。
     */
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    /**
     * Chat Completions 路径。保留为配置项，方便兼容讯飞 MaaS 这类 baseUrl=/v2、path=/chat/completions 的网关。
     */
    private String completionsPath = "/chat/completions";

    /**
     * 接口访问令牌。为空时不发送 Authorization 头，便于本地兼容接口调试。
     */
    private String apiKey = "";

    /**
     * Agent 推理使用的模型名。
     */
    private String model = "qwen-plus";

    /**
     * 单次模型调用超时时间。
     */
    private Duration timeout = Duration.ofSeconds(20);

    /**
     * Tool Calling 和企业问答优先稳定、可复现，默认低温度。
     */
    private double temperature = 0.0d;

    /**
     * 单次 Tool Calling 或最终回复的最大输出 token。
     */
    private int maxTokens = 800;

    /**
     * 单轮模型调用总预算。正常直答 1 次、一次查询 2 次；更长的多 Tool 链仍受硬上限保护。
     */
    @Min(2)
    @Max(MAX_MODEL_CALLS_LIMIT)
    private int maxModelCalls = 6;

    /** 单轮最多执行 Tool Call 次数，防止异常循环。 */
    @Min(1)
    @Max(MAX_TOOL_CALLS_LIMIT)
    private int maxToolCalls = 6;

    /**
     * 单轮允许模型基于 observation 继续规划的只读 Tool 批次数。
     * 值为 1 时等价于单 Tool round；默认允许三轮依赖型只读查询。
     */
    @Min(1)
    @Max(MAX_READ_TOOL_ROUNDS_LIMIT)
    private int maxReadToolRounds = 3;

    /**
     * 是否要求模型最终回复匹配严格 JSON Schema。默认关闭以兼容不同 OpenAI-compatible Provider。
     */
    private boolean structuredOutputEnabled;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getCompletionsPath() {
        return completionsPath;
    }

    public void setCompletionsPath(String completionsPath) {
        this.completionsPath = completionsPath;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getMaxModelCalls() {
        return maxModelCalls;
    }

    public void setMaxModelCalls(int maxModelCalls) {
        this.maxModelCalls = maxModelCalls;
    }

    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    public void setMaxToolCalls(int maxToolCalls) {
        this.maxToolCalls = maxToolCalls;
    }

    public int getMaxReadToolRounds() {
        return maxReadToolRounds;
    }

    public void setMaxReadToolRounds(int maxReadToolRounds) {
        this.maxReadToolRounds = maxReadToolRounds;
    }

    public boolean isStructuredOutputEnabled() {
        return structuredOutputEnabled;
    }

    public void setStructuredOutputEnabled(boolean structuredOutputEnabled) {
        this.structuredOutputEnabled = structuredOutputEnabled;
    }
}
