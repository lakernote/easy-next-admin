package com.laker.admin.module.assistant.tool.workflow;

import com.laker.admin.module.assistant.state.AssistantRunReference;
import com.laker.admin.module.assistant.state.AssistantRunReferenceExtractor;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * 将待办列表工具结果暴露为下一轮可引用项。
 *
 * <p>这里只保存待办条目的稳定标识和展示摘要，不解析“第一个/这个”等用户表达。
 * 下一轮模型看到 references 后，再决定用户指代的是哪一个待办。</p>
 */
@Component
public class WorkflowTaskRunReferenceExtractor implements AssistantRunReferenceExtractor {
    @Override
    public List<AssistantRunReference> extract(AssistantToolResult toolResult) {
        if (toolResult == null
                || !QueryMyTasksTool.TOOL_NAME.equals(toolResult.toolName())
                || !(toolResult.data() instanceof WorkflowTaskQueryResult result)) {
            return List.of();
        }
        List<WorkflowTaskToolItem> items = result.items();
        return IntStream.range(0, items.size())
                .mapToObj(index -> toReference(index, items.get(index)))
                .toList();
    }

    private static AssistantRunReference toReference(int index, WorkflowTaskToolItem item) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("index", index + 1);
        attributes.put("taskId", item.taskId());
        attributes.put("instanceId", item.instanceId());
        attributes.put("instanceTitle", text(item.instanceTitle()));
        attributes.put("nodeName", text(item.nodeName()));
        attributes.put("status", text(item.status()));
        attributes.put("startedAt", item.startedAt() == null ? "" : item.startedAt().toString());
        return new AssistantRunReference(
                "workflow.task",
                "待办 " + (index + 1),
                String.valueOf(item.taskId()),
                displayValue(item),
                attributes
        );
    }

    private static String displayValue(WorkflowTaskToolItem item) {
        String title = text(item.instanceTitle());
        String nodeName = text(item.nodeName());
        if (title.isBlank()) {
            return nodeName;
        }
        if (nodeName.isBlank()) {
            return title;
        }
        return title + " / " + nodeName;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
