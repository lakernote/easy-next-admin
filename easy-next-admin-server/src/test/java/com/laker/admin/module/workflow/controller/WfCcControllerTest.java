package com.laker.admin.module.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.model.PageResponse;
import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.message.service.UserMessageService;
import com.laker.admin.module.workflow.entity.WfCc;
import com.laker.admin.module.workflow.entity.WfHistoricCc;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.service.IWfCcService;
import com.laker.admin.module.workflow.service.IWfHistoricCcService;
import com.laker.admin.module.workflow.support.WorkflowArchiveService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WfCcControllerTest {

    private final IWfCcService ccService = mock(IWfCcService.class);
    private final IWfHistoricCcService historicCcService = mock(IWfHistoricCcService.class);
    private final WorkflowArchiveService archiveService = mock(WorkflowArchiveService.class);
    private final UserMessageService userMessageService = mock(UserMessageService.class);
    private final WfCcController controller = new WfCcController(
            ccService,
            historicCcService,
            archiveService,
            userMessageService);

    @AfterEach
    void tearDown() {
        EasySecurityContext.clear();
    }

    @Test
    void pageShouldReturnCcRowsWithInstanceBrief() {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(7L)
                .permissions(List.of(EasyPermissions.Workflow.VIEW))
                .build());
        WfCc cc = new WfCc();
        cc.setId(1L);
        cc.setInstanceId(11L);
        cc.setNodeKey("notify");
        cc.setNodeName("审计备案");
        cc.setReceiverId(7L);
        cc.setReadStatus(0);
        Page<WfCc> ccPage = new Page<>(1, 10);
        ccPage.setRecords(List.of(cc));
        ccPage.setTotal(1);
        when(ccService.count(any())).thenReturn(1L);
        when(ccService.page(any(), any())).thenReturn(ccPage);

        WfProcessInstance instance = new WfProcessInstance();
        instance.setId(11L);
        instance.setTitle("请假申请 L001");
        instance.setBusinessType("LEAVE");
        instance.setBusinessId("L001");
        instance.setStatus("RUNNING");
        when(archiveService.instanceMap(List.of(11L))).thenReturn(Map.of(11L, instance));
        when(historicCcService.count(any())).thenReturn(0L);
        when(historicCcService.page(any(), any())).thenReturn(new Page<>(1, 10));

        PageResponse<?> response = controller.page(1, 10, null, true);

        Object row = response.getData().list().get(0);
        assertThat(row)
                .hasFieldOrPropertyWithValue("instanceTitle", "请假申请 L001")
                .hasFieldOrPropertyWithValue("businessType", "LEAVE")
                .hasFieldOrPropertyWithValue("businessId", "L001")
                .hasFieldOrPropertyWithValue("instanceStatus", "RUNNING")
                .hasFieldOrPropertyWithValue("nodeName", "审计备案")
                .hasFieldOrPropertyWithValue("historic", false);
    }

    @Test
    void markReadShouldUseHistoricTableWhenSourceIsHistoric() {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(7L)
                .build());
        WfHistoricCc historicCc = new WfHistoricCc();
        historicCc.setId(9L);
        historicCc.setReceiverId(7L);
        historicCc.setReadStatus(0);
        when(historicCcService.getById(9L)).thenReturn(historicCc);
        when(historicCcService.updateById(historicCc)).thenReturn(true);

        Response<Boolean> response = controller.markRead(9L, true);

        assertThat(response.getData()).isTrue();
        assertThat(historicCc.getReadStatus()).isEqualTo(1);
        assertThat(historicCc.getReadAt()).isNotNull();
        verify(ccService, never()).getById(9L);
        verify(historicCcService).updateById(historicCc);
        verify(userMessageService).markWorkflowCcMessageRead(7L, 9L);
    }

    @Test
    void markReadShouldSyncRuntimeCcToMessage() {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(7L)
                .build());
        WfCc cc = new WfCc();
        cc.setId(10L);
        cc.setReceiverId(7L);
        cc.setReadStatus(0);
        when(ccService.getById(10L)).thenReturn(cc);
        when(ccService.updateById(cc)).thenReturn(true);

        Response<Boolean> response = controller.markRead(10L, false);

        assertThat(response.getData()).isTrue();
        assertThat(cc.getReadStatus()).isEqualTo(1);
        assertThat(cc.getReadAt()).isNotNull();
        verify(historicCcService, never()).getById(10L);
        verify(ccService).updateById(cc);
        verify(userMessageService).markWorkflowCcMessageRead(7L, 10L);
    }

    @Test
    void markReadShouldRejectOtherReceivers() {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(8L)
                .build());
        WfCc cc = new WfCc();
        cc.setId(10L);
        cc.setReceiverId(7L);
        when(ccService.getById(10L)).thenReturn(cc);

        assertThatThrownBy(() -> controller.markRead(10L, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能标记自己的流程抄送已读");
    }
}
