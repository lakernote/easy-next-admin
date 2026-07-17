package com.laker.admin.module.workflow.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.module.workflow.entity.WfCc;
import com.laker.admin.module.workflow.entity.WfHistoricCc;

public final class WfCcQueryBuilder {

    private WfCcQueryBuilder() {
    }

    public static LambdaQueryWrapper<WfCc> build(AuthPrincipal principal, Integer readStatus, boolean mine) {
        LambdaQueryWrapper<WfCc> queryWrapper = Wrappers.lambdaQuery(WfCc.class);
        if (readStatus != null) {
            queryWrapper.eq(WfCc::getReadStatus, readStatus);
        }
        applyRuntimeVisibility(queryWrapper, principal, mine);
        return queryWrapper.orderByDesc(WfCc::getCreatedAt);
    }

    public static LambdaQueryWrapper<WfHistoricCc> buildHistoric(AuthPrincipal principal, Integer readStatus, boolean mine) {
        LambdaQueryWrapper<WfHistoricCc> queryWrapper = Wrappers.lambdaQuery(WfHistoricCc.class);
        if (readStatus != null) {
            queryWrapper.eq(WfHistoricCc::getReadStatus, readStatus);
        }
        applyHistoricVisibility(queryWrapper, principal, mine);
        return queryWrapper.orderByDesc(WfHistoricCc::getCreatedAt);
    }

    private static void applyRuntimeVisibility(LambdaQueryWrapper<WfCc> queryWrapper,
                                               AuthPrincipal principal,
                                               boolean mine) {
        if (principal == null || principal.getUserId() == null) {
            queryWrapper.apply("1 = 0");
            return;
        }
        if (mine || !principal.isSuperAdmin()) {
            queryWrapper.eq(WfCc::getReceiverId, principal.getUserId());
        }
    }

    private static void applyHistoricVisibility(LambdaQueryWrapper<WfHistoricCc> queryWrapper,
                                                AuthPrincipal principal,
                                                boolean mine) {
        if (principal == null || principal.getUserId() == null) {
            queryWrapper.apply("1 = 0");
            return;
        }
        if (mine || !principal.isSuperAdmin()) {
            queryWrapper.eq(WfHistoricCc::getReceiverId, principal.getUserId());
        }
    }
}
