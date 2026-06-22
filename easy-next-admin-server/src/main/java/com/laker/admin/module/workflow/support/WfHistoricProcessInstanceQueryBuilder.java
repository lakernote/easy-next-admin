package com.laker.admin.module.workflow.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.module.workflow.entity.WfHistoricProcessInstance;
import org.springframework.util.StringUtils;

public final class WfHistoricProcessInstanceQueryBuilder {

    private WfHistoricProcessInstanceQueryBuilder() {
    }

    public static LambdaQueryWrapper<WfHistoricProcessInstance> build(AuthPrincipal principal,
                                                                      boolean mine,
                                                                      boolean manage,
                                                                      String status,
                                                                      String businessType,
                                                                      String keyword) {
        LambdaQueryWrapper<WfHistoricProcessInstance> queryWrapper = Wrappers.lambdaQuery(WfHistoricProcessInstance.class);
        if (StringUtils.hasText(status)) {
            queryWrapper.eq(WfHistoricProcessInstance::getStatus, status);
        }
        if (StringUtils.hasText(businessType)) {
            queryWrapper.eq(WfHistoricProcessInstance::getBusinessType, businessType);
        }
        if (StringUtils.hasText(keyword)) {
            queryWrapper.and(wrapper -> wrapper.like(WfHistoricProcessInstance::getTitle, keyword)
                    .or()
                    .like(WfHistoricProcessInstance::getBusinessId, keyword));
        }
        applyVisibility(queryWrapper, principal, mine, manage);
        queryWrapper.orderByDesc(WfHistoricProcessInstance::getStartedAt);
        return queryWrapper;
    }

    private static void applyVisibility(LambdaQueryWrapper<WfHistoricProcessInstance> queryWrapper,
                                        AuthPrincipal principal,
                                        boolean mine,
                                        boolean manage) {
        if (principal == null || principal.getUserId() == null) {
            queryWrapper.apply("1 = 0");
            return;
        }

        Long userId = principal.getUserId();
        if (mine) {
            queryWrapper.eq(WfHistoricProcessInstance::getInitiatorId, userId);
            return;
        }

        if (principal.isSuperAdmin() || manage) {
            return;
        }

        queryWrapper.and(wrapper -> wrapper.eq(WfHistoricProcessInstance::getInitiatorId, userId)
                .or()
                .inSql(WfHistoricProcessInstance::getId, "SELECT instance_id FROM wf_hi_task WHERE assignee_id = " + userId)
                .or()
                .inSql(WfHistoricProcessInstance::getId, "SELECT instance_id FROM wf_hi_cc WHERE receiver_id = " + userId));
    }
}
