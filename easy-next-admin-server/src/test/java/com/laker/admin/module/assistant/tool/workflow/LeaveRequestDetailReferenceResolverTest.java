package com.laker.admin.module.assistant.tool.workflow;

import com.laker.admin.module.assistant.state.AssistantConversationState;
import com.laker.admin.module.assistant.tool.AssistantToolArgument;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.AssistantToolRunStatus;
import com.laker.admin.module.assistant.tool.AssistantVisibleTool;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.laker.admin.module.assistant.AssistantTestFixtures.requestContext;
import static org.assertj.core.api.Assertions.assertThat;

class LeaveRequestDetailReferenceResolverTest {
    private final LeaveRequestDetailReferenceResolver resolver = new LeaveRequestDetailReferenceResolver();
    private final AssistantVisibleTool tool = AssistantVisibleTool.from(
            new LeaveRequestDetailTool((user, identifier) -> java.util.Optional.empty()).definition()
    );

    @Test
    void ordinalUsesCurrentTurnProcessDetailOrderInsteadOfDatabaseRecordId() {
        AssistantToolArgument resolved = resolve("2");

        assertThat(resolved.value()).isEqualTo("LV-20260716-000001");
        assertThat(resolved.displayValue()).isEqualTo("LV-20260716-000001");
    }

    @Test
    void workflowInstanceIdUsesItsCurrentTurnBusinessIdentifier() {
        AssistantToolArgument resolved = resolve("2077679717136416770");

        assertThat(resolved.value()).isEqualTo("LV-20260716-000001");
    }

    @Test
    void unrelatedExplicitIdentifierIsNotRewritten() {
        AssistantToolArgument resolved = resolve("LV-EXPLICIT-0001");

        assertThat(resolved.value()).isEqualTo("LV-EXPLICIT-0001");
    }

    private AssistantToolArgument resolve(String identifier) {
        List<AssistantToolArgument> resolved = resolver.resolveReferences(
                requestContext("参考第二个请假单"),
                tool,
                AssistantConversationState.empty(),
                List.of(new AssistantToolArgument(
                        "requestIdentifier",
                        "请假单标识",
                        identifier,
                        identifier
                )),
                List.of(
                        processDetail(2079860827360935937L, "LV-20260722-000001"),
                        processDetail(2077679717136416770L, "LV-20260716-000001")
                )
        );
        return resolved.get(0);
    }

    private static AssistantToolResult processDetail(long instanceId, String businessId) {
        return new AssistantToolResult(
                ProcessDetailTool.TOOL_NAME,
                AssistantToolRunStatus.EXECUTED,
                "PROCESS_DETAIL",
                "流程详情 " + businessId,
                new WorkflowProcessToolItem(
                        instanceId,
                        "leave",
                        businessId,
                        "请假申请",
                        "manager",
                        "RUNNING",
                        null,
                        "runtime"
                )
        );
    }
}
