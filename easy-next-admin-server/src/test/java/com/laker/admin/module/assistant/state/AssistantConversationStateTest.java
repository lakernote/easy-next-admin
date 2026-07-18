package com.laker.admin.module.assistant.state;

import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionStatus;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionPlan;
import com.laker.admin.module.assistant.tool.AssistantToolArgument;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.AssistantToolRunStatus;
import com.laker.admin.module.assistant.tool.ToolInputField;
import com.laker.admin.module.assistant.tool.ToolSchemaFormat;
import com.laker.admin.module.assistant.tool.ToolSchemaType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.laker.admin.module.assistant.AssistantTestFixtures.requestContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistantConversationStateTest {

    @Test
    void shouldCommitPendingActionAndCompleteConversationTurnInOneSnapshot() {
        StateFixture fixture = new StateFixture();
        var context = requestContext("明天下午请病假");
        ToolExecutionPlan draftPlan = writePlan(ToolExecutionStatus.APPROVAL_REQUIRED);

        AssistantConversationState written = fixture.writer().writeTurn(
                context,
                fixture.store().load(context),
                AssistantPendingActionUpdate.upsert(AssistantPendingAction.from(context, draftPlan)),
                AssistantToolResult.skipped("已生成草稿"),
                "已生成请假草稿，请确认。"
        );

        AssistantConversationState loaded = fixture.store().load(context);
        assertThat(written.version()).isEqualTo(1);
        assertThat(loaded).isEqualTo(written);
        assertThat(loaded.pendingAction()).isNotNull();
        assertThat(loaded.pendingAction().status())
                .isEqualTo(AssistantPendingActionStatus.WAITING_FOR_CONFIRMATION);
        assertThat(loaded.pendingAction().originatingUserMessage()).isEqualTo("明天下午请病假");
        assertThat(loaded.chatMemory().recentTurns()).singleElement().satisfies(turn -> {
            assertThat(turn.userMessage()).isEqualTo("明天下午请病假");
            assertThat(turn.assistantMessage()).isEqualTo("已生成请假草稿，请确认。");
        });
    }

    @Test
    void shouldPreservePendingActionWhenUserTemporarilyAsksReadOnlyQuestion() {
        StateFixture fixture = new StateFixture();
        var context = requestContext("先帮我算 1+1");
        AssistantConversationState initial = stateWithPendingAction(context);
        fixture.store().save(context, 0, initial);

        fixture.writer().writeTurn(
                context,
                initial,
                AssistantPendingActionUpdate.unchanged(),
                AssistantToolResult.skipped("未调用 Tool"),
                "2"
        );

        AssistantConversationState loaded = fixture.store().load(context);
        assertThat(loaded.pendingAction()).isEqualTo(initial.pendingAction());
        assertThat(loaded.version()).isEqualTo(2);
    }

    @Test
    void shouldClearPendingActionAfterConfirmedExecution() {
        StateFixture fixture = new StateFixture();
        var context = requestContext("确认提交");
        AssistantConversationState initial = stateWithPendingAction(context);
        fixture.store().save(context, 0, initial);

        fixture.writer().writeTurn(
                context,
                initial,
                AssistantPendingActionUpdate.clear(),
                AssistantToolResult.skipped("已提交"),
                "已提交。"
        );

        assertThat(fixture.store().load(context).pendingAction()).isNull();
    }

    @Test
    void shouldRejectStaleSnapshotInsteadOfOverwritingNewerTurn() {
        InMemoryAssistantConversationStateStore store = new InMemoryAssistantConversationStateStore();
        var context = requestContext("并发消息");
        AssistantConversationState first = new AssistantConversationState(
                1, AssistantChatMemory.empty(), null, null, context.requestInstant());
        store.save(context, 0, first);

        AssistantConversationState staleWrite = new AssistantConversationState(
                1, AssistantChatMemory.empty(), null, null, context.requestInstant());

        assertThatThrownBy(() -> store.save(context, 0, staleWrite))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("会话状态已更新");
        assertThat(store.load(context)).isEqualTo(first);
    }

    @Test
    void shouldExpirePendingActionAndLastResultWithoutDroppingChatMemory() {
        Instant oldTime = Instant.parse("2026-07-07T05:00:00Z");
        AssistantPendingAction pendingAction = new AssistantPendingAction(
                "run", "trace", "workflow.leave.prepare", "帮我请假", false,
                AssistantPendingActionStatus.COLLECTING, "action", "idem",
                List.of(), List.of(), oldTime
        );
        AssistantLastResult lastResult = new AssistantLastResult(
                "workflow.task.list_my_pending", "workflow.task.list", "找到 1 个待办",
                List.of(), oldTime
        );
        AssistantChatMemory memory = new AssistantChatMemory(List.of(new AssistantConversationTurn(
                "查待办", "找到 1 个待办", oldTime
        )));
        AssistantConversationState state = new AssistantConversationState(
                3, memory, pendingAction, lastResult, oldTime
        );

        AssistantConversationState active = state.activeAt(Instant.parse("2026-07-07T06:30:00Z"));

        assertThat(active.pendingAction()).isNull();
        assertThat(active.lastResult()).isNull();
        assertThat(active.chatMemory()).isEqualTo(memory);
        assertThat(active.version()).isEqualTo(3);
    }

    @Test
    void shouldKeepWholeTurnsWhenMemoryWindowEvictsOldConversation() {
        StateFixture fixture = new StateFixture();
        AssistantConversationState current = AssistantConversationState.empty();

        for (int i = 1; i <= 7; i++) {
            var context = requestContext("问题" + i);
            current = fixture.writer().writeTurn(
                    context,
                    current,
                    AssistantPendingActionUpdate.unchanged(),
                    AssistantToolResult.skipped("未调用 Tool"),
                    "回答" + i
            );
        }

        assertThat(current.chatMemory().recentTurns()).hasSize(6);
        assertThat(current.chatMemory().recentTurns())
                .extracting(AssistantConversationTurn::userMessage)
                .containsExactly("问题2", "问题3", "问题4", "问题5", "问题6", "问题7");
        assertThat(current.chatMemory().recentTurns())
                .allMatch(AssistantConversationTurn::complete);
        assertThat(new ConversationHistorySelector().selectRecentTurns(current, 1))
                .extracting(AssistantConversationTurn::userMessage, AssistantConversationTurn::assistantMessage)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("问题7", "回答7"));
    }

    @Test
    void shouldKeepNewestWholeTurnsWithinOneConversationTokenBudget() {
        String longText = "长会话内容".repeat(120);
        List<AssistantConversationTurn> turns = java.util.stream.IntStream.rangeClosed(1, 6)
                .mapToObj(index -> new AssistantConversationTurn(
                        "问题" + index + longText,
                        "回答" + index + longText,
                        Instant.parse("2026-07-07T0" + index + ":00:00Z")
                ))
                .toList();
        AssistantConversationState state = new AssistantConversationState(
                1,
                new AssistantChatMemory(turns),
                null,
                null,
                Instant.parse("2026-07-07T07:00:00Z")
        );

        List<AssistantConversationTurn> selected = new ConversationHistorySelector()
                .selectRecentTurns(state);

        assertThat(selected).hasSize(1);
        assertThat(selected.get(0).userMessage()).startsWith("问题6").hasSizeLessThanOrEqualTo(303);
        assertThat(selected.get(0).assistantMessage()).startsWith("回答6").hasSizeLessThanOrEqualTo(303);
        assertThat(selected.stream()
                .mapToInt(turn -> turn.userMessage().length() + turn.assistantMessage().length())
                .sum()).isLessThanOrEqualTo(1_200);
    }

    @Test
    void shouldStoreOnlyBusinessResultNeededByTheNextTurn() {
        InMemoryAssistantConversationStateStore store = new InMemoryAssistantConversationStateStore();
        AssistantRunReference reference = new AssistantRunReference(
                "workflow.task", "第1个待办", "90001", "请假审批",
                Map.of("instanceId", "80001")
        );
        AssistantConversationStateWriter writer = new AssistantConversationStateWriter(
                store,
                new AssistantRunReferenceExtractorRegistry(List.of(result -> List.of(reference)))
        );
        var context = requestContext("查一下我的待办");
        AssistantToolResult toolResult = new AssistantToolResult(
                "workflow.task.list_my_pending",
                AssistantToolRunStatus.EXECUTED,
                "workflow.task.list",
                "找到 1 个待办",
                Map.of("total", 1)
        );

        AssistantConversationState written = writer.writeTurn(
                context,
                AssistantConversationState.empty(),
                AssistantPendingActionUpdate.unchanged(),
                toolResult,
                "你当前有 1 个待办。"
        );

        assertThat(written.lastResult()).isNotNull();
        assertThat(written.lastResult().toolName()).isEqualTo("workflow.task.list_my_pending");
        assertThat(written.lastResult().summary()).isEqualTo("找到 1 个待办");
        assertThat(written.lastResult().references()).singleElement().satisfies(savedReference -> {
            assertThat(savedReference.value()).isEqualTo("90001");
            assertThat(savedReference.attributes()).containsEntry("index", 1);
        });
    }

    private static AssistantConversationState stateWithPendingAction(
            com.laker.admin.module.assistant.context.AssistantRequestContext context) {
        return new AssistantConversationState(
                1,
                AssistantChatMemory.empty(),
                AssistantPendingAction.from(context, writePlan(ToolExecutionStatus.APPROVAL_REQUIRED)),
                null,
                context.requestInstant()
        );
    }

    private static ToolExecutionPlan writePlan(ToolExecutionStatus decision) {
        return new ToolExecutionPlan(
                "run-agent-001",
                "trace-agent-001",
                decision,
                "workflow.leave.prepare",
                false,
                List.of(new AssistantToolArgument("leaveType", "请假类型", "SICK", "病假")),
                decision == ToolExecutionStatus.INPUT_REQUIRED
                        ? List.of(new ToolInputField("reason", "请假原因", ToolSchemaType.STRING,
                        ToolSchemaFormat.NONE, "原因", true, List.of()))
                        : List.of(),
                List.of(),
                "test",
                "act-1",
                "idem-1"
        );
    }

    private record StateFixture(InMemoryAssistantConversationStateStore store) {
        StateFixture() {
            this(new InMemoryAssistantConversationStateStore());
        }

        AssistantConversationStateWriter writer() {
            return new AssistantConversationStateWriter(
                    store,
                    new AssistantRunReferenceExtractorRegistry(List.of())
            );
        }
    }
}
