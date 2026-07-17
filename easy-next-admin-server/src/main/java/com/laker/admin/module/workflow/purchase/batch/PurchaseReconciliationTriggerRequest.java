package com.laker.admin.module.workflow.purchase.batch;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PurchaseReconciliationTriggerRequest {
    @NotNull(message = "对账日期不能为空")
    private LocalDate businessDate;
}
