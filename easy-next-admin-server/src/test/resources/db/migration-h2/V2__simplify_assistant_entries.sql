-- 与 MySQL 升级迁移保持一致，确保已有菜单数据也只保留两个助手入口。
UPDATE sys_menu
SET remark = '仅保留面向员工的流式智能对话和面向管理员的同步 Agent 调试。',
    update_time = CURRENT_TIMESTAMP
WHERE id = 202607070104000001;

UPDATE sys_menu
SET title = '发送流式对话',
    href = 'POST /api/assistant/chat/messages/stream',
    update_time = CURRENT_TIMESTAMP
WHERE id = 202607070104000007;

DELETE FROM sys_menu
WHERE id IN (202607070104000005, 202607070104000004);
