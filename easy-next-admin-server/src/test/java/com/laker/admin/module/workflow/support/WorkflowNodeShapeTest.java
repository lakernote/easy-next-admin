package com.laker.admin.module.workflow.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowNodeShapeTest {

    @Test
    void shouldResolveCurrentLogicFlowShapeNamesOnly() {
        assertThat(WorkflowNodeShape.of("circle")).isEqualTo(WorkflowNodeShape.CIRCLE);
        assertThat(WorkflowNodeShape.of("rect")).isEqualTo(WorkflowNodeShape.RECT);
        assertThat(WorkflowNodeShape.of("diamond")).isEqualTo(WorkflowNodeShape.DIAMOND);
        assertThat(WorkflowNodeShape.of("rectangle")).isEqualTo(WorkflowNodeShape.UNKNOWN);
    }
}
