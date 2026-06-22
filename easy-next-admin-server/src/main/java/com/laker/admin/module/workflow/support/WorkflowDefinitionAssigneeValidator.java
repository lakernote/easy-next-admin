package com.laker.admin.module.workflow.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.module.system.entity.SysRole;
import com.laker.admin.module.system.entity.SysUser;
import com.laker.admin.module.system.service.ISysRoleService;
import com.laker.admin.module.system.service.ISysUserRoleService;
import com.laker.admin.module.system.service.ISysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class WorkflowDefinitionAssigneeValidator {
    private final WorkflowGraphParser graphParser;
    private final ISysUserService userService;
    private final ISysRoleService roleService;
    private final ISysUserRoleService userRoleService;

    public void validateForEnable(String graphJson) {
        WorkflowGraph graph = graphParser.parse(graphJson);
        graph.nodes().values().stream()
                .filter(node -> node.kind().isApproval())
                .forEach(this::validateApprovalAssignee);
    }

    private void validateApprovalAssignee(WorkflowGraph.NodeInfo node) {
        WorkflowAssigneeRule rule = WorkflowAssigneeRule.of(
                node.propertyText(WorkflowGraphProperty.APPROVER_TYPE),
                nodeUserIds(node, WorkflowGraphProperty.ASSIGNEE_IDS),
                node.propertyText(WorkflowGraphProperty.ROLE_CODE));
        switch (rule.approverType()) {
            case USER -> validateFixedUsers(node, rule.assigneeIds());
            case ROLE -> validateRoleMembers(node, rule.roleCode());
            case INITIATOR, INITIATOR_SELECTED, MANAGER, DEPT_LEADER, UPPER_DEPT_LEADER, UNKNOWN -> {
            }
        }
    }

    private void validateFixedUsers(WorkflowGraph.NodeInfo node, List<Long> assigneeIds) {
        if (assigneeIds.isEmpty()) {
            throw new BusinessException(node.name() + " 未配置有效处理人规则");
        }
        Set<Long> activeUserIds = userService.listByIds(assigneeIds)
                .stream()
                .filter(user -> user.getEnable() != null && user.getEnable() == 1)
                .map(SysUser::getUserId)
                .collect(java.util.stream.Collectors.toSet());
        if (!activeUserIds.containsAll(assigneeIds)) {
            throw new BusinessException(node.name() + " 配置的指定审批人不存在或已停用");
        }
    }

    private void validateRoleMembers(WorkflowGraph.NodeInfo node, String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            throw new BusinessException(node.name() + " 未配置有效处理人规则");
        }
        SysRole role = roleService.lambdaQuery()
                .eq(SysRole::getRoleCode, roleCode.trim())
                .eq(SysRole::getEnable, true)
                .list()
                .stream()
                .findFirst()
                .orElse(null);
        if (role == null) {
            throw new BusinessException(node.name() + " 配置的审批角色不存在或已停用");
        }
        List<Long> roleUserIds = userRoleService.listUserIdsByRoleId(role.getRoleId());
        if (roleUserIds.isEmpty()) {
            throw new BusinessException(node.name() + " 配置的审批角色没有可用成员");
        }
        boolean hasActiveMember = userService.listByIds(roleUserIds)
                .stream()
                .anyMatch(user -> user.getEnable() != null && user.getEnable() == 1);
        if (!hasActiveMember) {
            throw new BusinessException(node.name() + " 配置的审批角色成员不存在或已停用");
        }
    }

    private List<Long> nodeUserIds(WorkflowGraph.NodeInfo node, WorkflowGraphProperty property) {
        List<Long> ids = new ArrayList<>();
        appendLongs(ids, node.property(property));
        return ids.stream().distinct().toList();
    }

    private void appendLongs(List<Long> target, JsonNode valueNode) {
        if (valueNode == null || valueNode.isNull()) {
            return;
        }
        if (valueNode.isArray()) {
            valueNode.forEach(item -> appendLongs(target, item));
            return;
        }
        if (valueNode.isNumber()) {
            target.add(valueNode.asLong());
            return;
        }
        if (valueNode.isTextual() && StringUtils.hasText(valueNode.asText())) {
            try {
                target.add(Long.valueOf(valueNode.asText().trim()));
            } catch (NumberFormatException ignored) {
                // 非数字成员标识不会映射到系统用户，后续按无效审批人处理。
            }
        }
    }
}
