package com.laker.admin.module.assistant.state;

import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.AssistantToolRunStatus;
import com.laker.admin.module.assistant.tool.workflow.ProcessDetailTool;
import com.laker.admin.module.assistant.tool.workflow.WorkflowProcessDetailRunReferenceExtractor;
import com.laker.admin.module.assistant.tool.workflow.WorkflowProcessToolItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantRunReferenceExtractorRegistryTest {

    @Test
    void shouldNormalizeIndexesAcrossAggregatedResultsOfTheSameType() {
        AssistantToolResult first = processDetail(9001L, "LV-001", "第一份请假申请");
        AssistantToolResult second = processDetail(9002L, "LV-002", "第二份请假申请");
        AssistantToolResult aggregate = new AssistantToolResult(
                "system.multiple_tools",
                AssistantToolRunStatus.EXECUTED,
                "MULTI_TOOL_RESULT",
                "已完成两个查询",
                Map.of("results", List.of(first, second))
        );
        AssistantRunReferenceExtractorRegistry registry = new AssistantRunReferenceExtractorRegistry(
                List.of(new WorkflowProcessDetailRunReferenceExtractor())
        );

        List<AssistantRunReference> references = registry.extract(aggregate);

        assertThat(references).extracting(AssistantRunReference::value)
                .containsExactly("LV-001", "LV-002");
        assertThat(references).extracting(reference -> reference.attributes().get("index"))
                .containsExactly(1, 2);
    }

    private static AssistantToolResult processDetail(Long instanceId, String businessId, String title) {
        return new AssistantToolResult(
                ProcessDetailTool.TOOL_NAME,
                AssistantToolRunStatus.EXECUTED,
                "PROCESS_DETAIL",
                "流程详情 " + businessId,
                new WorkflowProcessToolItem(
                        instanceId,
                        "LEAVE",
                        businessId,
                        title,
                        "manager",
                        "RUNNING",
                        null,
                        "runtime"
                )
        );
    }
}
