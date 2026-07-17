package com.laker.admin.module.assistant.tool;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 企业 Tool 的标准定义。
 *
 * <p>{@code name} 是稳定机器编码，{@code displayName} 是用户可读名称。
 * 模型选择语义只维护在 {@code description} 和输入 Schema 中；主体作用域、权限、执行模式、风险和输出合同
 * 只供运行时治理。{@code subjectScope} 描述真实业务主体，不能替代权限或数据范围校验。</p>
 *
 * <p>标准合同的核心形态如下。模型协议只接收 name、description 和 parameters；subjectScope 由
 * Runtime/Guardrail 消费。新增企业能力时实现一个 {@code AssistantTool} 并返回本定义即可：</p>
 * <pre>{@code
 * {
 *   "name": "workflow.task.list_my_pending",
 *   "description": "查询当前用户真实待办",
 *   "subjectScope": "CURRENT_USER",
 *   "parameters": {
 *     "type": "object",
 *     "additionalProperties": false,
 *     "properties": {}
 *   }
 * }
 * }</pre>
 */
public record ToolDefinition(
        String name,
        String displayName,
        String description,
        String requiredPermission,
        ToolExecutionMode executionMode,
        ToolRiskLevel riskLevel,
        List<ToolInputField> inputSchema,
        List<ToolOutputField> outputSchema,
        ToolSubjectScope subjectScope
) {
    /** 适用于不涉及业务主体的通用 Tool；企业数据 Tool 应使用完整构造器显式声明 subjectScope。 */
    public ToolDefinition(String name,
                          String displayName,
                          String description,
                          String requiredPermission,
                          ToolExecutionMode executionMode,
                          ToolRiskLevel riskLevel,
                          List<ToolInputField> inputSchema,
                          List<ToolOutputField> outputSchema) {
        this(name, displayName, description, requiredPermission, executionMode, riskLevel,
                inputSchema, outputSchema, ToolSubjectScope.NONE);
    }

    public ToolDefinition {
        name = text(name);
        displayName = text(displayName);
        description = text(description);
        requiredPermission = text(requiredPermission);
        if (executionMode == null) {
            throw new IllegalArgumentException("ToolDefinition.executionMode must not be null: " + name);
        }
        if (riskLevel == null) {
            throw new IllegalArgumentException("ToolDefinition.riskLevel must not be null: " + name);
        }
        if (subjectScope == null) {
            throw new IllegalArgumentException("ToolDefinition.subjectScope must not be null: " + name);
        }
        inputSchema = inputSchema == null ? List.of() : List.copyOf(inputSchema);
        outputSchema = outputSchema == null ? List.of() : List.copyOf(outputSchema);
        if (name.isEmpty()) {
            throw new IllegalArgumentException("ToolDefinition.name must not be blank");
        }
        if (!isCanonicalName(name)) {
            throw new IllegalArgumentException("ToolDefinition.name must use lowercase letters, digits, '.', '_' or '-': " + name);
        }
        if (displayName.isEmpty()) {
            throw new IllegalArgumentException("ToolDefinition.displayName must not be blank: " + name);
        }
        if (description.isEmpty()) {
            throw new IllegalArgumentException("ToolDefinition.description must not be blank: " + name);
        }
        requireUniqueNames(name, "input", inputSchema.stream().map(ToolInputField::name).toList());
        requireUniqueNames(name, "output", outputSchema.stream().map(ToolOutputField::name).toList());
        if (executionMode == ToolExecutionMode.READ_ONLY && riskLevel == ToolRiskLevel.CRITICAL) {
            throw new IllegalArgumentException("只读工具不能声明为 CRITICAL 风险");
        }
    }

    public boolean readOnly() {
        return executionMode == ToolExecutionMode.READ_ONLY;
    }

    public boolean requiresConfirmation() {
        return executionMode == ToolExecutionMode.REQUIRES_CONFIRMATION;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isCanonicalName(String value) {
        if (value.length() > 128 || value.startsWith(".") || value.endsWith(".") || value.contains("..")) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (!((current >= 'a' && current <= 'z')
                    || (current >= '0' && current <= '9')
                    || current == '.'
                    || current == '_'
                    || current == '-')) {
                return false;
            }
        }
        return true;
    }

    private static void requireUniqueNames(String toolName, String schemaName, List<String> names) {
        Set<String> unique = new HashSet<>();
        for (String fieldName : names) {
            if (!unique.add(fieldName)) {
                throw new IllegalArgumentException(
                        "ToolDefinition " + toolName + " contains duplicate " + schemaName + " field: " + fieldName
                );
            }
        }
    }
}
