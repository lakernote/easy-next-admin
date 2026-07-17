package com.laker.admin.module.workflow.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowApproverTypeTest {

    @Test
    void shouldResolveKnownApproverCodes() {
        assertThat(WorkflowApproverType.of("USER")).isEqualTo(WorkflowApproverType.USER);
        assertThat(WorkflowApproverType.of("role")).isEqualTo(WorkflowApproverType.ROLE);
        assertThat(WorkflowApproverType.of("INITIATOR_SELECTED")).isEqualTo(WorkflowApproverType.INITIATOR_SELECTED);
        assertThat(WorkflowApproverType.of("MANAGER")).isEqualTo(WorkflowApproverType.MANAGER);
        assertThat(WorkflowApproverType.of("UPPER_DEPT_LEADER")).isEqualTo(WorkflowApproverType.UPPER_DEPT_LEADER);
    }

    @Test
    void shouldKeepUnknownRuleClosedByDefault() {
        assertThat(WorkflowApproverType.of("CUSTOM_RULE")).isEqualTo(WorkflowApproverType.UNKNOWN);
        assertThat(WorkflowApproverType.of("UNKNOWN_RULE")).isEqualTo(WorkflowApproverType.UNKNOWN);
        assertThat(WorkflowApproverType.UNKNOWN.acceptsPreferredAssignee()).isFalse();
        assertThat(WorkflowApproverType.MANAGER.acceptsPreferredAssignee()).isFalse();
        assertThat(WorkflowApproverType.DEPT_LEADER.acceptsPreferredAssignee()).isFalse();
        assertThat(WorkflowApproverType.INITIATOR_SELECTED.acceptsPreferredAssignee()).isTrue();
    }
}
