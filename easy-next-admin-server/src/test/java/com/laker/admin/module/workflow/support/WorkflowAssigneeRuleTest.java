package com.laker.admin.module.workflow.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowAssigneeRuleTest {

    @Test
    void shouldInferUserRuleFromAssigneeIds() {
        WorkflowAssigneeRule rule = WorkflowAssigneeRule.of("", List.of(10L, 10L, 20L), "");

        assertThat(rule.approverType()).isEqualTo(WorkflowApproverType.USER);
        assertThat(rule.assigneeIds()).containsExactly(10L, 20L);
        assertThat(rule.shouldFailWhenUnresolved()).isTrue();
    }

    @Test
    void shouldInferRoleRuleFromRoleCode() {
        WorkflowAssigneeRule rule = WorkflowAssigneeRule.of("", List.of(), "manager");

        assertThat(rule.approverType()).isEqualTo(WorkflowApproverType.ROLE);
        assertThat(rule.roleCode()).isEqualTo("manager");
        assertThat(rule.shouldFailWhenUnresolved()).isTrue();
    }

    @Test
    void shouldPreferExplicitApproverTypeOverOtherFields() {
        WorkflowAssigneeRule rule = WorkflowAssigneeRule.of("DEPT_LEADER", List.of(10L), "manager");

        assertThat(rule.approverType()).isEqualTo(WorkflowApproverType.DEPT_LEADER);
        assertThat(rule.assigneeIds()).containsExactly(10L);
    }

    @Test
    void shouldFailUnknownDeclaredRuleWhenRuntimeCannotResolveIt() {
        WorkflowAssigneeRule rule = WorkflowAssigneeRule.of("CUSTOM_RULE", List.of(), "");

        assertThat(rule.approverType()).isEqualTo(WorkflowApproverType.UNKNOWN);
        assertThat(rule.shouldFailWhenUnresolved()).isTrue();
    }
}
