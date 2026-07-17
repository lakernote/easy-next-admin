package com.laker.admin.module.workflow.purchase.controller;

import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.audit.EasyAudit;
import com.laker.admin.infrastructure.observability.apilog.EasyApiAccessLog;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.workflow.purchase.batch.PurchaseReconciliationTriggerRequest;
import com.laker.admin.module.workflow.purchase.batch.PurchaseStatusReconciliationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflow/purchase/reconciliation")
@EasyApiAccessLog
@RequiredArgsConstructor
public class PurchaseReconciliationController {
    private final PurchaseStatusReconciliationService reconciliationService;

    @PostMapping
    @EasyPermission(EasyPermissions.Batch.TASK_MANAGE)
    @EasyAudit(module = "批处理任务", action = "触发采购状态对账", dataChange = true,
            bizType = "PURCHASE_RECONCILIATION", changeType = "TRIGGER")
    public Response<Integer> trigger(@Valid @RequestBody PurchaseReconciliationTriggerRequest request) {
        return Response.ok(reconciliationService.triggerManual(request.getBusinessDate()));
    }
}
