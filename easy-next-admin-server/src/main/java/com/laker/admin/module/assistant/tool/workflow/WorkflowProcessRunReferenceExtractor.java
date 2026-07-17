package com.laker.admin.module.assistant.tool.workflow;

import com.laker.admin.module.assistant.state.AssistantRunReference;
import com.laker.admin.module.assistant.state.AssistantRunReferenceExtractor;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * 将流程列表工具结果暴露为下一轮可引用项。
 *
 * <p>这里不解析“第一个/第二个/这个”等用户表达，只把上一轮列表里的稳定业务标识交给 LLM。
 * 后续 ToolArgumentResolver 的 prompt 会携带这些 references，由模型决定是否把某一项填入 processIdentifier。</p>
 */
@Component
public class WorkflowProcessRunReferenceExtractor implements AssistantRunReferenceExtractor {
    @Override
    public List<AssistantRunReference> extract(AssistantToolResult toolResult) {
        if (toolResult == null
                || !ProcessQueryTool.TOOL_NAME.equals(toolResult.toolName())
                || !(toolResult.data() instanceof WorkflowProcessQueryResult result)) {
            return List.of();
        }
        List<WorkflowProcessToolItem> items = result.items();
        return IntStream.range(0, items.size())
                .mapToObj(index -> toReference(index, items.get(index)))
                .toList();
    }

    private static AssistantRunReference toReference(int index, WorkflowProcessToolItem item) {
        String identifier = StringUtils.hasText(item.businessId())
                ? item.businessId()
                : String.valueOf(item.instanceId());
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("index", index + 1);
        attributes.put("instanceId", item.instanceId());
        attributes.put("businessId", text(item.businessId()));
        attributes.put("businessType", text(item.businessType()));
        attributes.put("title", text(item.title()));
        attributes.put("currentNodeKey", text(item.currentNodeKey()));
        attributes.put("status", text(item.status()));
        attributes.put("source", text(item.source()));
        return new AssistantRunReference(
                "workflow.process",
                "流程 " + (index + 1),
                identifier,
                item.title() + " / " + identifier,
                attributes
        );
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
