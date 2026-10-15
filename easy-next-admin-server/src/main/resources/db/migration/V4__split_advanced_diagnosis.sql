-- 将按需 JFR/JVM 现场从日常运行诊断中拆为独立高级诊断入口。
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`)
SELECT 202610140104000208, 202604280104000200, '高级诊断', 'Aim', '/monitor/advanced-diagnosis', 46, 1,
       '按需开启 JFR，并查看 JVM 线程、死锁、运行池、类直方图和录制分析。', NOW(), 202604280101000001, NOW(), 0, 0, 1,
       'monitor:diagnosis:advanced-view', '@/views/monitor/AdvancedDiagnosisView.vue', 1, 202604280101000001, 202604280103000001
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `id` = 202610140104000208);

INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`)
SELECT 202610140104000206, 202610140104000208, '抓取 JVM 现场', '', 'POST /api/monitor/diagnosis/heap-histogram', 11, 1,
       '允许人工触发有安全点停顿风险的 JVM 类直方图抓取。', NOW(), 202604280101000001, NOW(), 0, 0, 2,
       'monitor:diagnosis:capture', NULL, 0, 202604280101000001, 202604280103000001
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `id` = 202610140104000206);

INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`)
SELECT 202610140104000207, 202610140104000208, '下载 JFR 原始文件', '', 'GET /api/monitor/diagnosis/jfr/files/{fileName}', 12, 1,
       '允许下载可能包含类名、线程栈和运行细节的 JFR 原始分段。', NOW(), 202604280101000001, NOW(), 0, 0, 2,
       'monitor:diagnosis:jfr-download', NULL, 0, 202604280101000001, 202604280103000001
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `id` = 202610140104000207);

UPDATE `sys_menu`
SET `pid` = 202610140104000208, `update_time` = NOW()
WHERE `id` IN (202610140104000206, 202610140104000207) AND `deleted` = 0;

INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`)
SELECT 202610140104000209, 202610140104000208, '控制 JFR 录制', '', 'POST /api/monitor/diagnosis/jfr/recording/*', 13, 1,
       '允许开启或停止最长 24 小时的本地 JFR 高级诊断录制。', NOW(), 202604280101000001, NOW(), 0, 0, 2,
       'monitor:diagnosis:jfr-control', NULL, 0, 202604280101000001, 202604280103000001
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `id` = 202610140104000209);

INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_resource_id`, `create_by`, `create_dept_id`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `remark`)
SELECT 202610140106001000 + ROW_NUMBER() OVER (ORDER BY role_table.`id`, menu.`id`), role_table.`id`, menu.`id`,
       202604280101000001, 202604280103000001, NOW(), 202604280101000001, NOW(), 0, 0, '高级诊断内置授权'
FROM `sys_role` role_table
JOIN `sys_menu` menu ON menu.`permission_code` IN ('monitor:diagnosis:capture', 'monitor:diagnosis:jfr-download', 'monitor:diagnosis:advanced-view', 'monitor:diagnosis:jfr-control') AND menu.`deleted` = 0
WHERE role_table.`role_code` IN ('admin', 'ops') AND role_table.`deleted` = 0
  AND NOT EXISTS (SELECT 1 FROM `sys_role_permission` rp
                  WHERE rp.`role_id` = role_table.`id` AND rp.`permission_resource_id` = menu.`id` AND rp.`deleted` = 0);
