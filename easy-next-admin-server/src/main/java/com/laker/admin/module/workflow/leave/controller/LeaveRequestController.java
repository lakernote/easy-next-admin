package com.laker.admin.module.workflow.leave.controller;

import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.audit.EasyAudit;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.workflow.leave.dto.LeaveApplyRequest;
import com.laker.admin.module.workflow.leave.dto.LeaveRequestView;
import com.laker.admin.module.workflow.leave.service.ILeaveRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "请假申请")
@RestController
@RequestMapping("/api/workflow/leave/requests")
public class LeaveRequestController {
    private final ILeaveRequestService leaveRequestService;

    public LeaveRequestController(ILeaveRequestService leaveRequestService) {
        this.leaveRequestService = leaveRequestService;
    }

    @PostMapping
    @EasyPermission(EasyPermissions.Workflow.INSTANCE_START)
    @EasyAudit(module = "请假申请", action = "提交请假申请", dataChange = true, bizType = "LEAVE_REQUEST", changeType = "CREATE")
    @Operation(summary = "提交请假申请")
    public Response<LeaveRequestView> apply(@Valid @RequestBody LeaveApplyRequest request) {
        return Response.ok(leaveRequestService.apply(request));
    }

    @GetMapping("/{id}")
    @EasyPermission(EasyPermissions.Workflow.VIEW)
    @Operation(summary = "查询请假申请详情")
    public Response<LeaveRequestView> detail(@PathVariable Long id) {
        return Response.ok(leaveRequestService.detail(id));
    }
}
