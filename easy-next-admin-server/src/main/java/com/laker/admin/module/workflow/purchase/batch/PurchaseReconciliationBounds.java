package com.laker.admin.module.workflow.purchase.batch;

import lombok.Data;

/** 本次对账候选集的 ID 高低水位。 */
@Data
public class PurchaseReconciliationBounds {
    private Long minId;
    private Long maxId;
}
