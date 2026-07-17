package com.laker.admin.module.assistant.tool.workflow;

import com.laker.admin.module.assistant.state.AssistantRunReference;
import com.laker.admin.module.assistant.state.AssistantRunReferenceExtractor;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将刚查询的单个流程保留为下一轮“它/这个流程”可以精确引用的最近实体。 */
@Component
public class WorkflowProcessDetailRunReferenceExtractor implements AssistantRunReferenceExtractor {
    @Override
    public List<AssistantRunReference> extract(AssistantToolResult toolResult) {
        if (toolResult == null
                || !ProcessDetailTool.TOOL_NAME.equals(toolResult.toolName())
                || !(toolResult.data() instanceof WorkflowProcessToolItem item)) {
            return List.of();
        }
        String identifier = StringUtils.hasText(item.businessId())
                ? item.businessId()
                : String.valueOf(item.instanceId());
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("index", 1);
        attributes.put("instanceId", item.instanceId());
        attributes.put("businessId", text(item.businessId()));
        attributes.put("businessType", text(item.businessType()));
        attributes.put("title", text(item.title()));
        attributes.put("currentNodeKey", text(item.currentNodeKey()));
        attributes.put("status", text(item.status()));
        attributes.put("source", text(item.source()));
        return List.of(new AssistantRunReference(
                "workflow.process",
                "当前流程",
                identifier,
                item.title() + " / " + identifier,
                attributes
        ));
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
