-- 运行诊断精简为 Metrics、Slow、Errors 三个日常视图，Trace 从问题记录直接打开。
UPDATE `sys_menu`
SET `remark` = '查看 Metrics、Slow、Errors，并从慢调用或错误打开关联 Trace。',
    `update_time` = NOW()
WHERE `id` = 202610140104000205 AND `deleted` = 0;
