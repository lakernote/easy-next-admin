package com.laker.admin.module.workflow.leave.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LeaveTypeTest {

    @Test
    void shouldKeepStableCodesSeparateFromChineseLabels() {
        assertThat(LeaveType.fromCode("sick")).contains(LeaveType.SICK);
        assertThat(LeaveType.SICK.code()).isEqualTo("SICK");
        assertThat(LeaveType.SICK.label()).isEqualTo("病假");
        assertThat(LeaveType.displayLabel("SICK")).isEqualTo("病假");
        assertThat(LeaveType.agentLabels()).containsEntry("PERSONAL", "事假");
    }

    @Test
    void shouldRecognizeOnlyExplicitBusinessLabelsAndAliases() {
        assertThat(LeaveType.SICK.isExplicitlyMentionedIn("我明天下午请病假")).isTrue();
        assertThat(LeaveType.COMPENSATORY.isExplicitlyMentionedIn("我想申请补休")).isTrue();
        assertThat(LeaveType.PERSONAL.isExplicitlyMentionedIn("我想今天下午请假")).isFalse();
    }
}
