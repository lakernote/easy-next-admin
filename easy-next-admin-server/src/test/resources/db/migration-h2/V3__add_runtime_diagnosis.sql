-- H2 集成测试库同步运行诊断菜单和内置授权。
INSERT INTO sys_menu (id, pid, title, icon, href, sort, enable, remark, create_time, update_by, update_time, deleted, version, type, permission_code, component_path, visible, create_by, create_dept_id)
SELECT 202610140104000205, 202604280104000200, '运行诊断', 'DataAnalysis', '/monitor/diagnosis', 45, 1,
       '查看 Metrics、Events、Slow、Errors 和可关联的 Trace 调用树。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1,
       'monitor:diagnosis:view', '@/views/monitor/RuntimeDiagnosisView.vue', 1, 202604280101000001, 202604280103000001
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 202610140104000205);

INSERT INTO sys_role_permission (id, role_id, permission_resource_id, create_by, create_dept_id, create_time, update_by, update_time, deleted, version, remark)
SELECT 202610140106000000 + ROW_NUMBER() OVER (ORDER BY role_table.id), role_table.id, menu.id,
       202604280101000001, 202604280103000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, '运行诊断内置授权'
FROM sys_role role_table
JOIN sys_menu menu ON menu.permission_code = 'monitor:diagnosis:view' AND menu.deleted = 0
WHERE role_table.role_code IN ('admin', 'ops') AND role_table.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp
                  WHERE rp.role_id = role_table.id AND rp.permission_resource_id = menu.id AND rp.deleted = 0);
