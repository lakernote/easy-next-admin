package com.laker.admin.module.assistant.tool;

import com.laker.admin.module.assistant.tool.execution.ToolExecutionStatus;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.laker.admin.module.assistant.AssistantTestFixtures.requestContext;
import static org.assertj.core.api.Assertions.assertThat;

class ToolExecutionPolicyTest {
    private final ToolExecutionPolicy policy = new DefaultToolExecutionPolicy();

    @Test
    void shouldEnforcePermissionAgainAtExecutionTime() {
        ToolExecutionPolicy.PolicyDecision decision = policy.evaluate(
                requestContext("查询"),
                plan(ToolExecutionStatus.READY, true),
                tool(definition(ToolExecutionMode.READ_ONLY, ToolRiskLevel.LOW, "customer:view"))
        );

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("权限");
    }

    @Test
    void shouldNeverExecuteCriticalWriteInsideMiniAgent() {
        ToolExecutionPolicy.PolicyDecision decision = policy.evaluate(
                requestContext("确认", "critical:write"),
                plan(ToolExecutionStatus.APPROVED, false),
                tool(definition(ToolExecutionMode.REQUIRES_CONFIRMATION, ToolRiskLevel.CRITICAL, "critical:write"))
        );

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("外部审批");
    }

    private static ToolExecutionPlan plan(ToolExecutionStatus decision, boolean readOnly) {
        return new ToolExecutionPlan(
                "run-1", "trace-1", decision, "test.tool", readOnly,
                List.of(), List.of(), List.of(), "test"
        );
    }

    private static ToolDefinition definition(ToolExecutionMode mode, ToolRiskLevel risk, String permission) {
        return new ToolDefinition(
                "test.tool", "测试 Tool", "测试企业执行策略。", permission,
                mode, risk, List.of(), List.of()
        );
    }

    private static AssistantTool tool(ToolDefinition definition) {
        return new AssistantTool() {
            @Override
            public ToolDefinition definition() {
                return definition;
            }

            @Override
            public AssistantToolResult execute(AssistantToolExecutionContext context) {
                return AssistantToolResult.skipped("test");
            }
        };
    }
}
