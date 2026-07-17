package com.laker.admin.module.workflow.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import org.springframework.util.StringUtils;

/**
 * 流程实例查询边界。
 * 将“我发起的”和“与我相关的”两种口径显式拆开，避免前端再靠本地过滤猜测数据范围。
 */
public final class WfProcessInstanceQueryBuilder {

    private WfProcessInstanceQueryBuilder() {
    }

    public static LambdaQueryWrapper<WfProcessInstance> build(AuthPrincipal principal,
                                                              boolean mine,
                                                              boolean manage,
                                                              String status,
                                                              String businessType,
                                                              String keyword) {
        LambdaQueryWrapper<WfProcessInstance> queryWrapper = Wrappers.lambdaQuery(WfProcessInstance.class);
        if (StringUtils.hasText(status)) {
            queryWrapper.eq(WfProcessInstance::getStatus, status);
        }
        if (StringUtils.hasText(businessType)) {
            queryWrapper.eq(WfProcessInstance::getBusinessType, businessType);
        }
        if (StringUtils.hasText(keyword)) {
            queryWrapper.and(wrapper -> wrapper.like(WfProcessInstance::getTitle, keyword)
                    .or()
                    .like(WfProcessInstance::getBusinessId, keyword));
        }
        applyVisibility(queryWrapper, principal, mine, manage);
        queryWrapper.orderByDesc(WfProcessInstance::getStartedAt);
        return queryWrapper;
    }

    private static void applyVisibility(LambdaQueryWrapper<WfProcessInstance> queryWrapper,
                                        AuthPrincipal principal,
                                        boolean mine,
                                        boolean manage) {
        if (principal == null || principal.getUserId() == null) {
            queryWrapper.apply("1 = 0");
            return;
        }

        Long userId = principal.getUserId();
        if (mine) {
            queryWrapper.eq(WfProcessInstance::getInitiatorId, userId);
            return;
        }

        if (principal.isSuperAdmin() || manage) {
            return;
        }

        // 默认“我的流程”相关视图：发起人、处理人、抄送人均可见。
        queryWrapper.and(wrapper -> wrapper.eq(WfProcessInstance::getInitiatorId, userId)
                .or()
                .inSql(WfProcessInstance::getId, "SELECT instance_id FROM wf_ru_task WHERE assignee_id = " + userId)
                .or()
                .inSql(WfProcessInstance::getId, "SELECT instance_id FROM wf_hi_task WHERE assignee_id = " + userId)
                .or()
                .inSql(WfProcessInstance::getId, "SELECT instance_id FROM wf_ru_cc WHERE receiver_id = " + userId)
                .or()
                .inSql(WfProcessInstance::getId, "SELECT instance_id FROM wf_hi_cc WHERE receiver_id = " + userId));
    }
}
