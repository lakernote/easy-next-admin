package com.laker.admin.module.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.model.PageResponse;
import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.audit.EasyAudit;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.workflow.dto.WfProcessInstanceDetail;
import com.laker.admin.module.workflow.dto.WfTaskActionRequest;
import com.laker.admin.module.workflow.dto.WfTaskListItem;
import com.laker.admin.module.workflow.dto.WfTaskView;
import com.laker.admin.module.workflow.entity.WfHistoricTask;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.entity.WfTask;
import com.laker.admin.module.workflow.service.IWfHistoricTaskService;
import com.laker.admin.module.workflow.service.IWfTaskService;
import com.laker.admin.module.workflow.service.IWfWorkflowRuntimeService;
import com.laker.admin.module.workflow.support.WfHistoricTaskQueryBuilder;
import com.laker.admin.module.workflow.support.WfTaskQueryBuilder;
import com.laker.admin.module.workflow.support.WorkflowArchiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Tag(name = "流程任务")
@RestController
@RequestMapping("/api/workflow/tasks")
public class WfTaskController {
    private final IWfTaskService taskService;
    private final IWfHistoricTaskService historicTaskService;
    private final IWfWorkflowRuntimeService runtimeService;
    private final WorkflowArchiveService archiveService;

    public WfTaskController(IWfTaskService taskService,
                            IWfHistoricTaskService historicTaskService,
                            IWfWorkflowRuntimeService runtimeService,
                            WorkflowArchiveService archiveService) {
        this.taskService = taskService;
        this.historicTaskService = historicTaskService;
        this.runtimeService = runtimeService;
        this.archiveService = archiveService;
    }

    @GetMapping
    @EasyPermission(EasyPermissions.Workflow.VIEW)
    @Operation(summary = "分页查询流程任务")
    public PageResponse<WfTaskListItem> page(@RequestParam(required = false, defaultValue = "1") long page,
                                                   @RequestParam(required = false, defaultValue = "10") long limit,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String statuses,
                                                   @RequestParam(required = false) Long instanceId,
                                                   @RequestParam(required = false) Long assigneeId,
                                                   @RequestParam(required = false, defaultValue = "false") boolean mine) {
        if (mine) {
            Long userId = EasySecurityContext.getUserId();
            if (userId == null) {
                throw new BusinessException("未获取到当前登录用户");
            }
        }
        AuthPrincipal principal = EasySecurityContext.getPrincipal();
        List<String> parsedStatuses = parseStatuses(statuses);
        assertSingleTaskScope(status, parsedStatuses);
        if (historyOnly(status, parsedStatuses)) {
            var queryWrapper = WfHistoricTaskQueryBuilder.build(principal, status, parsedStatuses, instanceId, assigneeId, mine);
            Page<WfHistoricTask> pageResult = historicTaskService.page(new Page<>(page, limit), queryWrapper);
            Map<Long, WfProcessInstance> instanceMap = instanceMap(pageResult.getRecords().stream()
                    .map(WfHistoricTask::getInstanceId)
                    .toList());
            List<WfTaskListItem> records = pageResult.getRecords().stream()
                    .map(task -> WfTaskListItem.from(task, instanceMap.get(task.getInstanceId())))
                    .toList();
            return PageResponse.ok(records, pageResult.getTotal());
        }

        var queryWrapper = WfTaskQueryBuilder.build(principal, status, parsedStatuses, instanceId, assigneeId, mine);
        Page<WfTask> pageResult = taskService.page(new Page<>(page, limit), queryWrapper);
        Map<Long, WfProcessInstance> instanceMap = instanceMap(pageResult.getRecords().stream()
                .map(WfTask::getInstanceId)
                .toList());
        List<WfTaskListItem> records = pageResult.getRecords().stream()
                .map(task -> WfTaskListItem.from(task, instanceMap.get(task.getInstanceId())))
                .toList();
        return PageResponse.ok(records, pageResult.getTotal());
    }

    private void assertSingleTaskScope(String status, List<String> statuses) {
        boolean containsPending = "PENDING".equals(status) || statuses.stream().anyMatch("PENDING"::equals);
        boolean containsHistory = (StringUtils.hasText(status) && !"PENDING".equals(status))
                || statuses.stream().anyMatch(item -> !"PENDING".equals(item));
        if (containsPending && containsHistory) {
            throw new BusinessException("待办任务和已办任务请分开查询");
        }
    }

    private Map<Long, WfProcessInstance> instanceMap(List<Long> instanceIds) {
        return archiveService.instanceMap(instanceIds);
    }

    private boolean historyOnly(String status, List<String> statuses) {
        if (StringUtils.hasText(status)) {
            return !"PENDING".equals(status);
        }
        return !statuses.isEmpty() && statuses.stream().noneMatch("PENDING"::equals);
    }

    private List<String> parseStatuses(String statuses) {
        if (!StringUtils.hasText(statuses)) {
            return List.of();
        }
        return Arrays.stream(statuses.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    @GetMapping("/{id}")
    @EasyPermission(EasyPermissions.Workflow.VIEW)
    @Operation(summary = "查询流程任务详情")
    public Response<WfTaskView> detail(@PathVariable Long id) {
        WfTask task = taskService.getById(id);
        if (task == null) {
            WfHistoricTask historicTask = historicTaskService.getById(id);
            if (historicTask == null) {
                throw new BusinessException("流程任务不存在");
            }
            task = archiveService.toRuntimeTask(historicTask);
        }
        runtimeService.detail(task.getInstanceId());
        return Response.ok(WfTaskView.from(task));
    }

    @PutMapping("/{id}/approve")
    @EasyPermission(EasyPermissions.Workflow.TASK_APPROVE)
    @EasyAudit(module = "流程任务", action = "同意流程任务", dataChange = true, bizType = "WORKFLOW_TASK", bizId = "#id", changeType = "APPROVE")
    @Operation(summary = "同意流程任务")
    public Response<WfProcessInstanceDetail> approve(@PathVariable Long id,
                                                     @Valid @RequestBody(required = false) WfTaskActionRequest request) {
        return Response.ok(runtimeService.approve(id, request == null ? new WfTaskActionRequest() : request));
    }

    @PutMapping("/{id}/reject")
    @EasyPermission(EasyPermissions.Workflow.TASK_REJECT)
    @EasyAudit(module = "流程任务", action = "驳回流程任务", dataChange = true, bizType = "WORKFLOW_TASK", bizId = "#id", changeType = "REJECT")
    @Operation(summary = "驳回流程任务")
    public Response<WfProcessInstanceDetail> reject(@PathVariable Long id,
                                                    @Valid @RequestBody(required = false) WfTaskActionRequest request) {
        return Response.ok(runtimeService.reject(id, request == null ? new WfTaskActionRequest() : request));
    }

    @PutMapping("/{id}/transfer")
    @EasyPermission(EasyPermissions.Workflow.TASK_TRANSFER)
    @EasyAudit(module = "流程任务", action = "转办流程任务", dataChange = true, bizType = "WORKFLOW_TASK", bizId = "#id", changeType = "TRANSFER")
    @Operation(summary = "转办流程任务")
    public Response<WfProcessInstanceDetail> transfer(@PathVariable Long id,
                                                       @Valid @RequestBody WfTaskActionRequest request) {
        return Response.ok(runtimeService.transfer(id, request));
    }

    @PutMapping("/{id}/delegate")
    @EasyPermission(EasyPermissions.Workflow.TASK_DELEGATE)
    @EasyAudit(module = "流程任务", action = "委派流程任务", dataChange = true, bizType = "WORKFLOW_TASK", bizId = "#id", changeType = "DELEGATE")
    @Operation(summary = "委派流程任务")
    public Response<WfProcessInstanceDetail> delegate(@PathVariable Long id,
                                                       @Valid @RequestBody WfTaskActionRequest request) {
        return Response.ok(runtimeService.delegate(id, request));
    }

    @PutMapping("/{id}/return")
    @EasyPermission(EasyPermissions.Workflow.TASK_RETURN)
    @EasyAudit(module = "流程任务", action = "退回流程任务", dataChange = true, bizType = "WORKFLOW_TASK", bizId = "#id", changeType = "RETURN")
    @Operation(summary = "退回流程任务")
    public Response<WfProcessInstanceDetail> returnTask(@PathVariable Long id,
                                                        @Valid @RequestBody WfTaskActionRequest request) {
        return Response.ok(runtimeService.returnTask(id, request));
    }

    @PutMapping("/{id}/add-sign")
    @EasyPermission(EasyPermissions.Workflow.TASK_ADD_SIGN)
    @EasyAudit(module = "流程任务", action = "加签流程任务", dataChange = true, bizType = "WORKFLOW_TASK", bizId = "#id", changeType = "ADD_SIGN")
    @Operation(summary = "加签流程任务")
    public Response<WfProcessInstanceDetail> addSign(@PathVariable Long id,
                                                     @Valid @RequestBody WfTaskActionRequest request) {
        return Response.ok(runtimeService.addSign(id, request));
    }

    @PutMapping("/{id}/remove-sign")
    @EasyPermission(EasyPermissions.Workflow.TASK_REMOVE_SIGN)
    @EasyAudit(module = "流程任务", action = "减签流程任务", dataChange = true, bizType = "WORKFLOW_TASK", bizId = "#id", changeType = "REMOVE_SIGN")
    @Operation(summary = "减签流程任务")
    public Response<WfProcessInstanceDetail> removeSign(@PathVariable Long id,
                                                        @Valid @RequestBody WfTaskActionRequest request) {
        return Response.ok(runtimeService.removeSign(id, request));
    }

    @PutMapping("/{id}/remind")
    @EasyPermission(EasyPermissions.Workflow.TASK_REMIND)
    @EasyAudit(module = "流程任务", action = "催办流程任务", dataChange = true, bizType = "WORKFLOW_TASK", bizId = "#id", changeType = "REMIND")
    @Operation(summary = "催办流程任务")
    public Response<WfProcessInstanceDetail> remind(@PathVariable Long id,
                                                    @Valid @RequestBody(required = false) WfTaskActionRequest request) {
        return Response.ok(runtimeService.remind(id, request == null ? new WfTaskActionRequest() : request));
    }
}
