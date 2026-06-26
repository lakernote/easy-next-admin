package com.laker.admin.module.batch.controller;

import com.laker.admin.common.model.PageResponse;
import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.audit.EasyAudit;
import com.laker.admin.infrastructure.observability.apilog.EasyApiAccessLog;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.batch.dto.BatchTaskCancelRequest;
import com.laker.admin.module.batch.dto.BatchTaskItemQuery;
import com.laker.admin.module.batch.dto.BatchTaskItemView;
import com.laker.admin.module.batch.dto.BatchTaskQuery;
import com.laker.admin.module.batch.dto.BatchTaskSubmitRequest;
import com.laker.admin.module.batch.dto.BatchTaskView;
import com.laker.admin.module.batch.service.BatchTaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/batch/tasks")
@EasyApiAccessLog
public class BatchTaskController {
    private final BatchTaskService batchTaskService;

    public BatchTaskController(BatchTaskService batchTaskService) {
        this.batchTaskService = batchTaskService;
    }

    @GetMapping
    @EasyPermission(EasyPermissions.Batch.TASK_LIST)
    public PageResponse<BatchTaskView> page(BatchTaskQuery query) {
        return batchTaskService.pageTasks(query);
    }

    @GetMapping("/{id}")
    @EasyPermission(EasyPermissions.Batch.TASK_LIST)
    public Response<BatchTaskView> get(@PathVariable Long id) {
        return Response.ok(batchTaskService.getTask(id));
    }

    @GetMapping("/{id}/items")
    @EasyPermission(EasyPermissions.Batch.TASK_LIST)
    public PageResponse<BatchTaskItemView> pageItems(@PathVariable Long id, BatchTaskItemQuery query) {
        return batchTaskService.pageItems(id, query);
    }

    @PostMapping
    @EasyPermission(EasyPermissions.Batch.TASK_MANAGE)
    @EasyAudit(module = "批处理任务", action = "提交批处理任务", dataChange = true, bizType = "BATCH_TASK", changeType = "SUBMIT")
    public Response<BatchTaskView> submit(@RequestBody @Valid BatchTaskSubmitRequest request) {
        return Response.ok(batchTaskService.submitTask(request));
    }

    @PutMapping("/{id}/cancel")
    @EasyPermission(EasyPermissions.Batch.TASK_MANAGE)
    @EasyAudit(module = "批处理任务", action = "请求取消批处理任务", dataChange = true, bizType = "BATCH_TASK", bizId = "#id", changeType = "CANCEL")
    public Response<BatchTaskView> cancel(@PathVariable Long id, @RequestBody(required = false) @Valid BatchTaskCancelRequest request) {
        String reason = request == null ? null : request.getReason();
        return Response.ok(batchTaskService.requestCancel(id, reason));
    }

    @PutMapping("/{id}/retry-failed")
    @EasyPermission(EasyPermissions.Batch.TASK_MANAGE)
    @EasyAudit(module = "批处理任务", action = "重试批处理失败项", dataChange = true, bizType = "BATCH_TASK", bizId = "#id", changeType = "RETRY_FAILED")
    public Response<BatchTaskView> retryFailed(@PathVariable Long id) {
        return Response.ok(batchTaskService.retryFailedItems(id));
    }
}
