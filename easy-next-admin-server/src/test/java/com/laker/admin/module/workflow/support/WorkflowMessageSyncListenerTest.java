package com.laker.admin.module.workflow.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.laker.admin.module.message.event.UserMessageReadEvent;
import com.laker.admin.module.message.event.UserMessagesReadAllEvent;
import com.laker.admin.module.workflow.entity.WfCc;
import com.laker.admin.module.workflow.entity.WfHistoricCc;
import com.laker.admin.module.workflow.service.IWfCcService;
import com.laker.admin.module.workflow.service.IWfHistoricCcService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class WorkflowMessageSyncListenerTest {

    private final IWfCcService ccService = mock(IWfCcService.class);
    private final IWfHistoricCcService historicCcService = mock(IWfHistoricCcService.class);
    private final WorkflowMessageSyncListener listener = new WorkflowMessageSyncListener(ccService, historicCcService);

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, WfCc.class);
        TableInfoHelper.initTableInfo(assistant, WfHistoricCc.class);
    }

    @Test
    void shouldSyncWorkflowCcReadMessageToCcTables() {
        listener.onMessageRead(new UserMessageReadEvent(
                7L,
                "WORKFLOW_CC",
                "WORKFLOW_CC",
                "99",
                LocalDateTime.now()
        ));

        verify(ccService).update(any());
        verify(historicCcService).update(any());
    }

    @Test
    void shouldIgnoreNonWorkflowCcMessage() {
        listener.onMessageRead(new UserMessageReadEvent(
                7L,
                "WORKFLOW",
                "WORKFLOW_TASK",
                "99",
                LocalDateTime.now()
        ));

        verify(ccService, never()).update(any());
        verify(historicCcService, never()).update(any());
    }

    @Test
    void shouldSyncAllReadToAllCcRowsForReceiver() {
        listener.onAllMessagesRead(new UserMessagesReadAllEvent(7L, LocalDateTime.now()));

        verify(ccService).update(any());
        verify(historicCcService).update(any());
    }
}
