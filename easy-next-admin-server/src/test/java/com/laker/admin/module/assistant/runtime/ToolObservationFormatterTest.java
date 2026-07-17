package com.laker.admin.module.assistant.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laker.admin.infrastructure.json.EasyJsonCodec;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.AssistantToolRunStatus;
import com.laker.admin.module.assistant.tool.ToolDefinition;
import com.laker.admin.module.assistant.tool.ToolExecutionMode;
import com.laker.admin.module.assistant.tool.ToolInputField;
import com.laker.admin.module.assistant.tool.ToolOutputField;
import com.laker.admin.module.assistant.tool.ToolRiskLevel;
import com.laker.admin.module.assistant.tool.ToolSchemaFormat;
import com.laker.admin.module.assistant.tool.ToolSchemaType;
import com.laker.admin.module.assistant.tool.ToolSubjectScope;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionPlan;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionStatus;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ToolObservationFormatterTest {
    private final EasyJsonCodec jsonCodec = new EasyJsonCodec(new ObjectMapper());
    private final ToolObservationFormatter formatter = new ToolObservationFormatter(jsonCodec);

    @Test
    void shouldReturnOnlyContractDataWithoutDuplicatedRuntimeMetadata() {
        List<Map<String, Object>> items = IntStream.rangeClosed(1, 7)
                .mapToObj(index -> Map.<String, Object>of(
                        "id", index,
                        "title", "待办" + index + "x".repeat(400)
                ))
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", 7);
        data.put("items", items);
        data.put("internalDebug", "should-not-reach-model");
        AssistantToolResult result = new AssistantToolResult(
                "workflow.task.list_my_pending",
                AssistantToolRunStatus.EXECUTED,
                "TASK_LIST",
                "找到 7 个待办任务",
                data
        );

        JsonNode observation = jsonCodec.readTree(formatter.format(result, null, listDefinition()));

        assertThat(observation.path("status").asText()).isEqualTo("EXECUTED");
        assertThat(observation.path("complete").asBoolean()).isTrue();
        assertThat(observation.has("message")).isFalse();
        assertThat(observation.has("summary")).isFalse();
        assertThat(observation.has("artifactType")).isFalse();
        assertThat(observation.has("planStatus")).isFalse();
        assertThat(observation.path("data").has("internalDebug")).isFalse();
        assertThat(observation.path("data").path("items")).hasSize(5);
        assertThat(observation.path("data").path("items").get(0).path("title").asText())
                .endsWith("…")
                .hasSize(321);
        assertThat(observation.path("truncated").asBoolean()).isTrue();
    }

    @Test
    void shouldKeepFailureMessageAndOnlyActionableNextStep() {
        ToolInputField missingField = new ToolInputField(
                "processIdentifier",
                "流程标识",
                ToolSchemaType.STRING,
                ToolSchemaFormat.NONE,
                "流程实例号或业务单号",
                true,
                List.of()
        );
        ToolExecutionPlan plan = new ToolExecutionPlan(
                "run-1",
                "trace-1",
                ToolExecutionStatus.INPUT_REQUIRED,
                "workflow.process.detail",
                true,
                List.of(),
                List.of(missingField),
                List.of(),
                "缺少必填参数"
        );

        JsonNode observation = jsonCodec.readTree(formatter.format(
                AssistantToolResult.failed("workflow.process.detail", "请提供流程实例号或业务单号"),
                plan,
                null
        ));

        assertThat(observation.path("status").asText()).isEqualTo("FAILED");
        assertThat(observation.has("complete")).isFalse();
        assertThat(observation.path("message").asText()).contains("请提供流程实例号");
        assertThat(observation.has("data")).isFalse();
        assertThat(observation.path("next").path("state").asText()).isEqualTo("INPUT_REQUIRED");
        assertThat(observation.path("next").path("missing").get(0).asText()).isEqualTo("流程标识");
        assertThat(observation.path("next").has("confirmationRequired")).isFalse();
    }

    private static ToolDefinition listDefinition() {
        return new ToolDefinition(
                "workflow.task.list_my_pending",
                "查询我的待办",
                "用于查询当前用户的待办列表；不用于查询他人待办。",
                "workflow:view",
                ToolExecutionMode.READ_ONLY,
                ToolRiskLevel.LOW,
                List.of(),
                List.of(
                        new ToolOutputField("total", ToolSchemaType.INTEGER, true),
                        new ToolOutputField("items", ToolSchemaType.ARRAY, true)
                ),
                ToolSubjectScope.CURRENT_USER
        );
    }
}
