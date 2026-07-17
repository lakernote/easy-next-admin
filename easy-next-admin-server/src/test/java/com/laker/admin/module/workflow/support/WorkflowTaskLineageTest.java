package com.laker.admin.module.workflow.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowTaskLineageTest {

    @Test
    void shouldUseOriginalAssigneeAsSequentialCursorAfterTransfer() {
        Long cursorAssigneeId = WorkflowTaskLineage.sequentialCursorAssigneeId(
                List.of(10L, 20L, 30L),
                99L,
                List.of(new WorkflowTaskLineage.Delegation(20L, WorkflowTaskDelegationType.TRANSFER)));

        assertThat(cursorAssigneeId).isEqualTo(20L);
    }

    @Test
    void shouldUseOriginalAssigneeAsSequentialCursorAfterDelegate() {
        Long cursorAssigneeId = WorkflowTaskLineage.sequentialCursorAssigneeId(
                List.of(10L, 20L, 30L),
                99L,
                List.of(new WorkflowTaskLineage.Delegation(20L, WorkflowTaskDelegationType.DELEGATE)));

        assertThat(cursorAssigneeId).isEqualTo(20L);
    }

    @Test
    void shouldUseAddSignOriginWhenAddSignTaskCompletesAfterOriginalTask() {
        Long cursorAssigneeId = WorkflowTaskLineage.sequentialCursorAssigneeId(
                List.of(10L, 20L, 30L),
                99L,
                List.of(new WorkflowTaskLineage.Delegation(10L, WorkflowTaskDelegationType.ADD_SIGN)));

        assertThat(cursorAssigneeId).isEqualTo(10L);
    }

    @Test
    void shouldPreferApprovedAssigneeWhenItBelongsToSequentialChain() {
        Long cursorAssigneeId = WorkflowTaskLineage.sequentialCursorAssigneeId(
                List.of(10L, 20L, 30L),
                20L,
                List.of(new WorkflowTaskLineage.Delegation(10L, WorkflowTaskDelegationType.ADD_SIGN)));

        assertThat(cursorAssigneeId).isEqualTo(20L);
    }

    @Test
    void shouldResolveKnownDelegationTypes() {
        assertThat(WorkflowTaskDelegationType.of("add_sign")).isEqualTo(WorkflowTaskDelegationType.ADD_SIGN);
        assertThat(WorkflowTaskDelegationType.of("TRANSFER")).isEqualTo(WorkflowTaskDelegationType.TRANSFER);
        assertThat(WorkflowTaskDelegationType.of("delegate")).isEqualTo(WorkflowTaskDelegationType.DELEGATE);
    }

    @Test
    void shouldRejectUnknownDelegationTypes() {
        assertThatThrownBy(() -> WorkflowTaskDelegationType.of("COPY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的任务委派类型");
    }
}
