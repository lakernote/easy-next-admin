package com.laker.admin.module.workflow.controller;

import com.laker.admin.common.model.PageResponse;
import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.audit.EasyAudit;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.workflow.dto.WfProcessDefinitionDetail;
import com.laker.admin.module.workflow.dto.WfProcessDefinitionRequest;
import com.laker.admin.module.workflow.dto.WfProcessDefinitionView;
import com.laker.admin.module.workflow.service.IWfProcessDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "流程定义")
@RestController
@RequestMapping("/api/workflow/definitions")
public class WfProcessDefinitionController {
    private final IWfProcessDefinitionService definitionService;

    public WfProcessDefinitionController(IWfProcessDefinitionService definitionService) {
        this.definitionService = definitionService;
    }

    @GetMapping
    @EasyPermission(EasyPermissions.Workflow.VIEW)
    @Operation(summary = "分页查询流程定义")
    public PageResponse<WfProcessDefinitionView> page(@RequestParam(required = false, defaultValue = "1") long page,
                                                      @RequestParam(required = false, defaultValue = "10") long limit,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) String status) {
        return definitionService.pageDefinitions(page, limit, keyword, status);
    }

    @GetMapping("/{id}")
    @EasyPermission(EasyPermissions.Workflow.VIEW)
    @Operation(summary = "查询流程定义详情")
    public Response<WfProcessDefinitionDetail> detail(@PathVariable Long id) {
        return Response.ok(definitionService.detail(id));
    }

    @PostMapping
    @EasyPermission(EasyPermissions.Workflow.DEFINITION_EDIT)
    @EasyAudit(module = "流程配置", action = "保存流程定义", dataChange = true, bizType = "WORKFLOW_DEFINITION", changeType = "SAVE")
    @Operation(summary = "保存流程定义")
    public Response<WfProcessDefinitionDetail> save(@Valid @RequestBody WfProcessDefinitionRequest request) {
        return Response.ok(definitionService.saveDefinition(request));
    }

    @PutMapping("/{id}/status")
    @EasyPermission(EasyPermissions.Workflow.DEFINITION_EDIT)
    @EasyAudit(module = "流程配置", action = "启停流程定义", dataChange = true, bizType = "WORKFLOW_DEFINITION", bizId = "#id", changeType = "STATUS")
    @Operation(summary = "启停流程定义")
    public Response<Boolean> updateStatus(@PathVariable Long id, @RequestParam String status) {
        return Response.ok(definitionService.updateStatus(id, status));
    }

    @PutMapping("/{id}/publish")
    @EasyPermission(EasyPermissions.Workflow.DEFINITION_EDIT)
    @EasyAudit(module = "流程配置", action = "发布流程定义", dataChange = true, bizType = "WORKFLOW_DEFINITION", bizId = "#id", changeType = "PUBLISH")
    @Operation(summary = "发布流程定义新版本")
    public Response<WfProcessDefinitionDetail> publish(@PathVariable Long id) {
        return Response.ok(definitionService.publish(id));
    }

    @DeleteMapping("/{id}")
    @EasyPermission(EasyPermissions.Workflow.DEFINITION_EDIT)
    @EasyAudit(module = "流程配置", action = "删除流程定义", dataChange = true, bizType = "WORKFLOW_DEFINITION", bizId = "#id", changeType = "DELETE")
    @Operation(summary = "删除流程定义")
    public Response<Boolean> delete(@PathVariable Long id) {
        return Response.ok(definitionService.deleteDefinition(id));
    }
}
