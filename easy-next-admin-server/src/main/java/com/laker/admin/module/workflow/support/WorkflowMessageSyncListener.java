package com.laker.admin.module.workflow.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.laker.admin.module.message.event.UserMessageReadEvent;
import com.laker.admin.module.message.event.UserMessagesReadAllEvent;
import com.laker.admin.module.workflow.entity.WfCc;
import com.laker.admin.module.workflow.entity.WfHistoricCc;
import com.laker.admin.module.workflow.service.IWfCcService;
import com.laker.admin.module.workflow.service.IWfHistoricCcService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class WorkflowMessageSyncListener {
    private static final String WORKFLOW_CC_BIZ_TYPE = "WORKFLOW_CC";

    private final IWfCcService ccService;
    private final IWfHistoricCcService historicCcService;

    public WorkflowMessageSyncListener(IWfCcService ccService, IWfHistoricCcService historicCcService) {
        this.ccService = ccService;
        this.historicCcService = historicCcService;
    }

    @EventListener
    public void onMessageRead(UserMessageReadEvent event) {
        if (!WORKFLOW_CC_BIZ_TYPE.equals(event.bizType())) {
            return;
        }
        Long ccId = parseId(event.bizId());
        if (ccId == null || event.receiverId() == null) {
            return;
        }
        LocalDateTime readAt = event.readAt() == null ? LocalDateTime.now() : event.readAt();
        markRuntimeCcRead(ccId, event.receiverId(), readAt);
        markHistoricCcRead(ccId, event.receiverId(), readAt);
    }

    @EventListener
    public void onAllMessagesRead(UserMessagesReadAllEvent event) {
        if (event.receiverId() == null) {
            return;
        }
        LocalDateTime readAt = event.readAt() == null ? LocalDateTime.now() : event.readAt();
        ccService.update(Wrappers.<WfCc>lambdaUpdate()
                .eq(WfCc::getReceiverId, event.receiverId())
                .eq(WfCc::getReadStatus, 0)
                .set(WfCc::getReadStatus, 1)
                .set(WfCc::getReadAt, readAt));
        historicCcService.update(Wrappers.<WfHistoricCc>lambdaUpdate()
                .eq(WfHistoricCc::getReceiverId, event.receiverId())
                .eq(WfHistoricCc::getReadStatus, 0)
                .set(WfHistoricCc::getReadStatus, 1)
                .set(WfHistoricCc::getReadAt, readAt));
    }

    private void markRuntimeCcRead(Long ccId, Long receiverId, LocalDateTime readAt) {
        ccService.update(Wrappers.<WfCc>lambdaUpdate()
                .eq(WfCc::getId, ccId)
                .eq(WfCc::getReceiverId, receiverId)
                .eq(WfCc::getReadStatus, 0)
                .set(WfCc::getReadStatus, 1)
                .set(WfCc::getReadAt, readAt));
    }

    private void markHistoricCcRead(Long ccId, Long receiverId, LocalDateTime readAt) {
        historicCcService.update(Wrappers.<WfHistoricCc>lambdaUpdate()
                .eq(WfHistoricCc::getId, ccId)
                .eq(WfHistoricCc::getReceiverId, receiverId)
                .eq(WfHistoricCc::getReadStatus, 0)
                .set(WfHistoricCc::getReadStatus, 1)
                .set(WfHistoricCc::getReadAt, readAt));
    }

    private Long parseId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
