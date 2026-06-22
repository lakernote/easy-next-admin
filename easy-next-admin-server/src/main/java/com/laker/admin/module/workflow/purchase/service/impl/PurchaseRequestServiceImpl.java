package com.laker.admin.module.workflow.purchase.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.module.system.entity.SysUser;
import com.laker.admin.module.system.service.ISysUserService;
import com.laker.admin.module.workflow.dto.WfProcessInstanceDetail;
import com.laker.admin.module.workflow.dto.WfStartProcessRequest;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.event.WfProcessInstanceStatusChangedEvent;
import com.laker.admin.module.workflow.purchase.dto.PurchaseApplyRequest;
import com.laker.admin.module.workflow.purchase.dto.PurchaseRequestView;
import com.laker.admin.module.workflow.purchase.entity.BizPurchaseRequest;
import com.laker.admin.module.workflow.purchase.mapper.BizPurchaseRequestMapper;
import com.laker.admin.module.workflow.purchase.service.IPurchaseRequestService;
import com.laker.admin.module.workflow.sequence.service.BusinessRequestNoService;
import com.laker.admin.module.workflow.service.IWfProcessInstanceService;
import com.laker.admin.module.workflow.service.IWfWorkflowRuntimeService;
import com.laker.admin.module.workflow.support.WorkflowInstanceStatus;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PurchaseRequestServiceImpl extends ServiceImpl<BizPurchaseRequestMapper, BizPurchaseRequest>
        implements IPurchaseRequestService {
    private static final String PROCESS_KEY = "purchase_approval";
    private static final String BUSINESS_TYPE = "purchase";
    private static final String REQUEST_NO_PREFIX = "PR";

    private final IWfWorkflowRuntimeService workflowRuntimeService;
    private final IWfProcessInstanceService processInstanceService;
    private final ISysUserService userService;
    private final BusinessRequestNoService requestNoService;

    public PurchaseRequestServiceImpl(IWfWorkflowRuntimeService workflowRuntimeService,
                                      IWfProcessInstanceService processInstanceService,
                                      ISysUserService userService,
                                      BusinessRequestNoService requestNoService) {
        this.workflowRuntimeService = workflowRuntimeService;
        this.processInstanceService = processInstanceService;
        this.userService = userService;
        this.requestNoService = requestNoService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseRequestView apply(PurchaseApplyRequest request) {
        AuthPrincipal principal = currentPrincipal();
        LocalDateTime now = LocalDateTime.now();

        BizPurchaseRequest purchaseRequest = new BizPurchaseRequest();
        purchaseRequest.setApplicantId(principal.getUserId());
        purchaseRequest.setApplicantDeptId(principal.getDeptId());
        purchaseRequest.setRequestNo(requestNoService.nextRequestNo(REQUEST_NO_PREFIX));
        purchaseRequest.setItemName(request.getItemName());
        purchaseRequest.setCategory(request.getCategory());
        purchaseRequest.setQuantity(request.getQuantity());
        purchaseRequest.setEstimatedAmount(request.getEstimatedAmount());
        purchaseRequest.setRequiredDate(request.getRequiredDate());
        purchaseRequest.setReason(request.getReason());
        purchaseRequest.setStatus("DRAFT");
        purchaseRequest.setCreatedBy(principal.getUserId());
        purchaseRequest.setCreatedAt(now);
        purchaseRequest.setUpdatedBy(principal.getUserId());
        purchaseRequest.setUpdatedAt(now);
        save(purchaseRequest);

        WfStartProcessRequest startProcessRequest = new WfStartProcessRequest();
        startProcessRequest.setProcessKey(PROCESS_KEY);
        startProcessRequest.setBusinessType(BUSINESS_TYPE);
        startProcessRequest.setBusinessId(purchaseRequest.getRequestNo());
        String displayName = StringUtils.hasText(principal.getNickName()) ? principal.getNickName() : principal.getUserName();
        startProcessRequest.setTitle(displayName + "采购申请");
        startProcessRequest.setComment(request.getReason());
        startProcessRequest.setVariables(startVariables(principal, request));
        WfProcessInstanceDetail workflowDetail = workflowRuntimeService.start(startProcessRequest);

        purchaseRequest.setWorkflowInstanceId(workflowDetail.getInstance().getId());
        purchaseRequest.setStatus(statusFromWorkflow(workflowDetail.getInstance().getStatus()));
        purchaseRequest.setUpdatedAt(LocalDateTime.now());
        updateById(purchaseRequest);
        return toView(purchaseRequest);
    }

    @Override
    public PurchaseRequestView detail(Long id) {
        BizPurchaseRequest purchaseRequest = getById(id);
        if (purchaseRequest == null) {
            throw new BusinessException("采购申请不存在");
        }
        AuthPrincipal principal = currentPrincipal();
        if (!principal.isSuperAdmin() && !principal.getUserId().equals(purchaseRequest.getApplicantId())) {
            workflowRuntimeService.detail(purchaseRequest.getWorkflowInstanceId());
        }
        return toView(purchaseRequest);
    }

    @EventListener
    public void syncStatusWhenWorkflowFinished(WfProcessInstanceStatusChangedEvent event) {
        if (!BUSINESS_TYPE.equals(event.businessType()) || !StringUtils.hasText(event.businessId())) {
            return;
        }
        String purchaseStatus = statusFromWorkflow(event.status());
        lambdaUpdate()
                .eq(BizPurchaseRequest::getRequestNo, event.businessId())
                .set(BizPurchaseRequest::getStatus, purchaseStatus)
                .set(BizPurchaseRequest::getWorkflowInstanceId, event.instanceId())
                .set(BizPurchaseRequest::getUpdatedAt, LocalDateTime.now())
                .update();
    }

    private Map<String, Object> startVariables(AuthPrincipal principal, PurchaseApplyRequest request) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("itemName", request.getItemName());
        variables.put("category", request.getCategory());
        variables.put("quantity", request.getQuantity());
        variables.put("amount", request.getEstimatedAmount());
        variables.put("estimatedAmount", request.getEstimatedAmount());
        variables.put("requiredDate", request.getRequiredDate().toString());
        variables.put("reason", request.getReason());
        variables.put("applicantDeptId", principal.getDeptId() == null ? "" : principal.getDeptId());
        return variables;
    }

    private PurchaseRequestView toView(BizPurchaseRequest purchaseRequest) {
        PurchaseRequestView view = new PurchaseRequestView();
        view.setId(purchaseRequest.getId());
        view.setRequestNo(purchaseRequest.getRequestNo());
        view.setApplicantId(purchaseRequest.getApplicantId());
        view.setItemName(purchaseRequest.getItemName());
        view.setCategory(purchaseRequest.getCategory());
        view.setQuantity(purchaseRequest.getQuantity());
        view.setEstimatedAmount(purchaseRequest.getEstimatedAmount());
        view.setRequiredDate(purchaseRequest.getRequiredDate());
        view.setReason(purchaseRequest.getReason());
        view.setWorkflowInstanceId(purchaseRequest.getWorkflowInstanceId());
        view.setCreatedAt(purchaseRequest.getCreatedAt());
        fillApplicant(view, purchaseRequest);
        fillWorkflowStatus(view, purchaseRequest);
        return view;
    }

    private void fillApplicant(PurchaseRequestView view, BizPurchaseRequest purchaseRequest) {
        SysUser user = userService.getById(purchaseRequest.getApplicantId());
        if (user != null) {
            view.setApplicantName(StringUtils.hasText(user.getNickName()) ? user.getNickName() : user.getUserName());
        }
    }

    private void fillWorkflowStatus(PurchaseRequestView view, BizPurchaseRequest purchaseRequest) {
        String workflowStatus = null;
        if (purchaseRequest.getWorkflowInstanceId() != null) {
            WfProcessInstance instance = processInstanceService.getById(purchaseRequest.getWorkflowInstanceId());
            if (instance != null) {
                workflowStatus = instance.getStatus();
            }
        }
        view.setWorkflowStatus(workflowStatus);
        view.setStatus(StringUtils.hasText(workflowStatus) ? statusFromWorkflow(workflowStatus) : purchaseRequest.getStatus());
    }

    private String statusFromWorkflow(String workflowStatus) {
        if (WorkflowInstanceStatus.RUNNING.equalsCode(workflowStatus)) {
            return "APPROVING";
        }
        if (WorkflowInstanceStatus.APPROVED.equalsCode(workflowStatus)) {
            return "APPROVED";
        }
        if (WorkflowInstanceStatus.REJECTED.equalsCode(workflowStatus)) {
            return "REJECTED";
        }
        if (WorkflowInstanceStatus.REVOKED.equalsCode(workflowStatus)) {
            return "REVOKED";
        }
        if (WorkflowInstanceStatus.TERMINATED.equalsCode(workflowStatus)) {
            return "TERMINATED";
        }
        return workflowStatus;
    }

    private AuthPrincipal currentPrincipal() {
        AuthPrincipal principal = EasySecurityContext.getPrincipal();
        if (principal == null || principal.getUserId() == null) {
            throw new BusinessException("未获取到当前登录用户");
        }
        return principal;
    }
}
