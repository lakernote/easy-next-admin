package com.laker.admin.module.workflow.service;

import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.module.workflow.dto.WfTaskCenterSummary;
import com.laker.admin.module.workflow.support.WfCcQueryBuilder;
import com.laker.admin.module.workflow.support.WfHistoricProcessInstanceQueryBuilder;
import com.laker.admin.module.workflow.support.WfHistoricTaskQueryBuilder;
import com.laker.admin.module.workflow.support.WfProcessInstanceQueryBuilder;
import com.laker.admin.module.workflow.support.WfTaskQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WorkflowTaskCenterSummaryService {
    private static final List<String> DONE_TASK_STATUSES = List.of("APPROVED", "REJECTED", "TRANSFERRED", "DELEGATED", "CANCELED");

    private final IWfTaskService taskService;
    private final IWfHistoricTaskService historicTaskService;
    private final IWfProcessInstanceService instanceService;
    private final IWfHistoricProcessInstanceService historicInstanceService;
    private final IWfCcService ccService;
    private final IWfHistoricCcService historicCcService;

    public WorkflowTaskCenterSummaryService(IWfTaskService taskService,
                                            IWfHistoricTaskService historicTaskService,
                                            IWfProcessInstanceService instanceService,
                                            IWfHistoricProcessInstanceService historicInstanceService,
                                            IWfCcService ccService,
                                            IWfHistoricCcService historicCcService) {
        this.taskService = taskService;
        this.historicTaskService = historicTaskService;
        this.instanceService = instanceService;
        this.historicInstanceService = historicInstanceService;
        this.ccService = ccService;
        this.historicCcService = historicCcService;
    }

    public WfTaskCenterSummary summary() {
        AuthPrincipal principal = EasySecurityContext.getPrincipal();
        long pendingTotal = taskService.count(WfTaskQueryBuilder.build(principal, "PENDING", List.of(), null, null, true));
        long doneTotal = historicTaskService.count(WfHistoricTaskQueryBuilder.build(principal, null, DONE_TASK_STATUSES, null, null, true));
        long startedTotal = instanceService.count(WfProcessInstanceQueryBuilder.build(principal, true, false, null, null, null))
                + historicInstanceService.count(WfHistoricProcessInstanceQueryBuilder.build(principal, true, false, null, null, null));
        long ccTotal = ccService.count(WfCcQueryBuilder.build(principal, null, true))
                + historicCcService.count(WfCcQueryBuilder.buildHistoric(principal, null, true));
        return WfTaskCenterSummary.builder()
                .pendingTotal(pendingTotal)
                .doneTotal(doneTotal)
                .startedTotal(startedTotal)
                .ccTotal(ccTotal)
                .build();
    }
}
