package com.laker.admin.module.workflow.purchase.batch;

import com.laker.admin.module.batch.enums.BatchTriggerType;
import com.laker.admin.module.schedule.core.EasyJob;
import com.laker.admin.module.schedule.core.EasyJobHandler;
import com.laker.admin.module.schedule.enums.JobExecutionMode;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.Map;

@EasyJob(
        jobCode = PurchaseStatusReconciliationService.JOB_CODE,
        jobName = "采购流程状态日终对账",
        cron = "0 10 1 * * ?",
        executionMode = JobExecutionMode.BROADCAST
)
@RequiredArgsConstructor
public class PurchaseStatusReconciliationJob implements EasyJobHandler {
    private final PurchaseStatusReconciliationService reconciliationService;

    @Override
    public void execute(Map<String, Object> params) throws Exception {
        reconciliationService.run(LocalDate.now().minusDays(1), 1,
                BatchTriggerType.JOB.code(), PurchaseStatusReconciliationService.JOB_CODE);
    }
}
