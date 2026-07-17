package com.laker.admin.module.assistant.runtime.model;

import java.util.Locale;

/**
 * 模型停止生成的标准原因。
 *
 * <p>不同供应商会返回 {@code stop}、{@code end_turn}、{@code max_tokens}、
 * {@code safety} 等不同字符串。协议适配层先在这里归一化，企业助手主循环只判断稳定枚举，
 * 不在核心流程散落供应商字符串。</p>
 */
public enum ModelFinishReason {
    /** 正常完成最终文本。 */
    STOP,
    /** 正常完成一个或多个 Tool Call。 */
    TOOL_CALLS,
    /** 供应商没有返回停止原因；Runtime 继续以结构化响应形态判断。 */
    UNSPECIFIED,
    /** 达到输出 Token/长度上限，内容或 Tool 参数可能不完整。 */
    MAX_TOKENS,
    /** 供应商安全策略过滤了内容。 */
    CONTENT_FILTER,
    /** 模型明确拒绝完成请求。 */
    REFUSAL,
    /** 供应商要求暂停并由调用方稍后续接。当前 Mini Runtime 不自动续接。 */
    PAUSED,
    /** 供应商报告协议或生成错误。 */
    ERROR,
    /** 非空但尚未识别的供应商停止原因；按企业 fail-closed 处理。 */
    UNKNOWN;

    public static ModelFinishReason fromProvider(String providerValue) {
        if (providerValue == null || providerValue.isBlank()) {
            return UNSPECIFIED;
        }
        String normalized = providerValue.trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        return switch (normalized) {
            case "stop", "end", "end_turn", "stop_sequence", "complete", "completed" -> STOP;
            case "tool_calls", "tool_call", "tool_use", "function_call", "function_calls" -> TOOL_CALLS;
            case "length", "max_tokens", "max_token", "max_output_tokens", "token_limit" -> MAX_TOKENS;
            case "content_filter", "content_filtered", "safety", "recitation", "blocklist",
                 "prohibited_content", "spii", "sensitive" -> CONTENT_FILTER;
            case "refusal", "refused" -> REFUSAL;
            case "pause_turn", "paused" -> PAUSED;
            case "error", "failed", "malformed_function_call", "unexpected_tool_call" -> ERROR;
            default -> UNKNOWN;
        };
    }

}
