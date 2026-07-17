package com.laker.admin.module.assistant.tool;

import com.laker.admin.module.assistant.state.AssistantChatMemory;
import com.laker.admin.module.assistant.state.AssistantConversationState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.laker.admin.module.assistant.AssistantTestFixtures.requestContext;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultAssistantToolArgumentResolverTest {
    private final AssistantToolArgumentResolver resolver = new DefaultAssistantToolArgumentResolver(
            new AssistantToolArgumentReferenceResolverRegistry(List.of())
    );

    @Test
    void shouldBindOnlyDeclaredSchemaFieldsWithoutAnotherModelCall() {
        AssistantVisibleTool tool = visibleTool();

        AssistantToolArgumentResolution resolution = resolver.resolveToolCallArguments(
                requestContext("帮我处理这个申请"),
                tool,
                Map.of("type", "ANNUAL", "unknown", "must-not-pass"),
                emptyState()
        );

        assertThat(resolution.argumentMap()).containsExactlyEntriesOf(Map.of("type", "ANNUAL"));
        assertThat(resolution.missingRequiredParameters())
                .extracting(ToolInputField::name)
                .containsExactly("startTime");
    }

    @Test
    void shouldNormalizeOffsetDateTimeThenLetValidatorRejectInvalidEnum() {
        var context = requestContext("明天开始");
        var tool = visibleTool();
        AssistantToolArgumentResolution resolution = resolver.resolveToolCallArguments(
                context,
                tool,
                Map.of("type", "NOT_ALLOWED", "startTime", "2026-07-08T06:30:00Z"),
                emptyState()
        );

        assertThat(resolution.argumentMap())
                .containsEntry("startTime", "2026-07-08T14:30")
                .containsEntry("type", "NOT_ALLOWED");

        AssistantToolArgumentResolution validated = new DefaultAssistantToolArgumentValidator(List.of()).validate(
                context,
                AssistantToolSelection.selected(tool, List.of(tool), "test"),
                resolution
        );
        assertThat(validated.argumentMap()).doesNotContainKey("type");
        assertThat(validated.missingRequiredParameters())
                .extracting(ToolInputField::name)
                .containsExactly("type");
    }

    private static AssistantVisibleTool visibleTool() {
        return AssistantVisibleTool.from(new ToolDefinition(
                "workflow.request.prepare",
                "准备申请",
                "准备需要确认的企业申请。",
                "",
                ToolExecutionMode.REQUIRES_CONFIRMATION,
                ToolRiskLevel.MEDIUM,
                List.of(
                        new ToolInputField("type", "类型", ToolSchemaType.STRING, ToolSchemaFormat.NONE,
                                "申请类型", true, List.of("ANNUAL", "SICK")),
                        new ToolInputField("startTime", "开始时间", ToolSchemaType.STRING, ToolSchemaFormat.DATE_TIME,
                                "ISO 时间", true, List.of())
                ),
                List.of()
        ));
    }

    private static AssistantConversationState emptyState() {
        return AssistantConversationState.empty();
    }
}
