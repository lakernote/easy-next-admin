package com.laker.admin.module.assistant.tool;

public enum AssistantToolRunStatus {
    SKIPPED,
    EXECUTED,
    /** 多个独立 Tool 中至少一个成功、至少一个未成功。 */
    PARTIAL_SUCCESS,
    /**
     * 写请求曾经进入执行边界，但当前无法证明上一次是成功还是失败，需要查询业务状态或人工核对。
     */
    INDETERMINATE,
    FAILED
}
