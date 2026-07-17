package com.laker.admin.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.Version;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowEntityVersionTest {

    @Test
    void processInstanceAndTaskShouldUseOptimisticVersion() throws NoSuchFieldException {
        assertThat(WfProcessInstance.class.getDeclaredField("version").isAnnotationPresent(Version.class)).isTrue();
        assertThat(WfTask.class.getDeclaredField("version").isAnnotationPresent(Version.class)).isTrue();
    }

    @Test
    void workflowRuntimeAndHistoryEntitiesShouldUseSeparatedTables() {
        assertThat(tableName(WfProcessInstance.class)).isEqualTo("wf_ru_process_instance");
        assertThat(tableName(WfTask.class)).isEqualTo("wf_ru_task");
        assertThat(tableName(WfCc.class)).isEqualTo("wf_ru_cc");
        assertThat(tableName(WfEvent.class)).isEqualTo("wf_hi_event");
        assertThat(tableName(WfHistoricProcessInstance.class)).isEqualTo("wf_hi_process_instance");
        assertThat(tableName(WfHistoricTask.class)).isEqualTo("wf_hi_task");
        assertThat(tableName(WfHistoricCc.class)).isEqualTo("wf_hi_cc");
    }

    private String tableName(Class<?> entityType) {
        return entityType.getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class).value();
    }
}
