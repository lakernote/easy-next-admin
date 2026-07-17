package com.laker.admin.module.workflow.purchase.controller;

import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.audit.EasyAudit;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.workflow.purchase.dto.PurchaseApplyRequest;
import com.laker.admin.module.workflow.purchase.dto.PurchaseRequestView;
import com.laker.admin.module.workflow.purchase.service.IPurchaseRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "采购申请")
@RestController
@RequestMapping("/api/workflow/purchase/requests")
public class PurchaseRequestController {
    private final IPurchaseRequestService purchaseRequestService;

    public PurchaseRequestController(IPurchaseRequestService purchaseRequestService) {
        this.purchaseRequestService = purchaseRequestService;
    }

    @PostMapping
    @EasyPermission(EasyPermissions.Workflow.INSTANCE_START)
    @EasyAudit(module = "采购申请", action = "提交采购申请", dataChange = true, bizType = "PURCHASE_REQUEST", changeType = "CREATE")
    @Operation(summary = "提交采购申请")
    public Response<PurchaseRequestView> apply(@Valid @RequestBody PurchaseApplyRequest request) {
        return Response.ok(purchaseRequestService.apply(request));
    }

    @GetMapping("/{id}")
    @EasyPermission(EasyPermissions.Workflow.VIEW)
    @Operation(summary = "查询采购申请详情")
    public Response<PurchaseRequestView> detail(@PathVariable Long id) {
        return Response.ok(purchaseRequestService.detail(id));
    }
}
