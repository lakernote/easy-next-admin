package com.laker.admin.module.workflow.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WfProcessInstanceQueryBuilderTest {

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), WfProcessInstance.class);
    }

    @Test
    void shouldLimitMineQueryToCurrentInitiator() {
        AuthPrincipal principal = AuthPrincipal.builder()
                .userId(1001L)
                .superAdmin(false)
                .build();

        LambdaQueryWrapper<WfProcessInstance> wrapper = WfProcessInstanceQueryBuilder.build(
                principal,
                true,
                false,
                null,
                null,
                null
        );

        String sqlSegment = wrapper.getSqlSegment();
        assertThat(sqlSegment).contains("initiator_id");
        assertThat(sqlSegment).doesNotContain("wf_ru_task");
        assertThat(sqlSegment).doesNotContain("wf_ru_cc");
    }

    @Test
    void shouldIncludeRelatedTasksAndCcForDefaultNonAdminQuery() {
        AuthPrincipal principal = AuthPrincipal.builder()
                .userId(1001L)
                .superAdmin(false)
                .build();

        LambdaQueryWrapper<WfProcessInstance> wrapper = WfProcessInstanceQueryBuilder.build(
                principal,
                false,
                false,
                null,
                null,
                null
        );

        String sqlSegment = wrapper.getSqlSegment();
        assertThat(sqlSegment).contains("initiator_id");
        assertThat(sqlSegment).contains("wf_ru_task");
        assertThat(sqlSegment).contains("wf_hi_task");
        assertThat(sqlSegment).contains("wf_ru_cc");
        assertThat(sqlSegment).contains("wf_hi_cc");
    }

    @Test
    void shouldAllowInstanceManagerToQueryAllInstances() {
        AuthPrincipal principal = AuthPrincipal.builder()
                .userId(1001L)
                .superAdmin(false)
                .permissions(List.of("workflow:instance:manage"))
                .build();

        LambdaQueryWrapper<WfProcessInstance> wrapper = WfProcessInstanceQueryBuilder.build(
                principal,
                false,
                true,
                null,
                null,
                null
        );

        String sqlSegment = wrapper.getSqlSegment();
        assertThat(sqlSegment).doesNotContain("initiator_id");
        assertThat(sqlSegment).doesNotContain("wf_ru_task");
        assertThat(sqlSegment).doesNotContain("wf_hi_task");
        assertThat(sqlSegment).doesNotContain("wf_ru_cc");
        assertThat(sqlSegment).doesNotContain("wf_hi_cc");
    }
}
