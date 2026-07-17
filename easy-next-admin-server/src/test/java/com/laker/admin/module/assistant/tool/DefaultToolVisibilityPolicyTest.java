package com.laker.admin.module.assistant.tool;

import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.assistant.tool.workflow.LeavePrepareTool;
import com.laker.admin.module.workflow.leave.support.LeavePeriodCalculator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.laker.admin.module.assistant.AssistantTestFixtures.requestContext;
import static com.laker.admin.module.assistant.AssistantTestFixtures.superAdminRequestContext;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultToolVisibilityPolicyTest {
    private static final String TEST_TOOL_NAME = "test.read";
    private final AssistantToolRegistry registry = new AssistantToolRegistry(List.of(
            readTool(),
            new LeavePrepareTool(new LeavePeriodCalculator())
    ));
    private final ToolVisibilityPolicy policy = new DefaultToolVisibilityPolicy(registry);

    @Test
    void shouldExposeEveryRegisteredToolThatCurrentUserMayExecute() {
        AssistantToolVisibilityPlan plan = policy.selectVisibleTools(requestContext(
                "帮我算一下，也想请假",
                EasyPermissions.Assistant.CHAT,
                EasyPermissions.Workflow.INSTANCE_START
        ));

        assertThat(plan.registeredToolNames())
                .containsExactly(TEST_TOOL_NAME, LeavePrepareTool.TOOL_NAME);
        assertThat(plan.tools()).extracting(AssistantVisibleTool::name)
                .containsExactly(TEST_TOOL_NAME, LeavePrepareTool.TOOL_NAME);
        assertThat(plan.missingEvidence()).isEmpty();
    }

    @Test
    void shouldNotUseIntentOrMessageTextToHidePermissionAllowedTools() {
        AssistantToolVisibilityPlan plan = policy.selectVisibleTools(requestContext(
                "你好",
                EasyPermissions.Assistant.CHAT
        ));

        assertThat(plan.registeredToolNames()).hasSize(2);
        assertThat(plan.tools()).extracting(AssistantVisibleTool::name)
                .containsExactly(TEST_TOOL_NAME);
    }

    @Test
    void shouldAllowSuperAdminAndReportNoToolsForUnprivilegedUser() {
        assertThat(policy.selectVisibleTools(superAdminRequestContext("你好")).tools())
                .extracting(AssistantVisibleTool::name)
                .containsExactly(TEST_TOOL_NAME, LeavePrepareTool.TOOL_NAME);

        AssistantToolVisibilityPlan denied = policy.selectVisibleTools(requestContext("你好", "unrelated:view"));
        assertThat(denied.tools()).isEmpty();
        assertThat(denied.missingEvidence()).containsExactly("tool.permission");
    }

    private static AssistantTool readTool() {
        ToolDefinition definition = new ToolDefinition(
                TEST_TOOL_NAME,
                "测试只读 Tool",
                "读取测试数据。",
                EasyPermissions.Assistant.CHAT,
                ToolExecutionMode.READ_ONLY,
                ToolRiskLevel.LOW,
                List.of(),
                List.of()
        );
        return new AssistantTool() {
            @Override
            public ToolDefinition definition() {
                return definition;
            }

            @Override
            public AssistantToolResult execute(AssistantToolExecutionContext context) {
                return new AssistantToolResult(
                        TEST_TOOL_NAME,
                        AssistantToolRunStatus.EXECUTED,
                        "TEST",
                        "测试完成",
                        null
                );
            }
        };
    }
}
