package com.laker.admin.module.assistant.tool;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultToolResultValidatorTest {
    private final DefaultToolResultValidator validator = new DefaultToolResultValidator();

    @Test
    void shouldValidateRequiredFieldsAndDeclaredTypes() {
        ToolDefinition definition = definition(List.of(
                new ToolOutputField("total", ToolSchemaType.INTEGER, true),
                new ToolOutputField("items", ToolSchemaType.ARRAY, true)
        ));

        ToolResultValidator.ValidationResult valid = validator.validate(
                definition,
                result(Map.of("total", 1L, "items", List.of(Map.of("id", 1L)))),
                ToolExecutionPhase.READ
        );
        ToolResultValidator.ValidationResult invalidType = validator.validate(
                definition,
                result(Map.of("total", "1", "items", List.of())),
                ToolExecutionPhase.READ
        );
        ToolResultValidator.ValidationResult missing = validator.validate(
                definition,
                result(Map.of("total", 1L)),
                ToolExecutionPhase.READ
        );

        assertThat(valid.valid()).isTrue();
        assertThat(invalidType.valid()).isFalse();
        assertThat(invalidType.reason()).contains("total");
        assertThat(missing.valid()).isFalse();
        assertThat(missing.reason()).contains("items");
    }

    @Test
    void shouldSkipFinalOutputSchemaForPreparePhase() {
        ToolDefinition definition = definition(List.of(
                new ToolOutputField("requestNo", ToolSchemaType.STRING, true)
        ));

        ToolResultValidator.ValidationResult validation = validator.validate(
                definition,
                result(Map.of("draft", true)),
                ToolExecutionPhase.PREPARE
        );

        assertThat(validation.valid()).isTrue();
    }

    private static ToolDefinition definition(List<ToolOutputField> outputSchema) {
        return new ToolDefinition(
                "customer.contract.query",
                "查询合同",
                "查询当前用户可见合同。",
                "customer:contract:view",
                ToolExecutionMode.READ_ONLY,
                ToolRiskLevel.LOW,
                List.of(),
                outputSchema
        );
    }

    private static AssistantToolResult result(Object data) {
        return new AssistantToolResult(
                "customer.contract.query",
                AssistantToolRunStatus.EXECUTED,
                "CONTRACT_LIST",
                "查询成功",
                data
        );
    }
}
