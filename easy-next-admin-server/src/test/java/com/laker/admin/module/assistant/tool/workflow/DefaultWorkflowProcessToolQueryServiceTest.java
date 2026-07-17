package com.laker.admin.module.assistant.tool.workflow;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.laker.admin.module.assistant.context.AssistantRequestContext.AssistantUserContext;
import com.laker.admin.module.workflow.entity.WfHistoricProcessInstance;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.service.IWfHistoricProcessInstanceService;
import com.laker.admin.module.workflow.service.IWfProcessInstanceService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.laker.admin.module.assistant.AssistantTestFixtures.superAdminRequestContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultWorkflowProcessToolQueryServiceTest {

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, WfProcessInstance.class);
        TableInfoHelper.initTableInfo(assistant, WfHistoricProcessInstance.class);
    }

    @Test
    void shouldKeepMyStartedProcessScopeForSuperAdmin() {
        IWfProcessInstanceService instanceService = mock(IWfProcessInstanceService.class);
        IWfHistoricProcessInstanceService historicService = mock(IWfHistoricProcessInstanceService.class);
        AtomicReference<String> runtimeSql = new AtomicReference<>();
        AtomicReference<String> historySql = new AtomicReference<>();
        when(instanceService.list(org.mockito.ArgumentMatchers.<Wrapper<WfProcessInstance>>any()))
                .thenAnswer(invocation -> {
            Wrapper<WfProcessInstance> wrapper = invocation.getArgument(0);
            runtimeSql.set(wrapper.getSqlSegment());
            return List.of();
                });
        when(historicService.list(org.mockito.ArgumentMatchers.<Wrapper<WfHistoricProcessInstance>>any()))
                .thenAnswer(invocation -> {
            Wrapper<WfHistoricProcessInstance> wrapper = invocation.getArgument(0);
            historySql.set(wrapper.getSqlSegment());
            return List.of();
                });
        DefaultWorkflowProcessToolQueryService queryService =
                new DefaultWorkflowProcessToolQueryService(instanceService, historicService);
        AssistantUserContext superAdmin = superAdminRequestContext("查询我的流程").user();

        queryService.myStartedProcesses(superAdmin, null, null, 5);

        assertThat(runtimeSql.get()).contains("initiator_id");
        assertThat(historySql.get()).contains("initiator_id");
    }
}
