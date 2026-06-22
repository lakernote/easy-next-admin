package com.laker.admin.infrastructure.security.datascope.resolver;

import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.datascope.model.DataScopeCondition;
import com.laker.admin.infrastructure.security.datascope.model.DataScopeDeptNode;
import com.laker.admin.infrastructure.security.datascope.model.DataScopeType;
import com.laker.admin.infrastructure.security.datascope.repository.DataScopeMetadataRepository;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

@Component
public class CurrentUserDataScopeResolver implements DataScopeResolver {
    private final DataScopeMetadataRepository metadataRepository;

    public CurrentUserDataScopeResolver(DataScopeMetadataRepository metadataRepository) {
        this.metadataRepository = metadataRepository;
    }

    @Override
    public DataScopeCondition resolveCurrentUserScope() {
        AuthPrincipal principal = EasySecurityContext.getPrincipal();
        if (principal == null) {
            // 没有登录上下文时默认拒绝，避免系统内部误用导致数据放开。
            return DataScopeCondition.denied();
        }
        if (principal.isSuperAdmin()) {
            return DataScopeCondition.all(principal.getUserId(), principal.getDeptId(), principal.getDeptIds());
        }
        List<DataScopeType> matchedTypes = principal.getDataScopes();
        if (CollectionUtils.isEmpty(matchedTypes)) {
            return selfOnly(principal);
        }
        matchedTypes = matchedTypes.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (matchedTypes.contains(DataScopeType.ALL)) {
            return DataScopeCondition.all(principal.getUserId(), principal.getDeptId(), principal.getDeptIds());
        }
        Set<Long> deptIds = new HashSet<>();
        // 多角色场景取可见部门并集，最终仍由 Mapper 声明的字段落到 SQL。
        if (matchedTypes.contains(DataScopeType.DEPT_SETS)) {
            deptIds.addAll(metadataRepository.listCustomDeptIdsByUserId(principal.getUserId()));
        }
        if (matchedTypes.contains(DataScopeType.DEPT_AND_CHILDREN)) {
            deptIds.addAll(resolveDeptAndChildren(principal.getDeptId()));
        }
        if (matchedTypes.contains(DataScopeType.DEPT) && principal.getDeptId() != null) {
            deptIds.add(principal.getDeptId());
        }
        if (!deptIds.isEmpty()) {
            return new DataScopeCondition(resolveDeptScopeType(matchedTypes), principal.getUserId(), principal.getDeptId(), deptIds);
        }
        return selfOnly(principal);
    }

    private DataScopeType resolveDeptScopeType(List<DataScopeType> matchedTypes) {
        if (matchedTypes.size() == 1) {
            DataScopeType type = matchedTypes.get(0);
            if (type == DataScopeType.DEPT || type == DataScopeType.DEPT_AND_CHILDREN) {
                return type;
            }
        }
        return DataScopeType.DEPT_SETS;
    }

    private DataScopeCondition selfOnly(AuthPrincipal principal) {
        return DataScopeCondition.self(principal.getUserId(), principal.getDeptId(), principal.getDeptIds());
    }

    private Set<Long> resolveDeptAndChildren(Long deptId) {
        if (deptId == null) {
            return Set.of();
        }
        // 部门树由仓储层统一读取和缓存，组织变更后通过缓存失效刷新层级。
        List<DataScopeDeptNode> departments = metadataRepository.listActiveDeptNodes();
        Map<Long, List<Long>> childrenByParentId = indexChildrenByParentId(departments);
        Set<Long> deptIds = new HashSet<>();
        collectDeptIds(childrenByParentId, deptId, deptIds);
        return deptIds;
    }

    private Map<Long, List<Long>> indexChildrenByParentId(List<DataScopeDeptNode> departments) {
        Map<Long, List<Long>> childrenByParentId = new HashMap<>();
        for (DataScopeDeptNode department : departments) {
            if (department.parentId() == null || department.deptId() == null) {
                continue;
            }
            childrenByParentId.computeIfAbsent(department.parentId(), ignored -> new ArrayList<>())
                    .add(department.deptId());
        }
        return childrenByParentId;
    }

    private void collectDeptIds(Map<Long, List<Long>> childrenByParentId, Long rootDeptId, Set<Long> deptIds) {
        Queue<Long> pendingDeptIds = new ArrayDeque<>();
        pendingDeptIds.add(rootDeptId);
        while (!pendingDeptIds.isEmpty()) {
            Long currentDeptId = pendingDeptIds.poll();
            if (currentDeptId == null || !deptIds.add(currentDeptId)) {
                continue;
            }
            pendingDeptIds.addAll(childrenByParentId.getOrDefault(currentDeptId, List.of()));
        }
    }
}
