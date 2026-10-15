-- H2 集成测试库同步运行诊断精简后的菜单说明。
UPDATE sys_menu
SET remark = '查看 Metrics、Slow、Errors，并从慢调用或错误打开关联 Trace。',
    update_time = CURRENT_TIMESTAMP
WHERE id = 202610140104000205 AND deleted = 0;
