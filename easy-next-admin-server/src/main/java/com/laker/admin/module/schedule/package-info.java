/**
 * 任务调度模块。
 *
 * <p>负责动态定时任务、执行日志和运行监控。表名前缀保持 {@code schedule_}，
 * 后续接入工作流或告警时通过清晰模块边界集成，不与系统权限表混放业务语义。</p>
 */
package com.laker.admin.module.schedule;
