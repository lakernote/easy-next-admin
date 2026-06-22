package com.laker.admin.module.workflow.controller;

import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.workflow.dto.WfTaskCenterSummary;
import com.laker.admin.module.workflow.service.WorkflowTaskCenterSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "我的流程")
@RestController
@RequestMapping("/api/workflow/task-center")
public class WfTaskCenterController {
    private final WorkflowTaskCenterSummaryService summaryService;

    public WfTaskCenterController(WorkflowTaskCenterSummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping("/summary")
    @EasyPermission(EasyPermissions.Workflow.VIEW)
    @Operation(summary = "查询我的流程轻量计数")
    public Response<WfTaskCenterSummary> summary() {
        return Response.ok(summaryService.summary());
    }
}
