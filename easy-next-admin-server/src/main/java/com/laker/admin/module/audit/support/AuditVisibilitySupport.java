package com.laker.admin.module.audit.support;

import com.laker.admin.infrastructure.security.datascope.context.EasyDataScopeContext;
import com.laker.admin.infrastructure.security.datascope.model.DataScopeCondition;
import com.laker.admin.infrastructure.security.datascope.resolver.DataScopeResolver;
import com.laker.admin.module.system.entity.SysUser;
import com.laker.admin.module.system.service.ISysUserService;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AuditVisibilitySupport {
    private final DataScopeResolver dataScopeResolver;
    private final ISysUserService sysUserService;

    public AuditVisibilitySupport(DataScopeResolver dataScopeResolver, ISysUserService sysUserService) {
        this.dataScopeResolver = dataScopeResolver;
        this.sysUserService = sysUserService;
    }

    /**
     * 空 Optional 表示当前账号可查看全部审计操作者。
     */
    public Optional<Set<Long>> visibleUserIds() {
        DataScopeCondition condition = dataScopeResolver.resolveCurrentUserScope();
        if (condition.isAllData()) {
            return Optional.empty();
        }
        if (condition.isDenied()) {
            return Optional.of(Set.of());
        }
        if (condition.isSelfOnly() || condition.deptIds().isEmpty()) {
            return Optional.of(condition.userId() == null ? Set.of() : Set.of(condition.userId()));
        }
        // 这里已经按解析后的数据范围筛选部门，内部查询需要绕过拦截器，避免二次拼接数据范围。
        Set<Long> userIds = EasyDataScopeContext.ignore(() -> sysUserService.lambdaQuery()
                .select(SysUser::getUserId)
                .in(SysUser::getDeptId, condition.deptIds())
                .list()
                .stream()
                .map(SysUser::getUserId)
                .collect(Collectors.toCollection(HashSet::new)));
        if (condition.userId() != null) {
            userIds.add(condition.userId());
        }
        return Optional.of(userIds);
    }

    public boolean canReadAllUsers() {
        return dataScopeResolver.resolveCurrentUserScope().isAllData();
    }
}
