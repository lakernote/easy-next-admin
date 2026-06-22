package com.laker.admin.module.workflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.model.PageResponse;
import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.audit.EasyAudit;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.audit.service.SensitiveAuditService;
import com.laker.admin.module.workflow.dto.WfProcessDefinitionDetail;
import com.laker.admin.module.workflow.dto.WfProcessDefinitionRequest;
import com.laker.admin.module.workflow.dto.WfProcessDefinitionView;
import com.laker.admin.module.workflow.entity.WfProcessDefinition;
import com.laker.admin.module.workflow.entity.WfProcessDefinitionVersion;
import com.laker.admin.module.workflow.entity.WfHistoricProcessInstance;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.service.IWfHistoricProcessInstanceService;
import com.laker.admin.module.workflow.service.IWfProcessDefinitionService;
import com.laker.admin.module.workflow.service.IWfProcessDefinitionVersionService;
import com.laker.admin.module.workflow.service.IWfProcessInstanceService;
import com.laker.admin.module.workflow.support.WorkflowDefinitionAssigneeValidator;
import com.laker.admin.module.workflow.support.WorkflowDefinitionProjectionSync;
import com.laker.admin.module.workflow.support.WorkflowGraphValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Tag(name = "流程定义")
@RestController
@RequestMapping("/api/workflow/definitions")
public class WfProcessDefinitionController {
    private final IWfProcessDefinitionService definitionService;
    private final IWfProcessDefinitionVersionService versionService;
    private final IWfProcessInstanceService instanceService;
    private final IWfHistoricProcessInstanceService historicInstanceService;
    private final SensitiveAuditService sensitiveAuditService;
    private final WorkflowGraphValidator graphValidator;
    private final WorkflowDefinitionAssigneeValidator assigneeValidator;
    private final WorkflowDefinitionProjectionSync projectionSync;

    public WfProcessDefinitionController(IWfProcessDefinitionService definitionService,
                                                 IWfProcessDefinitionVersionService versionService,
                                                 IWfProcessInstanceService instanceService,
                                                 IWfHistoricProcessInstanceService historicInstanceService,
                                                 SensitiveAuditService sensitiveAuditService,
                                                 WorkflowGraphValidator graphValidator,
                                                 WorkflowDefinitionAssigneeValidator assigneeValidator,
                                                 WorkflowDefinitionProjectionSync projectionSync) {
        this.definitionService = definitionService;
        this.versionService = versionService;
        this.instanceService = instanceService;
        this.historicInstanceService = historicInstanceService;
        this.sensitiveAuditService = sensitiveAuditService;
        this.graphValidator = graphValidator;
        this.assigneeValidator = assigneeValidator;
        this.projectionSync = projectionSync;
    }

    @GetMapping
    @EasyPermission(EasyPermissions.Workflow.VIEW)
    @Operation(summary = "分页查询流程定义")
    public PageResponse<WfProcessDefinitionView> page(@RequestParam(required = false, defaultValue = "1") long page,
                                                      @RequestParam(required = false, defaultValue = "10") long limit,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) String status) {
        LambdaQueryWrapper<WfProcessDefinition> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            queryWrapper.and(wrapper -> wrapper
                    .like(WfProcessDefinition::getProcessName, keyword)
                    .or()
                    .like(WfProcessDefinition::getProcessKey, keyword));
        }
        if (StringUtils.hasText(status) && !"ALL".equalsIgnoreCase(status)) {
            queryWrapper.eq(WfProcessDefinition::getStatus, status);
        }
        queryWrapper.orderByDesc(WfProcessDefinition::getUpdatedAt);
        Page<WfProcessDefinition> pageResult = definitionService.page(new Page<>(page, limit), queryWrapper);
        return PageResponse.ok(WfProcessDefinitionView.fromList(pageResult.getRecords()), pageResult.getTotal());
    }

    @GetMapping("/{id}")
    @EasyPermission(EasyPermissions.Workflow.VIEW)
    @Operation(summary = "查询流程定义详情")
    public Response<WfProcessDefinitionDetail> detail(@PathVariable Long id) {
        WfProcessDefinition definition = definitionService.getById(id);
        if (definition == null) {
            throw new BusinessException("流程定义不存在");
        }
        WfProcessDefinitionDetail detail = new WfProcessDefinitionDetail();
        BeanUtils.copyProperties(definition, detail);
        versionService.lambdaQuery()
                .eq(WfProcessDefinitionVersion::getDefinitionId, id)
                .eq(WfProcessDefinitionVersion::getVersion, definition.getCurrentVersion())
                .oneOpt()
                .ifPresent(version -> detail.setGraphJson(version.getGraphJson()));
        return Response.ok(detail);
    }

    @PostMapping
    @EasyPermission(EasyPermissions.Workflow.DEFINITION_EDIT)
    @EasyAudit(module = "流程配置", action = "保存流程定义", dataChange = true, bizType = "WORKFLOW_DEFINITION", changeType = "SAVE")
    @Operation(summary = "保存流程定义")
    @Transactional(rollbackFor = Exception.class)
    public Response<WfProcessDefinitionDetail> save(@Valid @RequestBody WfProcessDefinitionRequest request) {
        WfProcessDefinition definition = new WfProcessDefinition();
        definition.setId(request.getId());
        definition.setProcessKey(request.getProcessKey());
        definition.setProcessName(request.getProcessName());
        definition.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : "DRAFT");
        if ("ENABLED".equals(definition.getStatus())) {
            validateForEnable(request.getGraphJson());
        }
        definition.setRemark(request.getRemark());
        definition.setCurrentVersion(1);
        LocalDateTime now = LocalDateTime.now();
        if (request.getId() == null) {
            definition.setCreatedAt(now);
        } else {
            WfProcessDefinition oldDefinition = definitionService.getById(request.getId());
            if (oldDefinition == null) {
                throw new BusinessException("流程定义不存在");
            }
            definition.setCurrentVersion(Objects.requireNonNullElse(oldDefinition.getCurrentVersion(), 1));
            definition.setCreatedBy(oldDefinition.getCreatedBy());
            definition.setCreatedAt(oldDefinition.getCreatedAt());
        }
        definition.setUpdatedAt(now);
        definitionService.saveOrUpdate(definition);
        if (StringUtils.hasText(request.getGraphJson())) {
            saveVersion(definition, request.getGraphJson());
        }
        sensitiveAuditService.record("流程配置", "保存流程定义", "WORKFLOW_DEFINITION", String.valueOf(definition.getId()),
                "{\"processKey\":\"" + safe(definition.getProcessKey()) + "\"}");
        return detail(definition.getId());
    }

    @PutMapping("/{id}/status")
    @EasyPermission(EasyPermissions.Workflow.DEFINITION_EDIT)
    @EasyAudit(module = "流程配置", action = "启停流程定义", dataChange = true, bizType = "WORKFLOW_DEFINITION", bizId = "#id", changeType = "STATUS")
    @Operation(summary = "启停流程定义")
    public Response<Boolean> updateStatus(@PathVariable Long id, @RequestParam String status) {
        WfProcessDefinition definition = definitionService.getById(id);
        if (definition == null) {
            throw new BusinessException("流程定义不存在");
        }
        if ("ENABLED".equals(status)) {
            WfProcessDefinitionVersion current = versionService.lambdaQuery()
                    .eq(WfProcessDefinitionVersion::getDefinitionId, id)
                    .eq(WfProcessDefinitionVersion::getVersion, definition.getCurrentVersion())
                    .one();
            if (current == null || !StringUtils.hasText(current.getGraphJson())) {
                throw new BusinessException("请先保存当前流程设计");
            }
            validateForEnable(current.getGraphJson());
        }
        definition.setStatus(status);
        definition.setUpdatedAt(LocalDateTime.now());
        boolean updated = definitionService.updateById(definition);
        if (updated) {
            sensitiveAuditService.record("流程配置", "启停流程定义", "WORKFLOW_DEFINITION", String.valueOf(id),
                    "{\"status\":\"" + safe(status) + "\"}");
        }
        return Response.ok(updated);
    }

    @PutMapping("/{id}/publish")
    @EasyPermission(EasyPermissions.Workflow.DEFINITION_EDIT)
    @EasyAudit(module = "流程配置", action = "发布流程定义", dataChange = true, bizType = "WORKFLOW_DEFINITION", bizId = "#id", changeType = "PUBLISH")
    @Operation(summary = "发布流程定义新版本")
    @Transactional(rollbackFor = Exception.class)
    public Response<WfProcessDefinitionDetail> publish(@PathVariable Long id) {
        WfProcessDefinition definition = definitionService.getById(id);
        if (definition == null) {
            throw new BusinessException("流程定义不存在");
        }
        int currentVersion = Objects.requireNonNullElse(definition.getCurrentVersion(), 1);
        int nextVersion = currentVersion + 1;
        WfProcessDefinitionVersion current = versionService.lambdaQuery()
                .eq(WfProcessDefinitionVersion::getDefinitionId, id)
                .eq(WfProcessDefinitionVersion::getVersion, currentVersion)
                .one();
        if (current == null || !StringUtils.hasText(current.getGraphJson())) {
            throw new BusinessException("请先保存当前流程设计");
        }
        validateForEnable(current.getGraphJson());
        if (versionService.lambdaQuery()
                .eq(WfProcessDefinitionVersion::getDefinitionId, id)
                .eq(WfProcessDefinitionVersion::getVersion, nextVersion)
                .count() > 0) {
            throw new BusinessException("流程定义版本已变化，请刷新后重试");
        }

        LocalDateTime now = LocalDateTime.now();
        WfProcessDefinitionVersion next = new WfProcessDefinitionVersion();
        next.setDefinitionId(id);
        next.setVersion(nextVersion);
        next.setGraphJson(current.getGraphJson());
        next.setStatus("PUBLISHED");
        next.setPublishedBy(EasySecurityContext.getUserId());
        next.setPublishedAt(now);
        next.setCreatedBy(EasySecurityContext.getUserId());
        next.setCreatedAt(now);
        next.setUpdatedBy(EasySecurityContext.getUserId());
        next.setUpdatedAt(now);
        versionService.save(next);
        projectionSync.sync(next, next.getGraphJson());

        boolean updated = definitionService.lambdaUpdate()
                .eq(WfProcessDefinition::getId, id)
                .eq(WfProcessDefinition::getCurrentVersion, currentVersion)
                .set(WfProcessDefinition::getCurrentVersion, nextVersion)
                .set(WfProcessDefinition::getStatus, "ENABLED")
                .set(WfProcessDefinition::getUpdatedBy, EasySecurityContext.getUserId())
                .set(WfProcessDefinition::getUpdatedAt, now)
                .update();
        if (!updated) {
            throw new BusinessException("流程定义版本已变化，请刷新后重试");
        }
        sensitiveAuditService.record("流程配置", "发布流程定义", "WORKFLOW_DEFINITION", String.valueOf(id),
                "{\"version\":" + nextVersion + "}");
        return detail(id);
    }

    @DeleteMapping("/{id}")
    @EasyPermission(EasyPermissions.Workflow.DEFINITION_EDIT)
    @EasyAudit(module = "流程配置", action = "删除流程定义", dataChange = true, bizType = "WORKFLOW_DEFINITION", bizId = "#id", changeType = "DELETE")
    @Operation(summary = "删除流程定义")
    @Transactional(rollbackFor = Exception.class)
    public Response<Boolean> delete(@PathVariable Long id) {
        WfProcessDefinition definition = definitionService.getById(id);
        if (definition == null) {
            throw new BusinessException("流程定义不存在");
        }
        long instanceCount = instanceService.lambdaQuery()
                .eq(WfProcessInstance::getDefinitionId, id)
                .count();
        long historicInstanceCount = historicInstanceService.lambdaQuery()
                .eq(WfHistoricProcessInstance::getDefinitionId, id)
                .count();
        if (instanceCount + historicInstanceCount > 0) {
            throw new BusinessException("流程定义已有实例，不能删除，请改为停用");
        }
        List<Long> versionIds = versionService.lambdaQuery()
                .select(WfProcessDefinitionVersion::getId)
                .eq(WfProcessDefinitionVersion::getDefinitionId, id)
                .list()
                .stream()
                .map(WfProcessDefinitionVersion::getId)
                .toList();
        projectionSync.removeByVersionIds(versionIds);
        versionService.lambdaUpdate()
                .eq(WfProcessDefinitionVersion::getDefinitionId, id)
                .remove();
        boolean deleted = definitionService.removeById(id);
        if (deleted) {
            sensitiveAuditService.record("流程配置", "删除流程定义", "WORKFLOW_DEFINITION", String.valueOf(id), "{\"deleted\":true}");
        }
        return Response.ok(deleted);
    }

    private void saveVersion(WfProcessDefinition definition, String graphJson) {
        WfProcessDefinitionVersion version = versionService.lambdaQuery()
                .eq(WfProcessDefinitionVersion::getDefinitionId, definition.getId())
                .eq(WfProcessDefinitionVersion::getVersion, definition.getCurrentVersion())
                .one();
        if (version == null) {
            version = new WfProcessDefinitionVersion();
            version.setDefinitionId(definition.getId());
            version.setVersion(definition.getCurrentVersion());
            version.setCreatedAt(LocalDateTime.now());
        }
        version.setGraphJson(graphJson);
        version.setStatus("PUBLISHED");
        version.setPublishedAt(LocalDateTime.now());
        version.setUpdatedAt(LocalDateTime.now());
        versionService.saveOrUpdate(version);
        projectionSync.sync(version, graphJson);
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void validateForEnable(String graphJson) {
        graphValidator.validateForEnable(graphJson);
        assigneeValidator.validateForEnable(graphJson);
    }
}
