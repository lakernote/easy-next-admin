package com.laker.admin.module.assistant.tool;

/** 工具输出合同校验端口。 */
public interface ToolResultValidator {
    ValidationResult validate(ToolDefinition definition,
                              AssistantToolResult result,
                              ToolExecutionPhase phase);

    record ValidationResult(boolean valid, String reason) {
        public static ValidationResult success() {
            return new ValidationResult(true, "工具输出合同校验通过");
        }

        public static ValidationResult failure(String reason) {
            return new ValidationResult(false, reason);
        }
    }
}
