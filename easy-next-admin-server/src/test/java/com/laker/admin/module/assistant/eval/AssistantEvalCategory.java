package com.laker.admin.module.assistant.eval;

/** 企业 Agent 评测维度；场景文件只能使用这些稳定分类。 */
enum AssistantEvalCategory {
    DIRECT_REPLY,
    KNOWLEDGE_BOUNDARY,
    TOOL_SELECTION,
    PARAMETER_EXTRACTION,
    MULTI_TURN,
    WRITE_GOVERNANCE,
    MULTI_GOAL,
    PROMPT_INJECTION,
    FAILURE_RECOVERY
}
