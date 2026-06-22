package com.laker.admin.module.workflow.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.module.workflow.entity.WfHistoricTask;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collection;

public final class WfHistoricTaskQueryBuilder {

    private WfHistoricTaskQueryBuilder() {
    }

    public static LambdaQueryWrapper<WfHistoricTask> build(AuthPrincipal principal,
                                                           String status,
                                                           Collection<String> statuses,
                                                           Long instanceId,
                                                           Long assigneeId,
                                                           boolean mine) {
        LambdaQueryWrapper<WfHistoricTask> queryWrapper = Wrappers.lambdaQuery(WfHistoricTask.class);
        if (StringUtils.hasText(status)) {
            queryWrapper.eq(WfHistoricTask::getStatus, status);
        } else if (!CollectionUtils.isEmpty(statuses)) {
            queryWrapper.in(WfHistoricTask::getStatus, statuses);
        }
        if (instanceId != null) {
            queryWrapper.eq(WfHistoricTask::getInstanceId, instanceId);
        }
        if (assigneeId != null) {
            queryWrapper.eq(WfHistoricTask::getAssigneeId, assigneeId);
        }
        applyVisibility(queryWrapper, principal, mine);
        queryWrapper.orderByDesc(WfHistoricTask::getStartedAt);
        return queryWrapper;
    }

    private static void applyVisibility(LambdaQueryWrapper<WfHistoricTask> queryWrapper,
                                        AuthPrincipal principal,
                                        boolean mine) {
        if (principal == null || principal.getUserId() == null) {
            queryWrapper.apply("1 = 0");
            return;
        }

        Long userId = principal.getUserId();
        if (mine) {
            queryWrapper.eq(WfHistoricTask::getAssigneeId, userId);
        }

        if (principal.isSuperAdmin()) {
            return;
        }

        queryWrapper.and(wrapper -> wrapper.eq(WfHistoricTask::getAssigneeId, userId)
                .or()
                .inSql(WfHistoricTask::getInstanceId, "SELECT id FROM wf_hi_process_instance WHERE initiator_id = " + userId)
                .or()
                .inSql(WfHistoricTask::getInstanceId, "SELECT instance_id FROM wf_hi_cc WHERE receiver_id = " + userId));
    }
}
