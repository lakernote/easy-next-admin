package com.laker.admin.module.assistant.tool.workflow;

import com.laker.admin.module.assistant.context.AssistantRequestContext.AssistantUserContext;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.module.workflow.entity.WfHistoricProcessInstance;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.service.IWfHistoricProcessInstanceService;
import com.laker.admin.module.workflow.service.IWfProcessInstanceService;
import com.laker.admin.module.workflow.support.WfHistoricProcessInstanceQueryBuilder;
import com.laker.admin.module.workflow.support.WfProcessInstanceQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class DefaultWorkflowProcessToolQueryService implements WorkflowProcessToolQueryService {
    private final IWfProcessInstanceService instanceService;
    private final IWfHistoricProcessInstanceService historicInstanceService;

    public DefaultWorkflowProcessToolQueryService(IWfProcessInstanceService instanceService,
                                                  IWfHistoricProcessInstanceService historicInstanceService) {
        this.instanceService = Objects.requireNonNull(instanceService, "instanceService must not be null");
        this.historicInstanceService = Objects.requireNonNull(historicInstanceService, "historicInstanceService must not be null");
    }

    @Override
    public List<WorkflowProcessToolItem> myStartedProcesses(AssistantUserContext user, String keyword, int limit) {
        return myStartedProcesses(user, keyword, null, limit);
    }

    @Override
    public List<WorkflowProcessToolItem> myStartedProcesses(AssistantUserContext user,
                                                            String keyword,
                                                            WorkflowProcessStatusFilter statusFilter,
                                                            int limit) {
        if (!hasUser(user)) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 10));
        List<WorkflowProcessToolItem> runtimeItems = runtimeProcesses(user, keyword, statusFilter, safeLimit).stream()
                .map(instance -> toItem(instance, "RUNTIME"))
                .toList();
        List<WorkflowProcessToolItem> historicItems = historicProcesses(user, keyword, statusFilter, safeLimit).stream()
                .map(instance -> toItem(instance, "HISTORY"))
                .toList();
        return Stream.concat(runtimeItems.stream(), historicItems.stream())
                .sorted(Comparator.comparing(WorkflowProcessToolItem::startedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(safeLimit)
                .toList();
    }

    @Override
    public Optional<WorkflowProcessToolItem> visibleProcessDetail(AssistantUserContext user, String identifier) {
        if (!hasUser(user) || !StringUtils.hasText(identifier)) {
            return Optional.empty();
        }
        String normalized = identifier.trim();
        Long instanceId = parseInstanceId(normalized);
        Optional<WfProcessInstance> runtime = runtimeProcessDetail(user, normalized, instanceId);
        if (runtime.isPresent()) {
            return runtime.map(instance -> toItem(instance, "RUNTIME"));
        }
        return historicProcessDetail(user, normalized, instanceId)
                .map(instance -> toItem(instance, "HISTORY"));
    }

    private List<WfProcessInstance> runtimeProcesses(AssistantUserContext user,
                                                     String keyword,
                                                     WorkflowProcessStatusFilter statusFilter,
                                                     int limit) {
        var query = WfProcessInstanceQueryBuilder.build(
                toPrincipal(user), true, false, null, null, keyword);
        if (statusFilter != null) {
            query.in(WfProcessInstance::getStatus, statusFilter.instanceStatuses());
        }
        return instanceService.list(query.last("LIMIT " + limit));
    }

    private List<WfHistoricProcessInstance> historicProcesses(AssistantUserContext user,
                                                               String keyword,
                                                               WorkflowProcessStatusFilter statusFilter,
                                                               int limit) {
        var query = WfHistoricProcessInstanceQueryBuilder.build(
                toPrincipal(user), true, false, null, null, keyword);
        if (statusFilter != null) {
            query.in(WfHistoricProcessInstance::getStatus, statusFilter.instanceStatuses());
        }
        return historicInstanceService.list(query.last("LIMIT " + limit));
    }

    private Optional<WfProcessInstance> runtimeProcessDetail(AssistantUserContext user,
                                                             String identifier,
                                                             Long instanceId) {
        var query = WfProcessInstanceQueryBuilder.build(
                toPrincipal(user), false, false, null, null, null);
        if (instanceId == null) {
            query.eq(WfProcessInstance::getBusinessId, identifier);
        } else {
            query.and(wrapper -> wrapper.eq(WfProcessInstance::getId, instanceId)
                    .or()
                    .eq(WfProcessInstance::getBusinessId, identifier));
        }
        return Optional.ofNullable(instanceService.getOne(query.last("LIMIT 1"), false));
    }

    private Optional<WfHistoricProcessInstance> historicProcessDetail(AssistantUserContext user,
                                                                       String identifier,
                                                                       Long instanceId) {
        var query = WfHistoricProcessInstanceQueryBuilder.build(
                toPrincipal(user), false, false, null, null, null);
        if (instanceId == null) {
            query.eq(WfHistoricProcessInstance::getBusinessId, identifier);
        } else {
            query.and(wrapper -> wrapper.eq(WfHistoricProcessInstance::getId, instanceId)
                    .or()
                    .eq(WfHistoricProcessInstance::getBusinessId, identifier));
        }
        return Optional.ofNullable(historicInstanceService.getOne(query.last("LIMIT 1"), false));
    }

    private static boolean hasUser(AssistantUserContext user) {
        return user != null && user.userId() != null;
    }

    private static AuthPrincipal toPrincipal(AssistantUserContext user) {
        return AuthPrincipal.builder()
                .sessionId(user.sessionId())
                .userId(user.userId())
                .userName(user.userName())
                .nickName(user.nickName())
                .deptId(user.deptId())
                .deptName(user.deptName())
                .deptIds(user.deptIds())
                .roles(user.roles())
                .roleNames(user.roleNames())
                .permissions(user.permissions())
                .superAdmin(user.superAdmin())
                .build();
    }

    private static Long parseInstanceId(String identifier) {
        try {
            return Long.valueOf(identifier);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static WorkflowProcessToolItem toItem(WfProcessInstance instance, String source) {
        return new WorkflowProcessToolItem(
                instance.getId(),
                instance.getBusinessType(),
                instance.getBusinessId(),
                instance.getTitle(),
                instance.getCurrentNodeKey(),
                instance.getStatus(),
                instance.getStartedAt(),
                source
        );
    }
}
