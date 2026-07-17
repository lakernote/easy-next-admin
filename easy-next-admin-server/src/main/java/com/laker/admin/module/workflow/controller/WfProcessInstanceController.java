package com.laker.admin.module.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.model.PageResponse;
import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.audit.EasyAudit;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.annotation.EasyPermissionMode;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.exception.EasyForbiddenException;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.workflow.dto.WfInstanceActionRequest;
import com.laker.admin.module.workflow.dto.WfProcessInstanceDetail;
import com.laker.admin.module.workflow.dto.WfProcessInstanceView;
import com.laker.admin.module.workflow.dto.WfStartProcessRequest;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.service.IWfHistoricProcessInstanceService;
import com.laker.admin.module.workflow.service.IWfProcessInstanceService;
import com.laker.admin.module.workflow.service.IWfWorkflowRuntimeService;
import com.laker.admin.module.workflow.support.WfHistoricProcessInstanceQueryBuilder;
import com.laker.admin.module.workflow.support.WfProcessInstanceQueryBuilder;
import com.laker.admin.module.workflow.support.WorkflowArchiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "流程实例")
@RestController
@RequestMapping("/api/workflow/instances")
public class WfProcessInstanceController {
    private final IWfProcessInstanceService instanceService;
    private final IWfHistoricProcessInstanceService historicInstanceService;
    private final IWfWorkflowRuntimeService runtimeService;
    private final WorkflowArchiveService archiveService;

    public WfProcessInstanceController(IWfProcessInstanceService instanceService,
                                       IWfHistoricProcessInstanceService historicInstanceService,
                                       IWfWorkflowRuntimeService runtimeService,
                                       WorkflowArchiveService archiveService) {
        this.instanceService = instanceService;
        this.historicInstanceService = historicInstanceService;
        this.runtimeService = runtimeService;
        this.archiveService = archiveService;
    }

    @GetMapping
    @EasyPermission(value = {EasyPermissions.Workflow.VIEW, EasyPermissions.Workflow.INSTANCE_MANAGE}, mode = EasyPermissionMode.ANY)
    @Operation(summary = "分页查询流程实例")
    public PageResponse<WfProcessInstanceView> page(@RequestParam(required = false, defaultValue = "1") long page,
                                                    @RequestParam(required = false, defaultValue = "10") long limit,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String businessType,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false, defaultValue = "false") boolean mine,
                                                    @RequestParam(required = false, defaultValue = "false") boolean manage,
                                                    @RequestParam String scope) {
        AuthPrincipal principal = EasySecurityContext.getPrincipal();
        boolean manageAll = manage && principal != null && principal.hasPermission(EasyPermissions.Workflow.INSTANCE_MANAGE);
        if (manage && !manageAll) {
            throw new EasyForbiddenException("缺少流程实例管理权限");
        }
        String normalizedScope = normalizeScope(scope);
        if ("RUNTIME".equals(normalizedScope)) {
            var queryWrapper = WfProcessInstanceQueryBuilder.build(principal, mine, manageAll, status, businessType, keyword);
            Page<WfProcessInstance> pageResult = instanceService.page(new Page<>(page, limit), queryWrapper);
            return PageResponse.ok(WfProcessInstanceView.fromList(pageResult.getRecords()), pageResult.getTotal());
        }
        var historicQueryWrapper = WfHistoricProcessInstanceQueryBuilder.build(principal, mine, manageAll, status, businessType, keyword);
        var pageResult = historicInstanceService.page(new Page<>(page, limit), historicQueryWrapper);
        List<WfProcessInstance> records = pageResult.getRecords().stream()
                .map(archiveService::toRuntimeInstance)
                .toList();
        return PageResponse.ok(WfProcessInstanceView.fromList(records), pageResult.getTotal());
    }

    private String normalizeScope(String scope) {
        String normalized = StringUtils.hasText(scope) ? scope.trim().toUpperCase() : "";
        if (!List.of("RUNTIME", "HISTORY").contains(normalized)) {
            throw new BusinessException("不支持的流程实例范围");
        }
        return normalized;
    }

    @PostMapping
    @EasyPermission(EasyPermissions.Workflow.INSTANCE_START)
    @EasyAudit(module = "流程实例", action = "启动流程", dataChange = true, bizType = "WORKFLOW_INSTANCE", changeType = "START")
    @Operation(summary = "启动流程")
    public Response<WfProcessInstanceDetail> start(@Valid @RequestBody WfStartProcessRequest request) {
        return Response.ok(runtimeService.start(request));
    }

    @GetMapping("/{id}")
    @EasyPermission(value = {EasyPermissions.Workflow.VIEW, EasyPermissions.Workflow.INSTANCE_MANAGE}, mode = EasyPermissionMode.ANY)
    @Operation(summary = "查询流程实例详情")
    public Response<WfProcessInstanceDetail> detail(@PathVariable Long id) {
        return Response.ok(runtimeService.detail(id));
    }

    @PutMapping("/{id}/revoke")
    @EasyPermission(EasyPermissions.Workflow.INSTANCE_REVOKE)
    @EasyAudit(module = "流程实例", action = "撤回流程实例", dataChange = true, bizType = "WORKFLOW_INSTANCE", bizId = "#id", changeType = "REVOKE")
    @Operation(summary = "撤回流程实例")
    public Response<WfProcessInstanceDetail> revoke(@PathVariable Long id,
                                                    @Valid @RequestBody(required = false) WfInstanceActionRequest request) {
        return Response.ok(runtimeService.revoke(id, request == null ? new WfInstanceActionRequest() : request));
    }

    @PutMapping("/{id}/terminate")
    @EasyPermission(EasyPermissions.Workflow.INSTANCE_TERMINATE)
    @EasyAudit(module = "流程实例", action = "终止流程实例", dataChange = true, bizType = "WORKFLOW_INSTANCE", bizId = "#id", changeType = "TERMINATE")
    @Operation(summary = "终止流程实例")
    public Response<WfProcessInstanceDetail> terminate(@PathVariable Long id,
                                                       @Valid @RequestBody(required = false) WfInstanceActionRequest request) {
        return Response.ok(runtimeService.terminate(id, request == null ? new WfInstanceActionRequest() : request));
    }
}
