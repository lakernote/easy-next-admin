/**
 * 轻量批处理治理模块。
 *
 * <p>这里不替代动态定时任务，也不替代业务表状态。Job 负责触发，业务表保存领域事实，
 * BatchTask 只负责批量执行过程中的进度、item 级断点续跑、取消、失败策略和失败明细治理。
 * {@code runner} 包提供轻量 PageReader / Processor / Runner 编排；固定账期任务还可以通过 Claim/Lease
 * 让广播唤醒的多个实例动态领取业务分区。</p>
 */
package com.laker.admin.module.batch;
