package com.laker.admin.module.schedule.core;

import com.laker.admin.module.schedule.enums.JobExecutionMode;
import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * 声明一个 EasyNextAdmin 动态定时任务。
 *
 * <p>默认使用集群单实例执行；固定账期批处理可以使用广播模式唤醒所有实例，
 * 数据分工仍由 Easy Batch 的 Claim/Lease 完成。</p>
 *
 * @author easynext
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface EasyJob {

    /**
     * 任务编码，必须唯一，用于标识每个任务
     */
    String jobCode();

    /**
     * 任务名称，用于描述任务的功能
     */
    String jobName();

    @AliasFor(annotation = Component.class) String value() default "";

    /**
     * cron表达式，默认""，代表不使用 cron 表达式进行任务调度
     */
    String cron() default "";

    /**
     * 分布式执行锁租约秒数。锁实现会自动续约，租约用于进程崩溃后的兜底释放。
     */
    long lockLeaseSeconds() default 300;

    /**
     * 集群执行方式。广播模式不会获取 Job 全局锁。
     */
    JobExecutionMode executionMode() default JobExecutionMode.SINGLETON;

}
