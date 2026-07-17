package com.laker.admin.module.assistant.state;

import com.laker.admin.module.assistant.tool.AssistantToolResult;

import java.util.List;

/**
 * 把某类工具结果转换成下一轮可引用的结构化上下文。
 *
 * <p>Extractor 只做结构转换，不读取用户消息，也不判断用户是不是在说“第一个”。
 * 自然语言指代由后续 LLM 参数抽取根据 prompt 中的 references 来完成。</p>
 */
public interface AssistantRunReferenceExtractor {
    List<AssistantRunReference> extract(AssistantToolResult toolResult);
}
