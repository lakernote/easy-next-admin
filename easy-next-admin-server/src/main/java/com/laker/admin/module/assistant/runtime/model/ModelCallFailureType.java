package com.laker.admin.module.assistant.runtime.model;

/** 模型调用失败分类；只有明确的瞬时故障允许 AgentLoop 重试。 */
public enum ModelCallFailureType {
    TIMEOUT,
    NETWORK,
    RATE_LIMIT,
    SERVER_ERROR,
    AUTHENTICATION,
    CLIENT_ERROR,
    PROTOCOL_ERROR,
    UNKNOWN
}
