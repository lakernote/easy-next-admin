package com.laker.admin.module.workflow.leave.service.impl;

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
import com.laker.admin.module.workflow.leave.dto.LeaveApplyRequest;
import com.laker.admin.module.workflow.leave.dto.LeaveRequestView;
import com.laker.admin.module.workflow.leave.entity.BizLeaveRequest;
import com.laker.admin.module.workflow.leave.mapper.BizLeaveRequestMapper;
import com.laker.admin.module.workflow.leave.service.ILeaveRequestService;
import com.laker.admin.module.business.number.service.BusinessNumberService;
import com.laker.admin.module.workflow.service.IWfProcessInstanceService;
import com.laker.admin.module.workflow.service.IWfWorkflowRuntimeService;
import com.laker.admin.module.workflow.support.WorkflowInstanceStatus;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class LeaveRequestServiceImpl extends ServiceImpl<BizLeaveRequestMapper, BizLeaveRequest>
        implements ILeaveRequestService {
    private static final String PROCESS_KEY = "leave_approval";
    private static final String BUSINESS_TYPE = "leave";
    private static final String NUMBER_RULE_CODE = "LEAVE_REQUEST";

    private final IWfWorkflowRuntimeService workflowRuntimeService;
    private final IWfProcessInstanceService processInstanceService;
    private final ISysUserService userService;
    private final BusinessNumberService businessNumberService;

    public LeaveRequestServiceImpl(IWfWorkflowRuntimeService workflowRuntimeService,
                                   IWfProcessInstanceService processInstanceService,
                                   ISysUserService userService,
                                   BusinessNumberService businessNumberService) {
        this.workflowRuntimeService = workflowRuntimeService;
        this.processInstanceService = processInstanceService;
        this.userService = userService;
        this.businessNumberService = businessNumberService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeaveRequestView apply(LeaveApplyRequest request) {
        AuthPrincipal principal = currentPrincipal();
        validateLeavePeriod(request);
        LocalDateTime now = LocalDateTime.now();

        BizLeaveRequest leaveRequest = new BizLeaveRequest();
        leaveRequest.setApplicantId(principal.getUserId());
        leaveRequest.setRequestNo(businessNumberService.nextNumber(NUMBER_RULE_CODE));
        leaveRequest.setApplicantDeptId(principal.getDeptId());
        leaveRequest.setLeaveType(request.getLeaveType());
        leaveRequest.setStartTime(request.getStartTime());
        leaveRequest.setEndTime(request.getEndTime());
        leaveRequest.setDays(request.getDays());
        leaveRequest.setReason(request.getReason());
        leaveRequest.setStatus("DRAFT");
        leaveRequest.setCreatedBy(principal.getUserId());
        leaveRequest.setCreatedAt(now);
        leaveRequest.setUpdatedBy(principal.getUserId());
        leaveRequest.setUpdatedAt(now);
        save(leaveRequest);

        WfStartProcessRequest startProcessRequest = new WfStartProcessRequest();
        startProcessRequest.setProcessKey(PROCESS_KEY);
        startProcessRequest.setBusinessType(BUSINESS_TYPE);
        startProcessRequest.setBusinessId(leaveRequest.getRequestNo());
        String displayName = StringUtils.hasText(principal.getNickName()) ? principal.getNickName() : principal.getUserName();
        startProcessRequest.setTitle(displayName + "请假申请");
        startProcessRequest.setComment(request.getReason());
        startProcessRequest.setVariables(Map.of(
                "leaveType", request.getLeaveType(),
                "startTime", request.getStartTime().toString(),
                "endTime", request.getEndTime().toString(),
                "days", request.getDays(),
                "duration", request.getDays(),
                "reason", request.getReason(),
                "applicantDeptId", principal.getDeptId() == null ? "" : principal.getDeptId()
        ));
        WfProcessInstanceDetail workflowDetail = workflowRuntimeService.start(startProcessRequest);
        leaveRequest.setWorkflowInstanceId(workflowDetail.getInstance().getId());
        leaveRequest.setStatus(statusFromWorkflow(workflowDetail.getInstance().getStatus()));
        leaveRequest.setUpdatedAt(LocalDateTime.now());
        updateById(leaveRequest);
        return toView(leaveRequest);
    }

    @Override
    public LeaveRequestView detail(Long id) {
        BizLeaveRequest leaveRequest = getById(id);
        if (leaveRequest == null) {
            throw new BusinessException("请假申请不存在");
        }
        AuthPrincipal principal = currentPrincipal();
        if (!principal.isSuperAdmin() && !principal.getUserId().equals(leaveRequest.getApplicantId())) {
            workflowRuntimeService.detail(leaveRequest.getWorkflowInstanceId());
        }
        return toView(leaveRequest);
    }

    @EventListener
    public void syncStatusWhenWorkflowFinished(WfProcessInstanceStatusChangedEvent event) {
        if (!BUSINESS_TYPE.equals(event.businessType()) || !StringUtils.hasText(event.businessId())) {
            return;
        }
        String leaveStatus = statusFromWorkflow(event.status());
        lambdaUpdate()
                .eq(BizLeaveRequest::getRequestNo, event.businessId())
                .set(BizLeaveRequest::getStatus, leaveStatus)
                .set(BizLeaveRequest::getWorkflowInstanceId, event.instanceId())
                .set(BizLeaveRequest::getUpdatedAt, LocalDateTime.now())
                .update();
    }

    private void validateLeavePeriod(LeaveApplyRequest request) {
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new BusinessException("结束时间必须晚于开始时间");
        }
    }

    private LeaveRequestView toView(BizLeaveRequest leaveRequest) {
        LeaveRequestView view = new LeaveRequestView();
        view.setId(leaveRequest.getId());
        view.setRequestNo(leaveRequest.getRequestNo());
        view.setApplicantId(leaveRequest.getApplicantId());
        view.setLeaveType(leaveRequest.getLeaveType());
        view.setStartTime(leaveRequest.getStartTime());
        view.setEndTime(leaveRequest.getEndTime());
        view.setDays(leaveRequest.getDays());
        view.setReason(leaveRequest.getReason());
        view.setWorkflowInstanceId(leaveRequest.getWorkflowInstanceId());
        view.setCreatedAt(leaveRequest.getCreatedAt());
        fillApplicant(view, leaveRequest);
        fillWorkflowStatus(view, leaveRequest);
        return view;
    }

    private void fillApplicant(LeaveRequestView view, BizLeaveRequest leaveRequest) {
        SysUser user = userService.getById(leaveRequest.getApplicantId());
        if (user != null) {
            view.setApplicantName(StringUtils.hasText(user.getNickName()) ? user.getNickName() : user.getUserName());
        }
    }

    private void fillWorkflowStatus(LeaveRequestView view, BizLeaveRequest leaveRequest) {
        String workflowStatus = null;
        if (leaveRequest.getWorkflowInstanceId() != null) {
            WfProcessInstance instance = processInstanceService.getById(leaveRequest.getWorkflowInstanceId());
            if (instance != null) {
                workflowStatus = instance.getStatus();
            }
        }
        view.setWorkflowStatus(workflowStatus);
        view.setStatus(StringUtils.hasText(workflowStatus) ? statusFromWorkflow(workflowStatus) : leaveRequest.getStatus());
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
