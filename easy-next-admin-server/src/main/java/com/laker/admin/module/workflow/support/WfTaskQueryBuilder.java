package com.laker.admin.module.workflow.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.module.workflow.entity.WfTask;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collection;

/**
 * 流程任务查询边界。
 * 已办任务需要服务端按多个终态分页，避免前端先取固定数量再本地过滤造成截断。
 */
public final class WfTaskQueryBuilder {

    private WfTaskQueryBuilder() {
    }

    public static LambdaQueryWrapper<WfTask> build(AuthPrincipal principal,
                                                   String status,
                                                   Collection<String> statuses,
                                                   Long instanceId,
                                                   Long assigneeId,
                                                   boolean mine) {
        LambdaQueryWrapper<WfTask> queryWrapper = Wrappers.lambdaQuery(WfTask.class);
        if (StringUtils.hasText(status)) {
            queryWrapper.eq(WfTask::getStatus, status);
        } else if (!CollectionUtils.isEmpty(statuses)) {
            queryWrapper.in(WfTask::getStatus, statuses);
        }
        if (instanceId != null) {
            queryWrapper.eq(WfTask::getInstanceId, instanceId);
        }
        if (assigneeId != null) {
            queryWrapper.eq(WfTask::getAssigneeId, assigneeId);
        }
        applyVisibility(queryWrapper, principal, mine);
        queryWrapper.orderByDesc(WfTask::getStartedAt);
        return queryWrapper;
    }

    private static void applyVisibility(LambdaQueryWrapper<WfTask> queryWrapper,
                                        AuthPrincipal principal,
                                        boolean mine) {
        if (principal == null || principal.getUserId() == null) {
            queryWrapper.apply("1 = 0");
            return;
        }

        Long userId = principal.getUserId();
        if (mine) {
            queryWrapper.eq(WfTask::getAssigneeId, userId);
        }

        if (principal.isSuperAdmin()) {
            return;
        }

        queryWrapper.and(wrapper -> wrapper.eq(WfTask::getAssigneeId, userId)
                .or()
                .inSql(WfTask::getInstanceId, "SELECT id FROM wf_ru_process_instance WHERE initiator_id = " + userId)
                .or()
                .inSql(WfTask::getInstanceId, "SELECT instance_id FROM wf_ru_cc WHERE receiver_id = " + userId));
    }
}
