package com.laker.admin.module.assistant.state;

/** 一次 Agent Run 对 Pending Action 的显式状态更新。 */
public enum AssistantPendingActionUpdateType {
    /** 本轮没有改变待处理动作，保留进入本轮时的状态。 */
    UNCHANGED,
    /** 新建或替换待补字段、待确认的动作草稿。 */
    UPSERT,
    /** 动作已经确认完成或被用户取消，清除原草稿。 */
    CLEAR
}
