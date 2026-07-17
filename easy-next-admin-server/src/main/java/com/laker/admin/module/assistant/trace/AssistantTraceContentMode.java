package com.laker.admin.module.assistant.trace;

/** Agent Trace 对业务内容的采集级别。 */
public enum AssistantTraceContentMode {
    /** 生产默认：只记录标识、计数、状态和耗时，不记录消息、参数与结果正文。 */
    METADATA_ONLY,
    /** 本地调试：记录经过统一脱敏和长度限制的结构化内容。 */
    SANITIZED_CONTENT
}
