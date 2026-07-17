package com.laker.admin.module.workflow.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowApproveTypeTest {

    @Test
    void shouldResolveKnownApproveTypes() {
        assertThat(WorkflowApproveType.of(null)).isEqualTo(WorkflowApproveType.ANY_ONE);
        assertThat(WorkflowApproveType.of("")).isEqualTo(WorkflowApproveType.ANY_ONE);
        assertThat(WorkflowApproveType.of("all")).isEqualTo(WorkflowApproveType.ALL);
        assertThat(WorkflowApproveType.of("SEQUENTIAL")).isEqualTo(WorkflowApproveType.SEQUENTIAL);
    }

    @Test
    void shouldRejectUnknownApproveType() {
        assertThatThrownBy(() -> WorkflowApproveType.of("ROUND_ROBIN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的审批方式");
    }
}
