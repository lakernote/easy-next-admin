package com.laker.admin.module.workflow.repair.controller;

import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.audit.EasyAudit;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.annotation.EasyPermissionMode;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.workflow.repair.dto.RepairAttachmentResource;
import com.laker.admin.module.workflow.repair.dto.RepairAttachmentView;
import com.laker.admin.module.workflow.repair.dto.RepairApplyRequest;
import com.laker.admin.module.workflow.repair.dto.RepairRequestView;
import com.laker.admin.module.workflow.repair.service.IRepairRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
@Tag(name = "报修申请")
@RestController
@RequestMapping("/api/workflow/repair/requests")
public class RepairRequestController {
    private final IRepairRequestService repairRequestService;

    public RepairRequestController(IRepairRequestService repairRequestService) {
        this.repairRequestService = repairRequestService;
    }

    @PostMapping
    @EasyPermission(EasyPermissions.Workflow.INSTANCE_START)
    @EasyAudit(module = "报修申请", action = "提交报修申请", dataChange = true, bizType = "REPAIR_REQUEST", changeType = "CREATE")
    @Operation(summary = "提交报修申请")
    public Response<RepairRequestView> apply(@Valid @RequestBody RepairApplyRequest request) {
        return Response.ok(repairRequestService.apply(request));
    }

    @PostMapping("/attachments")
    @EasyPermission(EasyPermissions.Workflow.INSTANCE_START)
    @EasyAudit(module = "报修申请", action = "上传报修附件", dataChange = true, bizType = "REPAIR_ATTACHMENT", changeType = "UPLOAD")
    @Operation(summary = "上传报修故障图片")
    public Response<RepairAttachmentView> uploadAttachment(@RequestParam("file") MultipartFile file) {
        return Response.ok(repairRequestService.uploadAttachment(file));
    }

    @GetMapping("/attachments/{fileId}")
    @EasyPermission(value = {EasyPermissions.Workflow.VIEW, EasyPermissions.Workflow.INSTANCE_START}, mode = EasyPermissionMode.ANY)
    @Operation(summary = "查看报修故障图片")
    public ResponseEntity<InputStreamResource> attachment(@PathVariable Long fileId) {
        RepairAttachmentResource resource = repairRequestService.readAttachment(fileId);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(resource.contentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(resource.fileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString());
        if (resource.fileSize() != null && resource.fileSize() >= 0) {
            builder.contentLength(resource.fileSize());
        }
        return builder.body(new InputStreamResource(resource.inputStream()));
    }

    @GetMapping("/by-workflow-instance/{workflowInstanceId}")
    @EasyPermission(EasyPermissions.Workflow.VIEW)
    @Operation(summary = "按流程实例查询报修申请详情")
    public Response<RepairRequestView> detailByWorkflowInstance(@PathVariable Long workflowInstanceId) {
        return Response.ok(repairRequestService.detailByWorkflowInstance(workflowInstanceId));
    }

    @GetMapping("/{id}")
    @EasyPermission(EasyPermissions.Workflow.VIEW)
    @Operation(summary = "查询报修申请详情")
    public Response<RepairRequestView> detail(@PathVariable Long id) {
        return Response.ok(repairRequestService.detail(id));
    }
}
