package com.laker.admin.module.assistant.runtime;

import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.AssistantToolRunStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentResultFactoryTest {

    @Test
    void laterSuccessSuppressesRecoveredFailureFromUserSummaryButKeepsTraceData() {
        AssistantToolResult failedCandidate = AssistantToolResult.failed(
                "workflow.leave.detail",
                "没有找到当前用户可见的请假单"
        );
        AssistantToolResult successfulCandidate = new AssistantToolResult(
                "workflow.leave.detail",
                AssistantToolRunStatus.EXECUTED,
                "LEAVE_REQUEST_DETAIL",
                "请假单号：LV-1001，请假类型：病假",
                Map.of("requestNo", "LV-1001")
        );

        AssistantToolResult aggregate = AgentResultFactory.aggregateToolResults(
                List.of(failedCandidate, successfulCandidate)
        );

        assertThat(aggregate.status()).isEqualTo(AssistantToolRunStatus.PARTIAL_SUCCESS);
        assertThat(aggregate.summary())
                .isEqualTo("请假单号：LV-1001，请假类型：病假")
                .doesNotContain("没有找到");
        assertThat(aggregate.data()).isInstanceOfSatisfying(Map.class, data ->
                assertThat(data.get("results")).isEqualTo(List.of(failedCandidate, successfulCandidate))
        );
    }

    @Test
    void latestFailureIsStillVisibleWhenItWasNotRecovered() {
        AssistantToolResult success = new AssistantToolResult(
                "workflow.leave.detail",
                AssistantToolRunStatus.EXECUTED,
                "LEAVE_REQUEST_DETAIL",
                "找到第一张请假单",
                Map.of("requestNo", "LV-1001")
        );
        AssistantToolResult failure = AssistantToolResult.failed(
                "workflow.leave.detail",
                "没有找到第二张请假单"
        );

        AssistantToolResult aggregate = AgentResultFactory.aggregateToolResults(List.of(success, failure));

        assertThat(aggregate.summary()).contains("找到第一张请假单", "没有找到第二张请假单");
    }
}
