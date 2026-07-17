package com.laker.admin.module.schedule.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 定时任务在集群中的执行方式。
 */
@Getter
public enum JobExecutionMode {
    /** 集群内只有抢到分布式锁的实例执行。 */
    SINGLETON("SINGLETON"),
    /** 每个在线实例都执行，适合唤醒各实例共同领取批处理分区。 */
    BROADCAST("BROADCAST");

    @EnumValue
    private final String code;

    JobExecutionMode(String code) {
        this.code = code;
    }

    public static JobExecutionMode normalize(JobExecutionMode mode) {
        return mode == null ? SINGLETON : mode;
    }
}
