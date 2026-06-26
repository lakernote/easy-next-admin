/**
 * 轻量批处理治理模块。
 *
 * <p>这里不替代动态定时任务。Job 负责触发，BatchTask 负责批量执行过程中的进度、取消和失败明细治理。</p>
 */
package com.laker.admin.module.batch;
