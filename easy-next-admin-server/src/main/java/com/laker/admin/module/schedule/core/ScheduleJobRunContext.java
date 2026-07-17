package com.laker.admin.module.schedule.core;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单次定时任务触发上下文。
 *
 * <p>上下文只在执行线程内有效，日志回调从这里补齐 runId、实例 ID 和锁令牌。</p>
 */
@Data
@Builder
public class ScheduleJobRunContext {
    private static final ThreadLocal<ScheduleJobRunContext> HOLDER = new ThreadLocal<>();

    private String runId;
    private String instanceId;
    private String lockToken;
    private LocalDateTime triggerTime;

    public static void set(ScheduleJobRunContext context) {
        HOLDER.set(context);
    }

    public static ScheduleJobRunContext current() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
