package com.laker.admin.module.assistant.tool;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistantToolRegistryTest {

    @Test
    void shouldFailFastWhenWriteToolDoesNotImplementConfirmableTool() {
        ToolDefinition definition = definition(ToolExecutionMode.REQUIRES_CONFIRMATION);
        AssistantTool invalidTool = new AssistantTool() {
            @Override
            public ToolDefinition definition() {
                return definition;
            }

            @Override
            public AssistantToolResult execute(AssistantToolExecutionContext context) {
                return AssistantToolResult.skipped("test");
            }
        };

        assertThatThrownBy(() -> new AssistantToolRegistry(List.of(invalidTool)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ConfirmableTool", definition.name());
    }

    @Test
    void shouldRejectDuplicateSchemaFieldsAtDefinitionTime() {
        assertThatThrownBy(() -> new ToolDefinition(
                "customer.contract.query",
                "查询合同",
                "查询当前用户可见合同。",
                "customer:contract:view",
                ToolExecutionMode.READ_ONLY,
                ToolRiskLevel.LOW,
                List.of(
                        new ToolInputField("keyword", "关键词", ToolSchemaType.STRING, ToolSchemaFormat.NONE,
                                "合同关键词。", false, List.of()),
                        new ToolInputField("keyword", "关键词", ToolSchemaType.STRING, ToolSchemaFormat.NONE,
                                "合同关键词。", false, List.of())
                ),
                List.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate input field", "keyword");
    }

    @Test
    void shouldNormalizeHumanMetadataAndExposeDerivedGovernanceFlags() {
        ToolDefinition definition = new ToolDefinition(
                " customer.contract.query ",
                " 查询合同 ",
                " 查询当前用户可见合同。 ",
                " customer:contract:view ",
                ToolExecutionMode.READ_ONLY,
                ToolRiskLevel.LOW,
                List.of(),
                List.of()
        );

        assertThat(definition.name()).isEqualTo("customer.contract.query");
        assertThat(definition.displayName()).isEqualTo("查询合同");
        assertThat(definition.description()).isEqualTo("查询当前用户可见合同。");
        assertThat(definition.requiredPermission()).isEqualTo("customer:contract:view");
        assertThat(definition.readOnly()).isTrue();
        assertThat(definition.requiresConfirmation()).isFalse();
    }

    @Test
    void shouldRejectUnsupportedStructuredInputAtDefinitionTime() {
        assertThatThrownBy(() -> new ToolInputField(
                "filters",
                "过滤条件",
                ToolSchemaType.OBJECT,
                ToolSchemaFormat.NONE,
                "结构化过滤条件。",
                false,
                List.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not support structured input fields", "filters");
    }

    @Test
    void shouldRejectDuplicateEnumValuesAtDefinitionTime() {
        assertThatThrownBy(() -> new ToolInputField(
                "status",
                "状态",
                ToolSchemaType.STRING,
                ToolSchemaFormat.NONE,
                "合同状态。",
                false,
                List.of("ACTIVE", "ACTIVE")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("enumValues must be unique", "status");
    }

    @Test
    void shouldRejectEnumLabelWithoutMatchingMachineValue() {
        assertThatThrownBy(() -> new ToolInputField(
                "status",
                "状态",
                ToolSchemaType.STRING,
                ToolSchemaFormat.NONE,
                "合同状态。",
                false,
                List.of("ACTIVE"),
                java.util.Map.of("DISABLED", "已停用")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("enumLabels keys must exist in enumValues", "status");
    }

    private static ToolDefinition definition(ToolExecutionMode mode) {
        return new ToolDefinition(
                "workflow.request.prepare",
                "准备申请",
                "生成待确认申请。",
                "workflow:request:create",
                mode,
                ToolRiskLevel.MEDIUM,
                List.of(),
                List.of()
        );
    }
}
