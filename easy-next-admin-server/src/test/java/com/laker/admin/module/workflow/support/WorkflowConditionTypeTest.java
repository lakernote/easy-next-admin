package com.laker.admin.module.workflow.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowConditionTypeTest {

    @Test
    void shouldDefaultBlankConditionToAlways() {
        WorkflowConditionType type = WorkflowConditionType.of("");

        assertThat(type).isEqualTo(WorkflowConditionType.ALWAYS);
        assertThat(type.isConditional()).isFalse();
    }

    @Test
    void shouldResolveExpressionCondition() {
        WorkflowConditionType type = WorkflowConditionType.of("expression");

        assertThat(type).isEqualTo(WorkflowConditionType.EXPRESSION);
        assertThat(type.isConditional()).isTrue();
    }

    @Test
    void shouldRejectUnsupportedConditionTypeNames() {
        assertThat(WorkflowConditionType.of("default")).isEqualTo(WorkflowConditionType.UNKNOWN);
        assertThat(WorkflowConditionType.of("condition")).isEqualTo(WorkflowConditionType.UNKNOWN);
    }
}
