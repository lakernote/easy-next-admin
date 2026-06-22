package com.laker.admin.module.workflow.purchase.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.laker.admin.module.workflow.purchase.dto.PurchaseApplyRequest;
import com.laker.admin.module.workflow.purchase.dto.PurchaseRequestView;
import com.laker.admin.module.workflow.purchase.entity.BizPurchaseRequest;

public interface IPurchaseRequestService extends IService<BizPurchaseRequest> {
    PurchaseRequestView apply(PurchaseApplyRequest request);

    PurchaseRequestView detail(Long id);
}
