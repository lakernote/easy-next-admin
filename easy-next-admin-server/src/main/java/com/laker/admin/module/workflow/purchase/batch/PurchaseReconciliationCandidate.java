package com.laker.admin.module.workflow.purchase.batch;

import lombok.Data;

/** 采购单与流程实例的状态对账候选。 */
@Data
public class PurchaseReconciliationCandidate {
    private Long purchaseId;
    private String purchaseStatus;
    private String workflowStatus;
}
