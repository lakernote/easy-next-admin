/**
 * 行为审计模块。
 *
 * <p>负责沉淀接口访问、关键操作和安全相关事件。写入由基础设施切面统一完成，
 * 查询与统计接口在本模块暴露，表名前缀保持 {@code audit_}。</p>
 */
package com.laker.admin.module.audit;
