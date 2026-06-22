package com.laker.admin.module.schedule.core;

import com.laker.admin.module.schedule.enums.JobStateEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class ScheduleJobDefinition {
    /**
     * 任务编码，作为调度、权限和日志关联的稳定业务键。
     */
    private String jobCode;
    /**
     * 任务名称，展示给研发和运维人员。
     */
    private String jobName;
    /**
     * Spring Bean 真实类名，用于从容器中定位执行器。
     */
    private String jobClassName;
    /**
     * Cron 表达式，最终以数据库配置为准。
     */
    private String cronExpression;

    /**
     * 当前运行态。START 表示启动时自动注册，STOP 表示只登记不运行。
     */
    private JobStateEnum jobState;
    /**
     * 是否允许被调度。关闭后即使状态为 START 也不注册。
     */
    private Boolean enable;

    public ScheduleJobDefinition() {

    }

}
