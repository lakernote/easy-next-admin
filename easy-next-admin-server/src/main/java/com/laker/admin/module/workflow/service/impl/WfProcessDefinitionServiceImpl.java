package com.laker.admin.module.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.model.PageResponse;
import com.laker.admin.infrastructure.persistence.mybatis.EasyPageSupport;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.module.audit.service.SensitiveAuditService;
import com.laker.admin.module.workflow.dto.WfProcessDefinitionDetail;
import com.laker.admin.module.workflow.dto.WfProcessDefinitionRequest;
import com.laker.admin.module.workflow.dto.WfProcessDefinitionView;
import com.laker.admin.module.workflow.entity.WfProcessDefinition;
import com.laker.admin.module.workflow.entity.WfProcessDefinitionVersion;
import com.laker.admin.module.workflow.entity.WfHistoricProcessInstance;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.mapper.WfProcessDefinitionMapper;
import com.laker.admin.module.workflow.service.IWfHistoricProcessInstanceService;
import com.laker.admin.module.workflow.service.IWfProcessDefinitionService;
import com.laker.admin.module.workflow.service.IWfProcessDefinitionVersionService;
import com.laker.admin.module.workflow.service.IWfProcessInstanceService;
import com.laker.admin.module.workflow.support.WorkflowDefinitionAssigneeValidator;
import com.laker.admin.module.workflow.support.WorkflowDefinitionProjectionSync;
import com.laker.admin.module.workflow.support.WorkflowGraphValidator;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class WfProcessDefinitionServiceImpl
        extends ServiceImpl<WfProcessDefinitionMapper, WfProcessDefinition>
        implements IWfProcessDefinitionService {

    private final IWfProcessDefinitionVersionService versionService;
    private final IWfProcessInstanceService instanceService;
    private final IWfHistoricProcessInstanceService historicInstanceService;
    private final SensitiveAuditService sensitiveAuditService;
    private final WorkflowGraphValidator graphValidator;
    private final WorkflowDefinitionAssigneeValidator assigneeValidator;
    private final WorkflowDefinitionProjectionSync projectionSync;

    public WfProcessDefinitionServiceImpl(IWfProcessDefinitionVersionService versionService,
                                          IWfProcessInstanceService instanceService,
                                          IWfHistoricProcessInstanceService historicInstanceService,
                                          SensitiveAuditService sensitiveAuditService,
                                          WorkflowGraphValidator graphValidator,
                                          WorkflowDefinitionAssigneeValidator assigneeValidator,
                                          WorkflowDefinitionProjectionSync projectionSync) {
        this.versionService = versionService;
        this.instanceService = instanceService;
        this.historicInstanceService = historicInstanceService;
        this.sensitiveAuditService = sensitiveAuditService;
        this.graphValidator = graphValidator;
        this.assigneeValidator = assigneeValidator;
        this.projectionSync = projectionSync;
    }

    @Override
    public PageResponse<WfProcessDefinitionView> pageDefinitions(long page, long limit, String keyword, String status) {
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
        return EasyPageSupport.response(page(EasyPageSupport.page(page, limit), queryWrapper), WfProcessDefinitionView::from);
    }

    @Override
    public WfProcessDefinitionDetail detail(Long id) {
        WfProcessDefinition definition = getById(id);
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
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WfProcessDefinitionDetail saveDefinition(WfProcessDefinitionRequest request) {
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
            WfProcessDefinition oldDefinition = getById(request.getId());
            if (oldDefinition == null) {
                throw new BusinessException("流程定义不存在");
            }
            definition.setCurrentVersion(Objects.requireNonNullElse(oldDefinition.getCurrentVersion(), 1));
            definition.setCreatedBy(oldDefinition.getCreatedBy());
            definition.setCreatedAt(oldDefinition.getCreatedAt());
        }
        definition.setUpdatedAt(now);
        saveOrUpdate(definition);
        if (StringUtils.hasText(request.getGraphJson())) {
            saveVersion(definition, request.getGraphJson());
        }
        sensitiveAuditService.record("流程配置", "保存流程定义", "WORKFLOW_DEFINITION", String.valueOf(definition.getId()),
                "{\"processKey\":\"" + safe(definition.getProcessKey()) + "\"}");
        return detail(definition.getId());
    }

    @Override
    public boolean updateStatus(Long id, String status) {
        WfProcessDefinition definition = getById(id);
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
        boolean updated = updateById(definition);
        if (updated) {
            sensitiveAuditService.record("流程配置", "启停流程定义", "WORKFLOW_DEFINITION", String.valueOf(id),
                    "{\"status\":\"" + safe(status) + "\"}");
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WfProcessDefinitionDetail publish(Long id) {
        WfProcessDefinition definition = getById(id);
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

        // 发布版本用 currentVersion 做乐观条件，避免两个管理员同时发布导致版本错位。
        boolean updated = lambdaUpdate()
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDefinition(Long id) {
        WfProcessDefinition definition = getById(id);
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
        boolean deleted = removeById(id);
        if (deleted) {
            sensitiveAuditService.record("流程配置", "删除流程定义", "WORKFLOW_DEFINITION", String.valueOf(id), "{\"deleted\":true}");
        }
        return deleted;
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
