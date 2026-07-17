package com.laker.admin.module.assistant.runtime.model;

/** 可替换的原生 Tool Calling 模型端口。 */
public interface ToolCallingModelClient {
    ToolCallingResponse call(ToolCallingRequest request);
}
