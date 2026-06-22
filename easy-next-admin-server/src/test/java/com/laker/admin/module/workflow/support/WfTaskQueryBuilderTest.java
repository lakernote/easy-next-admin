package com.laker.admin.module.workflow.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.module.workflow.entity.WfTask;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WfTaskQueryBuilderTest {

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), WfTask.class);
    }

    @Test
    void shouldFilterDoneTasksWithMultipleStatuses() {
        AuthPrincipal principal = AuthPrincipal.builder()
                .userId(1001L)
                .superAdmin(false)
                .build();

        LambdaQueryWrapper<WfTask> wrapper = WfTaskQueryBuilder.build(
                principal,
                null,
                List.of("APPROVED", "REJECTED", "TRANSFERRED", "DELEGATED", "CANCELED"),
                null,
                null,
                true
        );

        String sqlSegment = wrapper.getSqlSegment();
        assertThat(sqlSegment).contains("status IN");
        assertThat(sqlSegment).contains("assignee_id");
        assertThat(sqlSegment).doesNotContain("status =");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains("APPROVED", "REJECTED", "TRANSFERRED", "DELEGATED", "CANCELED");
    }
}
