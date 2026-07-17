package com.laker.admin.module.workflow.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowTaskPolicyTest {

    @Test
    void shouldHoldAnyOneNodeWhenAddSignTaskIsStillPending() {
        boolean hold = WorkflowTaskPolicy.ANY_ONE.shouldHoldCurrentNode(false, 1, 0);

        assertThat(hold).isTrue();
    }

    @Test
    void shouldHoldAddSignApprovalWhenOriginalTaskIsStillPending() {
        boolean hold = WorkflowTaskPolicy.ANY_ONE.shouldHoldCurrentNode(true, 0, 1);

        assertThat(hold).isTrue();
    }

    @Test
    void shouldAdvanceAnyOneNodeWhenNoAddSignOrRegularTaskIsPending() {
        boolean hold = WorkflowTaskPolicy.ANY_ONE.shouldHoldCurrentNode(false, 0, 0);

        assertThat(hold).isFalse();
    }

    @Test
    void shouldHoldAllNodeUntilRegularTasksAreFinished() {
        assertThat(WorkflowTaskPolicy.ALL.shouldHoldCurrentNode(false, 0, 1)).isTrue();
        assertThat(WorkflowTaskPolicy.ALL.shouldHoldCurrentNode(false, 0, 0)).isFalse();
    }

    @Test
    void shouldCreateOnlyFirstTaskForSequentialNode() {
        assertThat(WorkflowTaskPolicy.SEQUENTIAL.initialAssignees(List.of(1L, 2L, 3L)))
                .containsExactly(1L);
        assertThat(WorkflowTaskPolicy.SEQUENTIAL.nextAssigneeAfter(List.of(1L, 2L, 3L), 2L))
                .contains(3L);
        assertThat(WorkflowTaskPolicy.SEQUENTIAL.nextAssigneeAfter(List.of(1L, 2L, 3L), 3L))
                .isEmpty();
    }
}
