package com.laker.admin.module.workflow.event;

/**
 * 流程实例状态变更事件。
 * <p>
 * 工作流引擎只关心实例流转，业务模块通过该事件同步自己的业务状态，
 * 避免通用流程引擎直接依赖请假、报销等具体业务表。
 */
public record WfProcessInstanceStatusChangedEvent(
        Long instanceId,
        String businessType,
        String businessId,
        String status
) {
}
