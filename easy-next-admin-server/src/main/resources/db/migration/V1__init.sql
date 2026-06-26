-- EasyNextAdmin 首个公开版本初始化脚本。
-- 约定：由 Flyway 执行，面向 MySQL 8.4；本地调试也可按 README 手动导入。
-- 说明：表和字段 COMMENT 用于帮助二开团队快速理解权限、组织、审计、流程和监控数据模型。
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `infra_distributed_lock` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增id',
  `lock_key` varchar(50) NOT NULL COMMENT '锁记录key',
  `token` varchar(50) NOT NULL COMMENT '锁的token，防止误删其他人的锁',
  `thread_id` varchar(50) NOT NULL COMMENT '获取锁的线程id',
  `expire` bigint NOT NULL COMMENT '锁的失效时间，时间戳',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_infra_distributed_lock_key` (`lock_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分布式锁';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_api_log` (
  `log_id` bigint unsigned NOT NULL COMMENT '日志ID',
  `user_id` bigint DEFAULT NULL COMMENT '用户id',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '链路号',
  `ip` varchar(20) DEFAULT NULL COMMENT 'ip地址',
  `city` varchar(255) DEFAULT NULL COMMENT '请求城市',
  `client` varchar(500) DEFAULT NULL COMMENT '浏览器或者app信息',
  `uri` varchar(255) DEFAULT NULL COMMENT '请求uri',
  `method` varchar(255) DEFAULT NULL COMMENT '请求方法',
  `request` varchar(500) DEFAULT NULL COMMENT '请求',
  `response` varchar(500) DEFAULT NULL COMMENT '响应',
  `status` tinyint DEFAULT NULL COMMENT '状态',
  `cost` int DEFAULT NULL COMMENT '耗时ms',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_dept_id` bigint DEFAULT NULL COMMENT '创建部门ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常，1删除',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`log_id`) USING BTREE,
  KEY `idx_audit_api_log_deleted` (`deleted`),
  KEY `idx_audit_api_log_user_time` (`user_id`,`create_time`),
  KEY `idx_audit_api_log_create_dept` (`create_dept_id`),
  KEY `idx_audit_api_log_trace` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='日志';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_login_log` (
  `id` bigint NOT NULL COMMENT '登录日志ID',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID',
  `user_name` varchar(80) DEFAULT NULL COMMENT '用户名',
  `login_result` varchar(20) NOT NULL COMMENT '登录结果：SUCCESS / FAIL',
  `fail_reason` varchar(255) DEFAULT NULL COMMENT '失败原因',
  `ip` varchar(64) DEFAULT NULL COMMENT 'IP',
  `user_agent` varchar(500) DEFAULT NULL COMMENT 'User-Agent',
  `client_type` varchar(40) DEFAULT NULL COMMENT '客户端类型',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '链路号',
  `login_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_audit_login_log_user_time` (`user_id`,`login_time`),
  KEY `idx_audit_login_log_time` (`login_time`),
  KEY `idx_audit_login_log_ip` (`ip`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录审计日志';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_operation_log` (
  `id` bigint NOT NULL COMMENT '操作日志ID',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '链路号',
  `module` varchar(80) DEFAULT NULL COMMENT '模块',
  `action` varchar(80) DEFAULT NULL COMMENT '操作',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(80) DEFAULT NULL COMMENT '操作人',
  `request_method` varchar(20) DEFAULT NULL COMMENT '请求方法',
  `request_uri` varchar(255) DEFAULT NULL COMMENT '请求地址',
  `request_params` text COMMENT '脱敏后的请求参数',
  `response_status` varchar(20) DEFAULT NULL COMMENT '响应状态',
  `error_message` varchar(1000) DEFAULT NULL COMMENT '错误信息',
  `ip` varchar(64) DEFAULT NULL COMMENT 'IP',
  `user_agent` varchar(500) DEFAULT NULL COMMENT 'User-Agent',
  `duration_ms` int DEFAULT NULL COMMENT '耗时',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_audit_operation_log_trace` (`trace_id`),
  KEY `idx_audit_operation_log_operator_time` (`operator_id`,`created_at`),
  KEY `idx_audit_operation_log_time` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务操作审计日志';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_data_change_log` (
  `id` bigint NOT NULL COMMENT '数据变更日志ID',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '链路号',
  `biz_type` varchar(80) DEFAULT NULL COMMENT '业务类型',
  `biz_id` varchar(80) DEFAULT NULL COMMENT '业务ID',
  `table_name` varchar(80) DEFAULT NULL COMMENT '表名',
  `change_type` varchar(20) NOT NULL COMMENT '变更类型：INSERT / UPDATE / DELETE',
  `before_json` json DEFAULT NULL COMMENT '变更前',
  `after_json` json DEFAULT NULL COMMENT '变更后',
  `changed_fields` varchar(1000) DEFAULT NULL COMMENT '变更字段',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_audit_data_change_log_biz` (`biz_type`,`biz_id`),
  KEY `idx_audit_data_change_log_table_time` (`table_name`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据变更审计日志';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_error_log` (
  `id` bigint NOT NULL COMMENT '异常日志ID',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '链路号',
  `request_uri` varchar(255) DEFAULT NULL COMMENT '请求地址',
  `request_method` varchar(20) DEFAULT NULL COMMENT '请求方法',
  `error_type` varchar(255) DEFAULT NULL COMMENT '异常类型',
  `error_message` varchar(1000) DEFAULT NULL COMMENT '异常消息',
  `stack_trace` mediumtext COMMENT '异常堆栈',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_audit_error_log_trace` (`trace_id`),
  KEY `idx_audit_error_log_time` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='异常审计日志';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_message` (
  `id` bigint NOT NULL COMMENT '站内消息ID',
  `receiver_id` bigint NOT NULL COMMENT '接收人ID',
  `sender_id` bigint DEFAULT NULL COMMENT '发送人ID，0为系统',
  `title` varchar(120) NOT NULL COMMENT '消息标题',
  `content` varchar(1000) DEFAULT NULL COMMENT '消息内容',
  `category` varchar(40) NOT NULL COMMENT '消息分类',
  `level` varchar(20) NOT NULL DEFAULT 'INFO' COMMENT '消息级别',
  `biz_type` varchar(80) DEFAULT NULL COMMENT '业务类型',
  `biz_id` varchar(100) DEFAULT NULL COMMENT '业务ID',
  `link` varchar(255) DEFAULT NULL COMMENT '跳转链接',
  `read_status` tinyint NOT NULL DEFAULT '0' COMMENT '0未读 1已读',
  `read_at` datetime DEFAULT NULL COMMENT '已读时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_message_receiver_read` (`receiver_id`,`read_status`,`created_at`),
  KEY `idx_user_message_category` (`category`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站内消息';
/*!40101 SET character_set_client = @saved_cs_client */;
CREATE TABLE `observability_remote_call_log` (
  `id` bigint unsigned NOT NULL COMMENT '远程调用日志ID',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '链路号',
  `target` varchar(160) DEFAULT NULL COMMENT '远程客户端',
  `method` varchar(160) DEFAULT NULL COMMENT '调用方法',
  `success` tinyint DEFAULT NULL COMMENT '是否成功',
  `duration_ms` bigint DEFAULT NULL COMMENT '耗时ms',
  `error_message` varchar(500) DEFAULT NULL COMMENT '异常摘要',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_dept_id` bigint DEFAULT NULL COMMENT '创建部门ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常，1删除',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_remote_call_time` (`create_time`),
  KEY `idx_remote_call_target_method_time` (`target`,`method`,`create_time`),
  KEY `idx_remote_call_trace` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='远程调用观测日志';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `infra_idempotent_record` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `key` varchar(255) NOT NULL COMMENT '幂等键',
  `expire_time` timestamp NOT NULL COMMENT '过期时间',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_key` (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='幂等记录';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `infra_local_message` (
  `id` bigint unsigned NOT NULL COMMENT '本地消息ID',
  `status` varchar(100) DEFAULT NULL COMMENT '消息状态：INIT / SUCCESS / FAIL 等',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常，1删除',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `retry_count` bigint DEFAULT NULL COMMENT '重试次数',
  `name` varchar(100) DEFAULT NULL COMMENT '消息处理器名称',
  `param` varchar(100) DEFAULT NULL COMMENT '处理参数摘要',
  `process_tag` varchar(64) DEFAULT NULL COMMENT '处理批次标识',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_dept_id` bigint DEFAULT NULL COMMENT '创建部门ID',
  PRIMARY KEY (`id`),
  KEY `idx_infra_local_message_status_deleted` (`status`,`deleted`),
  KEY `idx_infra_local_message_process_tag` (`process_tag`,`deleted`),
  KEY `idx_infra_local_message_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='本地消息表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dept` (
  `id` bigint unsigned NOT NULL COMMENT '部门ID',
  `dept_name` varchar(255) DEFAULT NULL,
  `full_name` varchar(200) DEFAULT NULL COMMENT '部门全称',
  `address` varchar(255) DEFAULT NULL,
  `pid` bigint NOT NULL DEFAULT '0',
  `tree_path` varchar(500) DEFAULT NULL COMMENT '部门树路径',
  `leader_user_id` bigint DEFAULT NULL COMMENT '负责人用户ID',
  `status` tinyint DEFAULT NULL,
  `sort` int DEFAULT NULL,
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_dept_id` bigint DEFAULT NULL COMMENT '创建部门ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常，1删除',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_sys_dept_deleted` (`deleted`),
  KEY `idx_sys_dept_pid_deleted` (`pid`,`deleted`),
  KEY `idx_sys_dept_create_dept` (`create_dept_id`),
  KEY `idx_sys_dept_leader` (`leader_user_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_file` (
  `id` bigint NOT NULL COMMENT '文件ID',
  `user_id` bigint DEFAULT NULL,
  `nick_name` varchar(50) DEFAULT NULL,
  `file_path` varchar(255) DEFAULT NULL COMMENT '文件路径',
  `file_name` varchar(255) DEFAULT NULL COMMENT '文件名称',
  `original_name` varchar(255) DEFAULT NULL COMMENT '原始文件名',
  `storage_name` varchar(255) DEFAULT NULL COMMENT '存储文件名',
  `storage_type` varchar(20) NOT NULL DEFAULT 'LOCAL' COMMENT '存储类型：LOCAL / OSS',
  `file_size` bigint NOT NULL DEFAULT '0' COMMENT '文件大小，单位字节',
  `content_type` varchar(120) DEFAULT NULL COMMENT 'MIME 类型',
  `business_type` varchar(80) DEFAULT NULL COMMENT '业务类型',
  `business_id` bigint DEFAULT NULL COMMENT '业务主键',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_dept_id` bigint DEFAULT NULL COMMENT '创建部门ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常，1删除',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_sys_file_deleted` (`deleted`),
  KEY `idx_sys_file_user_deleted` (`user_id`,`deleted`),
  KEY `idx_sys_file_create_dept` (`create_dept_id`),
  KEY `idx_sys_file_business` (`business_type`,`business_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_menu` (
  `id` bigint unsigned NOT NULL COMMENT '权限资源ID',
  `pid` bigint unsigned NOT NULL DEFAULT '0' COMMENT '父ID',
  `title` varchar(50) NOT NULL DEFAULT '' COMMENT '名称',
  `icon` varchar(100) NOT NULL DEFAULT '' COMMENT '菜单图标',
  `href` varchar(255) NOT NULL DEFAULT '' COMMENT '页面路由',
  `sort` int DEFAULT '0' COMMENT '菜单排序',
  `enable` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注信息',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常，1删除',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `type` int DEFAULT NULL COMMENT '权限类型0分组1页面2按钮',
  `permission_code` varchar(50) DEFAULT NULL COMMENT '菜单或按钮权限标识',
  `component_path` varchar(255) DEFAULT NULL COMMENT '前端组件路径',
  `visible` tinyint NOT NULL DEFAULT '1' COMMENT '侧边栏是否可见',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_dept_id` bigint DEFAULT NULL COMMENT '创建部门ID',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `title` (`title`) USING BTREE,
  KEY `href` (`href`) USING BTREE,
  KEY `idx_sys_menu_permission_code_deleted` (`permission_code`,`deleted`),
  KEY `idx_sys_menu_parent_type` (`pid`,`type`,`deleted`),
  KEY `idx_sys_menu_deleted` (`deleted`),
  KEY `idx_sys_menu_create_dept` (`create_dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统菜单权限资源表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `id` bigint unsigned NOT NULL COMMENT '角色ID',
  `role_name` varchar(255) DEFAULT NULL COMMENT '角色名',
  `role_code` varchar(255) DEFAULT NULL COMMENT 'Key值',
  `details` varchar(500) DEFAULT NULL COMMENT '描述',
  `enable` tinyint DEFAULT NULL COMMENT '是否可用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常，1删除',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `role_level` int NOT NULL DEFAULT '100' COMMENT '角色层级，数值越小权限越高',
  `data_scope` varchar(32) NOT NULL DEFAULT 'SELF' COMMENT '数据范围编码：ALL、DEPT_AND_CHILDREN、DEPT、SELF、DEPT_SETS',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_dept_id` bigint DEFAULT NULL COMMENT '创建部门ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_sys_role_code_deleted` (`role_code`,`deleted`),
  KEY `idx_sys_role_code_deleted` (`role_code`,`deleted`),
  KEY `idx_sys_role_enable_deleted` (`enable`,`deleted`),
  KEY `idx_sys_role_deleted` (`deleted`),
  KEY `idx_sys_role_create_dept` (`create_dept_id`),
  CONSTRAINT `chk_sys_role_data_scope` CHECK (`data_scope` IN ('ALL','DEPT_AND_CHILDREN','DEPT','SELF','DEPT_SETS'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_permission` (
  `id` bigint unsigned NOT NULL COMMENT '角色权限关系ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `permission_resource_id` bigint NOT NULL COMMENT '权限资源ID',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_dept_id` bigint DEFAULT NULL COMMENT '创建部门ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常，1删除',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_sys_role_permission_role_permission_resource` (`role_id`,`permission_resource_id`),
  KEY `idx_sys_role_permission_role` (`role_id`,`deleted`),
  KEY `idx_sys_role_permission_resource` (`permission_resource_id`,`deleted`),
  KEY `idx_sys_role_permission_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_dept` (
  `id` bigint unsigned NOT NULL COMMENT '角色自定义部门关系ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `dept_id` bigint NOT NULL COMMENT '部门ID',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_dept_id` bigint DEFAULT NULL COMMENT '创建部门ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常，1删除',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_sys_role_dept` (`role_id`,`dept_id`,`deleted`),
  KEY `idx_sys_role_dept_role` (`role_id`,`deleted`),
  KEY `idx_sys_role_dept_dept` (`dept_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色自定义部门数据范围';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schedule_job` (
  `job_id` bigint unsigned NOT NULL COMMENT '任务ID',
  `job_code` varchar(255) DEFAULT NULL COMMENT '任务的编码 必须全局唯一',
  `job_name` varchar(255) DEFAULT NULL COMMENT '任务的名称',
  `job_class_name` varchar(255) DEFAULT NULL COMMENT '任务的类名称',
  `cron_expression` varchar(255) DEFAULT NULL COMMENT '任务的cron表达式',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常，1删除',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `enable` tinyint DEFAULT NULL COMMENT '是否启用',
  `job_state` int DEFAULT NULL COMMENT '任务状态',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_dept_id` bigint DEFAULT NULL COMMENT '创建部门ID',
  PRIMARY KEY (`job_id`) USING BTREE,
  UNIQUE KEY `uk_schedule_job_code` (`job_code`) USING BTREE,
  KEY `idx_schedule_job_deleted` (`deleted`),
  KEY `idx_schedule_job_create_dept` (`create_dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schedule_job_log` (
  `job_log_id` bigint unsigned NOT NULL COMMENT '任务日志ID',
  `job_code` varchar(255) DEFAULT NULL COMMENT '任务编码',
  `start_time` datetime DEFAULT NULL COMMENT '任务开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '任务结束时间',
  `status` int DEFAULT NULL COMMENT '执行状态：1成功，2失败',
  `cost` int DEFAULT NULL COMMENT '耗时 ms',
  `thread_name` varchar(255) DEFAULT NULL COMMENT '线程名称',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_dept_id` bigint DEFAULT NULL COMMENT '创建部门ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常，1删除',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`job_log_id`) USING BTREE,
  KEY `idx_schedule_job_log_deleted` (`deleted`),
  KEY `idx_schedule_job_log_job_time` (`job_code`,`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint unsigned NOT NULL COMMENT '用户ID',
  `user_name` varchar(255) DEFAULT NULL,
  `password_hash` varchar(255) DEFAULT NULL COMMENT 'BCrypt 密码摘要',
  `nick_name` varchar(255) DEFAULT NULL,
  `dept_id` bigint DEFAULT NULL,
  `manager_user_id` bigint DEFAULT NULL COMMENT '直属上级用户ID',
  `phone` varchar(255) DEFAULT NULL,
  `enable` int DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `avatar` varchar(255) DEFAULT NULL,
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_dept_id` bigint DEFAULT NULL COMMENT '创建部门ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常，1删除',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `permission_version` bigint NOT NULL DEFAULT '1' COMMENT '权限版本号',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `employee_no` varchar(64) DEFAULT NULL COMMENT '员工编号',
  `real_name` varchar(64) DEFAULT NULL COMMENT '真实姓名',
  `position_name` varchar(100) DEFAULT NULL COMMENT '岗位名称',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_sys_user_username_deleted` (`user_name`,`deleted`),
  UNIQUE KEY `uk_sys_user_employee_deleted` (`employee_no`,`deleted`),
  KEY `idx_sys_user_deleted` (`deleted`),
  KEY `idx_sys_user_dept_deleted` (`dept_id`,`deleted`),
  KEY `idx_sys_user_manager_deleted` (`manager_user_id`,`deleted`),
  KEY `idx_sys_user_enable_deleted` (`enable`,`deleted`),
  KEY `idx_sys_user_create_by` (`create_by`),
  KEY `idx_sys_user_create_dept` (`create_dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `id` bigint unsigned NOT NULL COMMENT '用户角色关系ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_dept_id` bigint DEFAULT NULL COMMENT '创建部门ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常，1删除',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_sys_user_role_user_role` (`user_id`,`role_id`),
  KEY `idx_sys_user_role_user` (`user_id`,`deleted`),
  KEY `idx_sys_user_role_role` (`role_id`,`deleted`),
  KEY `idx_sys_user_role_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_process_definition` (
  `id` bigint unsigned NOT NULL COMMENT '流程定义ID',
  `process_key` varchar(100) NOT NULL COMMENT '流程编码',
  `process_name` varchar(100) NOT NULL COMMENT '流程名称',
  `current_version` int NOT NULL DEFAULT '0' COMMENT '当前版本',
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/ENABLED/DISABLED',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wf_process_definition_key` (`process_key`),
  KEY `idx_wf_process_definition_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流流程定义';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_process_definition_version` (
  `id` bigint unsigned NOT NULL COMMENT '流程定义版本ID',
  `definition_id` bigint unsigned NOT NULL COMMENT '流程定义ID',
  `version` int NOT NULL COMMENT '版本号',
  `graph_json` json NOT NULL COMMENT 'LogicFlow 画布数据',
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PUBLISHED/DISABLED',
  `published_by` bigint DEFAULT NULL COMMENT '发布人',
  `published_at` datetime DEFAULT NULL COMMENT '发布时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wf_definition_version` (`definition_id`,`version`),
  KEY `idx_wf_definition_version_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流流程定义版本';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_process_node` (
  `id` bigint unsigned NOT NULL COMMENT '流程节点ID',
  `version_id` bigint unsigned NOT NULL COMMENT '流程版本ID',
  `node_key` varchar(100) NOT NULL COMMENT '节点编码',
  `node_name` varchar(100) NOT NULL COMMENT '节点名称',
  `node_type` varchar(32) NOT NULL COMMENT 'START/SUBMIT/APPROVAL/CC/CONDITION/END',
  `approve_type` varchar(32) DEFAULT NULL COMMENT 'ANY_ONE/ALL/SEQUENTIAL',
  `approver_type` varchar(32) DEFAULT NULL COMMENT 'USER/ROLE/INITIATOR/INITIATOR_SELECTED/DEPT_LEADER/UPPER_DEPT_LEADER',
  `approver_value` varchar(500) DEFAULT NULL COMMENT '审批人配置',
  `allow_transfer` tinyint NOT NULL DEFAULT '1',
  `allow_delegate` tinyint NOT NULL DEFAULT '1',
  `allow_add_sign` tinyint NOT NULL DEFAULT '1',
  `allow_remove_sign` tinyint NOT NULL DEFAULT '0',
  `allow_return` tinyint NOT NULL DEFAULT '1',
  `sort_order` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wf_process_node_key` (`version_id`,`node_key`),
  KEY `idx_wf_process_node_version` (`version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流流程节点';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_process_transition` (
  `id` bigint unsigned NOT NULL COMMENT '流程连线ID',
  `version_id` bigint unsigned NOT NULL COMMENT '流程版本ID',
  `from_node_key` varchar(100) NOT NULL COMMENT '来源节点',
  `to_node_key` varchar(100) NOT NULL COMMENT '目标节点',
  `condition_type` varchar(32) NOT NULL DEFAULT 'ALWAYS' COMMENT 'ALWAYS/EXPRESSION',
  `condition_json` json DEFAULT NULL COMMENT '条件配置',
  `priority` int NOT NULL DEFAULT '0' COMMENT '优先级',
  PRIMARY KEY (`id`),
  KEY `idx_wf_transition_version_from` (`version_id`,`from_node_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流流程连线';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_ru_process_instance` (
  `id` bigint unsigned NOT NULL COMMENT '流程实例ID',
  `definition_id` bigint unsigned NOT NULL COMMENT '流程定义ID',
  `version_id` bigint unsigned NOT NULL COMMENT '流程版本ID',
  `process_key` varchar(100) NOT NULL COMMENT '流程编码',
  `business_type` varchar(100) NOT NULL COMMENT '业务类型',
  `business_id` varchar(100) NOT NULL COMMENT '业务ID',
  `title` varchar(200) NOT NULL COMMENT '流程标题',
  `initiator_id` bigint NOT NULL COMMENT '发起人',
  `current_node_key` varchar(100) DEFAULT NULL COMMENT '当前节点',
  `status` varchar(32) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/APPROVED/REJECTED/REVOKED/TERMINATED',
  `variables_json` json DEFAULT NULL COMMENT '流程运行变量快照',
  `definition_snapshot_json` json NOT NULL COMMENT '发起时定义快照',
  `started_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发起时间',
  `ended_at` datetime DEFAULT NULL COMMENT '结束时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_wf_instance_initiator_status` (`initiator_id`,`status`),
  KEY `idx_wf_instance_business` (`business_type`,`business_id`),
  KEY `idx_wf_instance_process` (`process_key`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流运行流程实例';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_hi_process_instance` (
  `id` bigint unsigned NOT NULL COMMENT '流程实例ID',
  `definition_id` bigint unsigned NOT NULL COMMENT '流程定义ID',
  `version_id` bigint unsigned NOT NULL COMMENT '流程版本ID',
  `process_key` varchar(100) NOT NULL COMMENT '流程编码',
  `business_type` varchar(100) NOT NULL COMMENT '业务类型',
  `business_id` varchar(100) NOT NULL COMMENT '业务ID',
  `title` varchar(200) NOT NULL COMMENT '流程标题',
  `initiator_id` bigint NOT NULL COMMENT '发起人',
  `current_node_key` varchar(100) DEFAULT NULL COMMENT '当前节点',
  `status` varchar(32) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/APPROVED/REJECTED/REVOKED/TERMINATED',
  `variables_json` json DEFAULT NULL COMMENT '流程运行变量快照',
  `definition_snapshot_json` json NOT NULL COMMENT '发起时定义快照',
  `started_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发起时间',
  `ended_at` datetime DEFAULT NULL COMMENT '结束时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '运行态归档版本',
  PRIMARY KEY (`id`),
  KEY `idx_wf_hi_instance_initiator_status` (`initiator_id`,`status`),
  KEY `idx_wf_hi_instance_business` (`business_type`,`business_id`),
  KEY `idx_wf_hi_instance_process` (`process_key`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流历史流程实例';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_ru_task` (
  `id` bigint unsigned NOT NULL COMMENT '审批任务ID',
  `instance_id` bigint unsigned NOT NULL COMMENT '流程实例ID',
  `node_key` varchar(100) NOT NULL COMMENT '节点编码',
  `node_name` varchar(100) NOT NULL COMMENT '节点名称',
  `assignee_id` bigint NOT NULL COMMENT '审批人',
  `assignee_dept_id` bigint DEFAULT NULL COMMENT '审批人所属部门',
  `assignment_rule_type` varchar(64) DEFAULT NULL COMMENT '派单规则类型',
  `assignment_rule_name` varchar(100) DEFAULT NULL COMMENT '派单规则名称',
  `assignment_resolve_path` varchar(500) DEFAULT NULL COMMENT '派单解析路径',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/TRANSFERRED/DELEGATED/CANCELED',
  `approve_comment` varchar(1000) DEFAULT NULL COMMENT '审批意见',
  `started_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `finished_at` datetime DEFAULT NULL COMMENT '完成时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_wf_task_assignee_status` (`assignee_id`,`status`),
  KEY `idx_wf_task_assignee_dept` (`assignee_dept_id`,`status`),
  KEY `idx_wf_task_instance` (`instance_id`,`node_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流运行审批任务';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_hi_task` (
  `id` bigint unsigned NOT NULL COMMENT '审批任务ID',
  `instance_id` bigint unsigned NOT NULL COMMENT '流程实例ID',
  `node_key` varchar(100) NOT NULL COMMENT '节点编码',
  `node_name` varchar(100) NOT NULL COMMENT '节点名称',
  `assignee_id` bigint NOT NULL COMMENT '审批人',
  `assignee_dept_id` bigint DEFAULT NULL COMMENT '审批人所属部门',
  `assignment_rule_type` varchar(64) DEFAULT NULL COMMENT '派单规则类型',
  `assignment_rule_name` varchar(100) DEFAULT NULL COMMENT '派单规则名称',
  `assignment_resolve_path` varchar(500) DEFAULT NULL COMMENT '派单解析路径',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/TRANSFERRED/DELEGATED/CANCELED',
  `approve_comment` varchar(1000) DEFAULT NULL COMMENT '审批意见',
  `started_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `finished_at` datetime DEFAULT NULL COMMENT '完成时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '运行态归档版本',
  PRIMARY KEY (`id`),
  KEY `idx_wf_hi_task_assignee_status` (`assignee_id`,`status`),
  KEY `idx_wf_hi_task_assignee_dept` (`assignee_dept_id`,`status`),
  KEY `idx_wf_hi_task_instance` (`instance_id`,`node_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流历史审批任务';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_hi_event` (
  `id` bigint unsigned NOT NULL COMMENT '流程事件ID',
  `instance_id` bigint unsigned NOT NULL COMMENT '流程实例ID',
  `task_id` bigint unsigned DEFAULT NULL COMMENT '任务ID',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人',
  `action` varchar(32) NOT NULL COMMENT 'SUBMIT/APPROVE/REJECT/RETURN/REVOKE/TRANSFER/DELEGATE/ADD_SIGN/REMOVE_SIGN/CC/REMIND/TERMINATE',
  `from_node_key` varchar(100) DEFAULT NULL COMMENT '来源节点',
  `to_node_key` varchar(100) DEFAULT NULL COMMENT '目标节点',
  `target_user_id` bigint DEFAULT NULL COMMENT '目标用户',
  `comment` varchar(1000) DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_wf_event_instance` (`instance_id`,`created_at`),
  KEY `idx_wf_event_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流历史流转事件';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_ru_cc` (
  `id` bigint unsigned NOT NULL COMMENT '抄送ID',
  `instance_id` bigint unsigned NOT NULL COMMENT '流程实例ID',
  `node_key` varchar(100) DEFAULT NULL COMMENT '节点编码',
  `node_name` varchar(120) DEFAULT NULL COMMENT '节点名称',
  `receiver_id` bigint NOT NULL COMMENT '接收人',
  `read_status` tinyint NOT NULL DEFAULT '0' COMMENT '读取状态',
  `read_at` datetime DEFAULT NULL COMMENT '读取时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_wf_cc_receiver` (`receiver_id`,`read_status`),
  KEY `idx_wf_cc_instance` (`instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流运行抄送';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_hi_cc` (
  `id` bigint unsigned NOT NULL COMMENT '抄送ID',
  `instance_id` bigint unsigned NOT NULL COMMENT '流程实例ID',
  `node_key` varchar(100) DEFAULT NULL COMMENT '节点编码',
  `node_name` varchar(120) DEFAULT NULL COMMENT '节点名称',
  `receiver_id` bigint NOT NULL COMMENT '接收人',
  `read_status` tinyint NOT NULL DEFAULT '0' COMMENT '读取状态',
  `read_at` datetime DEFAULT NULL COMMENT '读取时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_wf_hi_cc_receiver` (`receiver_id`,`read_status`),
  KEY `idx_wf_hi_cc_instance` (`instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流历史抄送';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_task_delegation` (
  `id` bigint unsigned NOT NULL COMMENT '任务转办委派ID',
  `task_id` bigint unsigned NOT NULL COMMENT '任务ID',
  `from_user_id` bigint NOT NULL COMMENT '来源用户',
  `to_user_id` bigint NOT NULL COMMENT '目标用户',
  `delegation_type` varchar(32) NOT NULL COMMENT 'TRANSFER/DELEGATE/ADD_SIGN',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_wf_task_delegation_task` (`task_id`),
  KEY `idx_wf_task_delegation_to_user` (`to_user_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流转办委派';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_number_rule` (
  `id` bigint unsigned NOT NULL COMMENT '业务编号规则ID',
  `rule_code` varchar(64) NOT NULL COMMENT '规则编码，业务代码按此取号',
  `rule_name` varchar(100) NOT NULL COMMENT '规则名称',
  `prefix` varchar(16) NOT NULL COMMENT '编号前缀',
  `date_pattern` varchar(16) NOT NULL DEFAULT 'yyyyMMdd' COMMENT '日期段规则：yyyyMMdd/yyyyMM/yyyy/NONE',
  `number_separator` varchar(4) NOT NULL DEFAULT '-' COMMENT '编号分隔符',
  `sequence_width` int NOT NULL DEFAULT '6' COMMENT '流水位数',
  `sequence_step` int NOT NULL DEFAULT '1' COMMENT '递增步长',
  `initial_value` bigint unsigned NOT NULL DEFAULT '0' COMMENT '新周期初始当前值',
  `enable` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '是否启用',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_dept_id` bigint DEFAULT NULL COMMENT '创建部门ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常，1删除',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_biz_number_rule_code_deleted` (`rule_code`,`deleted`),
  KEY `idx_biz_number_rule_enable_deleted` (`enable`,`deleted`),
  KEY `idx_biz_number_rule_create_dept` (`create_dept_id`),
  CONSTRAINT `chk_biz_number_rule_date_pattern` CHECK (`date_pattern` IN ('yyyyMMdd','yyyyMM','yyyy','NONE')),
  CONSTRAINT `chk_biz_number_rule_width` CHECK (`sequence_width` BETWEEN 1 AND 12),
  CONSTRAINT `chk_biz_number_rule_step` CHECK (`sequence_step` BETWEEN 1 AND 1000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务编号规则';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_number_sequence` (
  `sequence_key` varchar(128) NOT NULL COMMENT '计数器键，格式：规则编码:日期段',
  `rule_code` varchar(64) NOT NULL COMMENT '规则编码',
  `segment` varchar(32) NOT NULL COMMENT '日期段或 NONE',
  `current_value` bigint unsigned NOT NULL DEFAULT '0' COMMENT '当前流水值',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`sequence_key`),
  KEY `idx_biz_number_sequence_rule_segment` (`rule_code`,`segment`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务编号计数器';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `batch_task` (
  `id` bigint unsigned NOT NULL COMMENT '批处理任务ID',
  `task_type` varchar(64) NOT NULL COMMENT '任务类型，例如 USER_IMPORT、REPORT_GENERATE',
  `task_name` varchar(120) NOT NULL COMMENT '任务名称',
  `business_key` varchar(128) DEFAULT NULL COMMENT '业务幂等键，同一类型下唯一',
  `trigger_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '触发类型：MANUAL/API/JOB/MESSAGE/SYSTEM',
  `trigger_ref_id` varchar(128) DEFAULT NULL COMMENT '触发来源ID，例如 job_log_id、上传文件ID',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态',
  `total_count` int NOT NULL DEFAULT '0' COMMENT '总数',
  `success_count` int NOT NULL DEFAULT '0' COMMENT '成功数',
  `failed_count` int NOT NULL DEFAULT '0' COMMENT '失败数',
  `skipped_count` int NOT NULL DEFAULT '0' COMMENT '跳过数',
  `progress_percent` int NOT NULL DEFAULT '0' COMMENT '进度百分比 0-100',
  `cancel_requested` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '是否请求取消',
  `started_at` datetime DEFAULT NULL COMMENT '开始时间',
  `finished_at` datetime DEFAULT NULL COMMENT '结束时间',
  `trace_id` varchar(128) DEFAULT NULL COMMENT '关联 traceId',
  `error_message` varchar(1000) DEFAULT NULL COMMENT '错误或取消原因',
  `result_message` varchar(1000) DEFAULT NULL COMMENT '结果说明',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_dept_id` bigint DEFAULT NULL COMMENT '创建部门ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常，1删除',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_batch_task_type_business_deleted` (`task_type`,`business_key`,`deleted`),
  KEY `idx_batch_task_status_time` (`status`,`create_time`),
  KEY `idx_batch_task_trigger` (`trigger_type`,`trigger_ref_id`),
  KEY `idx_batch_task_create_dept` (`create_dept_id`),
  CONSTRAINT `chk_batch_task_status` CHECK (`status` IN ('PENDING','RUNNING','SUCCESS','PARTIAL_SUCCESS','FAILED','CANCELING','CANCELED')),
  CONSTRAINT `chk_batch_task_progress` CHECK (`progress_percent` BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='批处理任务';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `batch_task_item` (
  `id` bigint unsigned NOT NULL COMMENT '批处理明细ID',
  `task_id` bigint unsigned NOT NULL COMMENT '批处理任务ID',
  `item_key` varchar(128) NOT NULL COMMENT '明细业务键，同一任务内唯一',
  `item_name` varchar(200) DEFAULT NULL COMMENT '明细名称',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '明细状态',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '重试次数',
  `payload` text DEFAULT NULL COMMENT '明细输入快照',
  `error_message` varchar(1000) DEFAULT NULL COMMENT '失败原因',
  `result_message` varchar(1000) DEFAULT NULL COMMENT '结果说明',
  `started_at` datetime DEFAULT NULL COMMENT '开始时间',
  `finished_at` datetime DEFAULT NULL COMMENT '结束时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_dept_id` bigint DEFAULT NULL COMMENT '创建部门ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常，1删除',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_batch_task_item_key_deleted` (`task_id`,`item_key`,`deleted`),
  KEY `idx_batch_task_item_status` (`task_id`,`status`),
  KEY `idx_batch_task_item_create_dept` (`create_dept_id`),
  CONSTRAINT `chk_batch_task_item_status` CHECK (`status` IN ('PENDING','RUNNING','SUCCESS','FAILED','SKIPPED','RETRYING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='批处理任务明细';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_leave_request` (
  `id` bigint unsigned NOT NULL COMMENT '请假申请ID',
  `request_no` varchar(64) NOT NULL COMMENT '申请单号',
  `applicant_id` bigint NOT NULL COMMENT '申请人',
  `applicant_dept_id` bigint DEFAULT NULL COMMENT '申请人部门',
  `leave_type` varchar(32) NOT NULL COMMENT '请假类型',
  `start_time` datetime NOT NULL COMMENT '开始时间',
  `end_time` datetime NOT NULL COMMENT '结束时间',
  `days` decimal(6,2) NOT NULL COMMENT '请假天数',
  `reason` varchar(500) NOT NULL COMMENT '请假事由',
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVING/APPROVED/REJECTED/REVOKED/TERMINATED',
  `workflow_instance_id` bigint unsigned DEFAULT NULL COMMENT '流程实例ID',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_leave_request_no` (`request_no`),
  KEY `idx_biz_leave_applicant_status` (`applicant_id`,`status`),
  KEY `idx_biz_leave_workflow_instance` (`workflow_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务请假申请';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_purchase_request` (
  `id` bigint unsigned NOT NULL COMMENT '采购申请ID',
  `request_no` varchar(64) NOT NULL COMMENT '申请单号',
  `applicant_id` bigint NOT NULL COMMENT '申请人',
  `applicant_dept_id` bigint DEFAULT NULL COMMENT '申请人部门',
  `item_name` varchar(120) NOT NULL COMMENT '采购物品',
  `category` varchar(32) NOT NULL COMMENT '采购类别',
  `quantity` int NOT NULL COMMENT '采购数量',
  `estimated_amount` decimal(12,2) NOT NULL COMMENT '预算金额',
  `required_date` date NOT NULL COMMENT '期望到货日期',
  `reason` varchar(500) NOT NULL COMMENT '采购事由',
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVING/APPROVED/REJECTED/REVOKED/TERMINATED',
  `workflow_instance_id` bigint unsigned DEFAULT NULL COMMENT '流程实例ID',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_purchase_request_no` (`request_no`),
  KEY `idx_biz_purchase_applicant_status` (`applicant_id`,`status`),
  KEY `idx_biz_purchase_workflow_instance` (`workflow_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务采购申请';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_repair_request` (
  `id` bigint unsigned NOT NULL COMMENT '报修申请ID',
  `request_no` varchar(64) NOT NULL COMMENT '申请单号',
  `applicant_id` bigint NOT NULL COMMENT '申请人',
  `applicant_dept_id` bigint DEFAULT NULL COMMENT '申请人部门',
  `repair_type` varchar(32) NOT NULL COMMENT '报修类型',
  `asset_name` varchar(120) NOT NULL COMMENT '报修对象',
  `urgency` varchar(32) NOT NULL COMMENT '紧急程度',
  `fault_time` datetime NOT NULL COMMENT '故障时间',
  `location` varchar(120) NOT NULL COMMENT '所在位置',
  `description` varchar(500) NOT NULL COMMENT '问题描述',
  `attachments_json` text DEFAULT NULL COMMENT '故障图片附件JSON',
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVING/APPROVED/REJECTED/REVOKED/TERMINATED',
  `workflow_instance_id` bigint unsigned DEFAULT NULL COMMENT '流程实例ID',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_repair_request_no` (`request_no`),
  KEY `idx_biz_repair_applicant_status` (`applicant_id`,`status`),
  KEY `idx_biz_repair_workflow_instance` (`workflow_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务报修申请';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


-- -----------------------------------------------------------------------------
-- Enterprise seed data
-- -----------------------------------------------------------------------------

INSERT INTO `sys_dept` (`id`, `dept_name`, `full_name`, `address`, `pid`, `tree_path`, `leader_user_id`, `status`, `sort`, `create_by`, `create_dept_id`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `remark`) VALUES (202604280103000001,'易企科技有限公司','易企科技有限公司','深圳总部',0,'/202604280103000001/',202604280101000001,1,10,202604280101000001,202604280103000001,'2026-04-29 07:57:24',202604280101000001,'2026-04-29 09:33:24',0,0,'企业根组织');
INSERT INTO `sys_dept` (`id`, `dept_name`, `full_name`, `address`, `pid`, `tree_path`, `leader_user_id`, `status`, `sort`, `create_by`, `create_dept_id`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `remark`) VALUES (202604280103000101, '总经办', '易企科技有限公司 / 总经办', '深圳总部', 202604280103000001, '/202604280103000001/202604280103000101/', 202604280101000001, 1, 20, 202604280101000001, 202604280103000001, '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, '经营管理和企业治理');
INSERT INTO `sys_dept` (`id`, `dept_name`, `full_name`, `address`, `pid`, `tree_path`, `leader_user_id`, `status`, `sort`, `create_by`, `create_dept_id`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `remark`) VALUES (202604280103000102, '产品研发中心', '易企科技有限公司 / 产品研发中心', '深圳南山', 202604280103000001, '/202604280103000001/202604280103000102/', 202604280101000018, 1, 30, 202604280101000001, 202604280103000001, '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, '产品、研发、测试和平台工程');
INSERT INTO `sys_dept` (`id`, `dept_name`, `full_name`, `address`, `pid`, `tree_path`, `leader_user_id`, `status`, `sort`, `create_by`, `create_dept_id`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `remark`) VALUES (202604280103000103, '客户成功中心', '易企科技有限公司 / 客户成功中心', '广州分部', 202604280103000001, '/202604280103000001/202604280103000103/', 202604280101000025, 1, 40, 202604280101000001, 202604280103000001, '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, '售前、交付和客户运营');
INSERT INTO `sys_dept` (`id`, `dept_name`, `full_name`, `address`, `pid`, `tree_path`, `leader_user_id`, `status`, `sort`, `create_by`, `create_dept_id`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `remark`) VALUES (202604280103000104, '运营交付中心', '易企科技有限公司 / 运营交付中心', '深圳总部', 202604280103000001, '/202604280103000001/202604280103000104/', 202604280101000024, 1, 50, 202604280101000001, 202604280103000001, '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, '应用运维、发布、监控和任务调度');
INSERT INTO `sys_dept` (`id`, `dept_name`, `full_name`, `address`, `pid`, `tree_path`, `leader_user_id`, `status`, `sort`, `create_by`, `create_dept_id`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `remark`) VALUES (202604280103000105, '财务行政部', '易企科技有限公司 / 财务行政部', '深圳总部', 202604280103000001, '/202604280103000001/202604280103000105/', 202604280101000017, 1, 60, 202604280101000001, 202604280103000001, '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, '财务、行政和审计协同');
INSERT INTO `sys_role` (`id`, `role_name`, `role_code`, `details`, `enable`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `remark`, `role_level`, `data_scope`, `create_by`, `create_dept_id`) VALUES (202604280102000005, '超级管理员', 'admin', '拥有系统配置、用户与角色维护、监控和审计能力。', 1, '2026-04-28 22:57:43', 202604280101000001, '2026-04-29 09:33:24', 0, 0, '内置最高权限角色', 1, 'ALL', 202604280101000001, 202604280103000001);
INSERT INTO `sys_role` (`id`, `role_name`, `role_code`, `details`, `enable`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `remark`, `role_level`, `data_scope`, `create_by`, `create_dept_id`) VALUES (202604280102000006, '运维人员', 'ops', '负责服务监控、在线用户、缓存监控、实时日志、定时任务和发布排障。', 1, '2026-04-28 22:57:43', 202604280101000001, '2026-04-29 09:33:24', 0, 0, '适合研发运维岗位', 30, 'DEPT', 202604280101000001, 202604280103000001);
INSERT INTO `sys_role` (`id`, `role_name`, `role_code`, `details`, `enable`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `remark`, `role_level`, `data_scope`, `create_by`, `create_dept_id`) VALUES (202604280102000010, '审计人员', 'auditor', '查看行为审计、权限配置，并处理财务复核类工作流待办。', 1, '2026-04-28 22:57:43', 202604280101000001, '2026-04-29 09:33:24', 0, 0, '适合内控、审计和财务复核岗位', 40, 'ALL', 202604280101000001, 202604280103000001);
INSERT INTO `sys_role` (`id`, `role_name`, `role_code`, `details`, `enable`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `remark`, `role_level`, `data_scope`, `create_by`, `create_dept_id`) VALUES (202604280102000012, '部门负责人', 'dept_manager', '负责本部门业务审批、人员管理和组织数据。', 1, '2026-04-28 22:57:43', 202604280101000001, '2026-04-29 09:33:24', 0, 0, '适合业务部门负责人', 20, 'DEPT_AND_CHILDREN', 202604280101000001, 202604280103000001);
INSERT INTO `sys_role` (`id`, `role_name`, `role_code`, `details`, `enable`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `remark`, `role_level`, `data_scope`, `create_by`, `create_dept_id`) VALUES (202604280102000013, '普通员工', 'staff', '基础菜单入口和个人流程处理能力。', 1, '2026-04-28 22:57:43', 202604280101000001, '2026-04-29 09:33:24', 0, 0, '适合普通业务用户', 50, 'SELF', 202604280101000001, 202604280103000001);
INSERT INTO `sys_user` (`id`, `user_name`, `password_hash`, `nick_name`, `dept_id`, `manager_user_id`, `phone`, `enable`, `email`, `avatar`, `create_by`, `create_dept_id`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `permission_version`, `remark`, `employee_no`, `real_name`, `position_name`, `last_login_time`) VALUES (202604280101000001, 'admin', '$2a$12$nIMLkCUCotpAOa9SXEZfj.WMf4Vl18DXifv6.js7CsKq.1Gdhx7mu', '超级管理员', 202604280103000101, NULL, '13800000000', 1, 'admin@easynextadmin.local', '', 202604280101000001, 202604280103000001, '2021-08-15 11:02:15', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 1, '平台初始化管理员', 'EA000001', '系统管理员', '平台管理员', NULL);
INSERT INTO `sys_user` (`id`, `user_name`, `password_hash`, `nick_name`, `dept_id`, `manager_user_id`, `phone`, `enable`, `email`, `avatar`, `create_by`, `create_dept_id`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `permission_version`, `remark`, `employee_no`, `real_name`, `position_name`, `last_login_time`) VALUES (202604280101000016, 'product_li', '$2a$12$EiB3avTyJ5c3CV4SnAVrE.Q08PllqdC9C00m4G1YPLQGw3caJTUU6', '李产品', 202604280103000102, 202604280101000018, '13800000003', 1, 'product.li@easynextadmin.local', '', 202604280101000001, 202604280103000001, '2021-08-09 18:25:32', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 1, '产品负责人样例账号', 'EA000201', '李产品', '产品经理', NULL);
INSERT INTO `sys_user` (`id`, `user_name`, `password_hash`, `nick_name`, `dept_id`, `manager_user_id`, `phone`, `enable`, `email`, `avatar`, `create_by`, `create_dept_id`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `permission_version`, `remark`, `employee_no`, `real_name`, `position_name`, `last_login_time`) VALUES (202604280101000017, 'auditor', '$2a$12$EiB3avTyJ5c3CV4SnAVrE.Q08PllqdC9C00m4G1YPLQGw3caJTUU6', '王审计', 202604280103000105, 202604280101000001, '13800000006', 1, 'auditor@easynextadmin.local', '', 202604280101000001, 202604280103000001, '2021-08-10 10:24:23', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 1, '审计人员样例账号', 'EA000401', '王审计', '内控审计', NULL);
INSERT INTO `sys_user` (`id`, `user_name`, `password_hash`, `nick_name`, `dept_id`, `manager_user_id`, `phone`, `enable`, `email`, `avatar`, `create_by`, `create_dept_id`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `permission_version`, `remark`, `employee_no`, `real_name`, `position_name`, `last_login_time`) VALUES (202604280101000018, 'tech_zhang', '$2a$12$EiB3avTyJ5c3CV4SnAVrE.Q08PllqdC9C00m4G1YPLQGw3caJTUU6', '张技术', 202604280103000102, 202604280101000001, '13800000004', 1, 'tech.zhang@easynextadmin.local', '', 202604280101000001, 202604280103000001, '2021-08-10 10:24:38', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 1, '研发负责人样例账号', 'EA000202', '张技术', '研发负责人', NULL);
INSERT INTO `sys_user` (`id`, `user_name`, `password_hash`, `nick_name`, `dept_id`, `manager_user_id`, `phone`, `enable`, `email`, `avatar`, `create_by`, `create_dept_id`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `permission_version`, `remark`, `employee_no`, `real_name`, `position_name`, `last_login_time`) VALUES (202604280101000024, 'ops', '$2a$12$EiB3avTyJ5c3CV4SnAVrE.Q08PllqdC9C00m4G1YPLQGw3caJTUU6', '周运维', 202604280103000104, 202604280101000001, '13800000005', 1, 'ops@easynextadmin.local', '', 202604280101000001, 202604280103000001, '2021-10-21 10:07:07', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 1, '运维人员样例账号', 'EA000301', '周运维', '应用运维工程师', NULL);
INSERT INTO `sys_user` (`id`, `user_name`, `password_hash`, `nick_name`, `dept_id`, `manager_user_id`, `phone`, `enable`, `email`, `avatar`, `create_by`, `create_dept_id`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `permission_version`, `remark`, `employee_no`, `real_name`, `position_name`, `last_login_time`) VALUES (202604280101000025, 'manager', '$2a$12$EiB3avTyJ5c3CV4SnAVrE.Q08PllqdC9C00m4G1YPLQGw3caJTUU6', '陈经理', 202604280103000103, 202604280101000001, '13800000001', 1, 'manager@easynextadmin.local', '', 202604280101000001, 202604280103000001, '2026-04-28 21:26:36', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 1, '客户成功中心负责人', 'EA000101', '陈经理', '客户成功经理', NULL);
INSERT INTO `sys_user` (`id`, `user_name`, `password_hash`, `nick_name`, `dept_id`, `manager_user_id`, `phone`, `enable`, `email`, `avatar`, `create_by`, `create_dept_id`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `permission_version`, `remark`, `employee_no`, `real_name`, `position_name`, `last_login_time`) VALUES (202604280101000026, 'staff', '$2a$12$EiB3avTyJ5c3CV4SnAVrE.Q08PllqdC9C00m4G1YPLQGw3caJTUU6', '林员工', 202604280103000103, 202604280101000025, '13800000002', 1, 'staff@easynextadmin.local', '', 202604280101000001, 202604280103000001, '2026-04-28 21:26:36', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 1, '普通业务员工', 'EA000102', '林员工', '客户运营专员', NULL);

INSERT INTO `audit_login_log` (`id`, `user_id`, `user_name`, `login_result`, `fail_reason`, `ip`, `user_agent`, `client_type`, `trace_id`, `login_time`) VALUES
    (202605080101000001, 202604280101000001, 'admin', 'SUCCESS', NULL, '127.0.0.1', 'Seed', 'web', 'seed-admin-login', DATE_SUB(NOW(), INTERVAL 1 DAY)),
    (202605080101000002, 202604280101000026, 'staff', 'SUCCESS', NULL, '127.0.0.1', 'Seed', 'web', 'seed-staff-login', DATE_SUB(NOW(), INTERVAL 2 DAY));

INSERT INTO `user_message` (`id`, `receiver_id`, `sender_id`, `title`, `content`, `category`, `level`, `biz_type`, `biz_id`, `link`, `read_status`, `read_at`, `created_at`) VALUES
    (202605080127000001, 202604280101000025, 0, '新的流程待办', '林员工提交了请假申请，请及时处理。', 'WORKFLOW', 'INFO', 'WORKFLOW_TASK', '202604280108020001', '/workflow/tasks?tab=pending', 0, NULL, NOW()),
    (202605080127000002, 202604280101000017, 0, '流程抄送提醒', '林员工的报修流程已抄送给你备案。', 'WORKFLOW_CC', 'INFO', 'WORKFLOW_CC', '202604280108050001', '/workflow/tasks?tab=cc&ccId=202604280108050001&instanceId=202604280108030004', 0, NULL, NOW());

INSERT INTO `biz_number_rule`
(`id`, `rule_code`, `rule_name`, `prefix`, `date_pattern`, `number_separator`, `sequence_width`, `sequence_step`, `initial_value`, `enable`, `create_by`, `create_dept_id`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `remark`)
VALUES
    (202606250112000001, 'LEAVE_REQUEST', '请假申请单号', 'LV', 'yyyyMMdd', '-', 6, 1, 0, 1, 202604280101000001, 202604280103000001, NOW(), 202604280101000001, NOW(), 0, 0, '流程请假申请使用的业务编号规则。'),
    (202606250112000002, 'PURCHASE_REQUEST', '采购申请单号', 'PR', 'yyyyMMdd', '-', 6, 1, 0, 1, 202604280101000001, 202604280103000001, NOW(), 202604280101000001, NOW(), 0, 0, '流程采购申请使用的业务编号规则。'),
    (202606250112000003, 'REPAIR_REQUEST', '报修申请单号', 'RP', 'yyyyMMdd', '-', 6, 1, 0, 1, 202604280101000001, 202604280103000001, NOW(), 202604280101000001, NOW(), 0, 0, '流程报修申请使用的业务编号规则。');

INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000001, 0, '工作台', 'DataBoard', '/dashboard', 10, 1, '进入企业工作台并查看系统概览。', '2026-04-28 22:57:43', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 1, 'dashboard:view', '@/views/dashboard/WorkspaceView.vue', 1, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000100, 0, '系统管理', 'Setting', '', 20, 1, '系统基础能力分组。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 0, NULL, NULL, 1, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000101, 202604280104000100, '用户管理', 'User', '/system/users', 10, 1, '维护企业账号、部门、状态和角色绑定。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 1, 'sys:user:list', '@/views/system/UserView.vue', 1, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000102, 202604280104000100, '角色权限', 'Key', '/system/roles', 20, 1, '维护角色资料、菜单权限、按钮权限和数据范围。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 1, 'sys:role:list', '@/views/system/RoleView.vue', 1, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000103, 202604280104000100, '菜单配置', 'Menu', '/system/menus', 30, 1, '维护目录、页面和按钮权限。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 1, 'sys:menu:list', '@/views/system/MenuView.vue', 1, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000104, 202604280104000100, '组织架构', 'OfficeBuilding', '/system/departments', 40, 1, '维护企业部门树和负责人。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 1, 'sys:dept:list', '@/views/system/DepartmentView.vue', 1, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000111, 202604280104000101, '新增用户', '', '', 11, 1, '允许创建账号并绑定部门、角色。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 2, 'sys:user:add', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000112, 202604280104000101, '编辑用户', '', '', 12, 1, '允许修改账号资料、启停状态和角色绑定。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 2, 'sys:user:edit', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000113, 202604280104000101, '删除用户', '', '', 13, 1, '允许删除非超级管理员账号。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 2, 'sys:user:delete', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000114, 202604280104000101, '导入用户', '', '', 14, 1, '允许下载用户导入模板并批量导入账号。', '2026-05-09 22:40:00', 202604280101000001, '2026-05-09 22:40:00', 0, 0, 2, 'sys:user:import', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000115, 202604280104000101, '导出用户', '', '', 15, 1, '允许按当前筛选条件导出用户 CSV。', '2026-05-09 22:40:00', 202604280101000001, '2026-05-09 22:40:00', 0, 0, 2, 'sys:user:export', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000116, 202604280104000101, '重置密码', '', '', 16, 1, '允许将非高权限用户密码重置为系统默认密码。', '2026-05-13 13:20:00', 202604280101000001, '2026-05-13 13:20:00', 0, 0, 2, 'sys:user:reset-password', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000121, 202604280104000102, '编辑角色与授权配置', '', '', 11, 1, '允许维护角色资料并配置菜单权限、按钮权限和数据范围策略。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 2, 'sys:role:edit', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000131, 202604280104000103, '编辑权限资源', '', '', 11, 1, '允许维护目录、页面和按钮权限。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 2, 'sys:menu:edit', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000141, 202604280104000104, '编辑部门', '', '', 11, 1, '允许维护部门和组织树。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 2, 'sys:dept:edit', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000151, 202604280104000100, '文件中心', 'Document', '/system/files', 50, 1, '进入文件中心，查看文件元数据并通过鉴权接口下载。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 1, 'sys:file:list', '@/views/system/FileCenterView.vue', 1, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000152, 202604280104000151, '上传文件', '', '', 11, 1, '允许上传白名单范围内的企业附件。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 2, 'sys:file:upload', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000153, 202604280104000151, '删除文件', '', '', 12, 1, '允许删除文件元数据和存储对象。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 2, 'sys:file:delete', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202606250104000001, 202604280104000100, '编号规则', 'Tickets', '/system/business-numbers', 60, 1, '维护业务单号、工单号、申请单号等可读编号规则。', NOW(), 202604280101000001, NOW(), 0, 0, 1, 'business:number:list', '@/views/system/BusinessNumberRuleView.vue', 1, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202606250104000002, 202606250104000001, '维护编号规则', '', '', 11, 1, '允许新增、编辑、停用和删除业务编号规则。', NOW(), 202604280101000001, NOW(), 0, 0, 2, 'business:number:edit', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202606250104000003, 202606250104000001, '人工生成编号', '', '', 12, 1, '允许运维或管理员在编号规则页人工生成测试编号。', NOW(), 202604280101000001, NOW(), 0, 0, 2, 'business:number:generate', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202605260104000001, 0, '报表中心', 'DataAnalysis', '/reports/enterprise', 28, 1, '查看组织人员台账和采购流程复核纸质报表。', '2026-05-26 21:45:00', 202604280101000001, '2026-05-26 21:45:00', 0, 0, 1, 'report:view', '@/views/report/EnterpriseReportView.vue', 1, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000200, 0, '运行监控', 'Monitor', '', 30, 1, '服务监控、在线用户、缓存监控、缓存列表、实时日志和任务调度分组。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 0, NULL, NULL, 1, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000201, 202604280104000200, '服务监控', 'Monitor', '/monitor/server', 10, 1, '查看服务健康、JVM、CPU、内存、线程、磁盘和 GC 水位。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 1, 'monitor:server:view', '@/views/monitor/MonitorView.vue', 1, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000204, 202604280104000200, '在线用户', 'UserFilled', '/monitor/online', 20, 1, '查看在线账号、客户端、来源 IP 和最近活跃时间。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 1, 'monitor:online:view', '@/views/monitor/OnlineUserView.vue', 1, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000211, 202604280104000204, '下线会话', '', '', 11, 1, '允许运维人员终止异常在线会话。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 2, 'auth:session:revoke', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000205, 202604280104000200, '缓存监控', 'Coin', '/monitor/cache', 30, 1, '查看缓存名称、容量、命中率和淘汰次数，并支持按名称清理缓存。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 1, 'monitor:cache:view', '@/views/monitor/CacheMonitorView.vue', 1, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000213, 202604280104000200, '缓存列表', 'Tickets', '/monitor/cache-list', 31, 1, '查看缓存 key 和 value 预览，支持按 key 精确清理缓存项。', '2026-05-12 10:00:00', 202604280101000001, '2026-05-12 10:00:00', 0, 0, 1, 'monitor:cache:view', '@/views/monitor/CacheListView.vue', 1, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000212, 202604280104000205, '清理缓存', '', 'DELETE /api/monitor/cache/{cacheName}, DELETE /api/monitor/cache/{cacheName}/entries', 11, 1, '允许按缓存名称清理缓存，或按 key 精确清理缓存项。', '2026-04-29 07:57:24', 202604280101000001, '2026-05-12 10:00:00', 0, 0, 2, 'monitor:cache:clear', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000203, 202604280104000200, '实时日志', 'Document', '/monitor/weblog', 40, 1, '查看 logback 当前文件日志，支持关键词、级别和行数过滤。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 1, 'monitor:weblog:view', '@/views/monitor/WebLogView.vue', 1, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202605130104000001, 202604280104000203, '调整日志级别', '', '', 11, 1, '允许在白名单范围内临时调整实时日志级别。', '2026-05-13 22:30:00', 202604280101000001, '2026-05-13 22:30:00', 0, 0, 2, 'monitor:weblog:level', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000250, 0, '审计中心', 'Operation', '', 35, 1, '登录、操作、数据变更、异常和接口访问审计分组。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 0, NULL, NULL, 1, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000202, 202604280104000250, '行为审计', 'Operation', '/audit/behavior', 10, 1, '查看登录、操作、数据变更、异常和接口访问审计。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 1, 'audit:behavior:view', '@/views/audit/BehaviorAuditView.vue', 1, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000500, 0, '消息中心', 'Bell', '/messages', 38, 1, '查看流程待办、抄送、审计提醒和任务消息。', '2026-05-08 22:05:00', 202604280101000001, '2026-05-08 22:05:00', 0, 0, 1, 'message:view', '@/views/message/MessageCenterView.vue', 1, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000501, 202604280104000500, '标记消息已读', '', '', 11, 1, '允许将自己的站内消息标记为已读。', '2026-05-08 22:05:00', 202604280101000001, '2026-05-08 22:05:00', 0, 0, 2, 'message:read', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000510, 0, '个人中心', 'UserFilled', '/profile/security', 39, 1, '维护个人资料、头像、密码、登录历史和会话。', '2026-05-10 09:00:00', 202604280101000001, '2026-05-10 09:00:00', 0, 0, 1, 'profile:view', '@/views/profile/ProfileSecurityView.vue', 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000511, 202604280104000510, '编辑个人资料', '', '', 11, 1, '允许维护自己的基础资料和头像。', '2026-05-10 09:00:00', 202604280101000001, '2026-05-10 09:00:00', 0, 0, 2, 'profile:edit', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000512, 202604280104000510, '修改个人密码', '', '', 12, 1, '允许修改自己的登录密码。', '2026-05-10 09:00:00', 202604280101000001, '2026-05-10 09:00:00', 0, 0, 2, 'profile:password:change', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000513, 202604280104000510, '管理个人会话', '', '', 13, 1, '允许查看并撤销自己的登录会话。', '2026-05-10 09:00:00', 202604280101000001, '2026-05-10 09:00:00', 0, 0, 2, 'profile:session:manage', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202605110104000001, 0, '接口文档', 'DocumentChecked', '/developer/api-docs', 45, 1, '查看 OpenAPI Swagger UI 接口文档。', '2026-05-11 10:00:00', 202604280101000001, '2026-05-11 10:00:00', 0, 0, 1, 'developer:api-docs:view', '@/views/developer/ApiDocsView.vue', 1, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000300, 202604280104000200, '定时任务', 'Timer', '/schedule/jobs', 50, 1, '查看任务定义、运行状态和最近执行结果。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 1, 'schedule:job:list', '@/views/schedule/JobView.vue', 1, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000301, 202604280104000300, '维护定时任务', '', '', 11, 1, '允许新增、暂停、恢复、立即执行和编辑任务。', '2026-04-28 22:57:43', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 2, 'schedule:job:edit', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202606250104010001, 202604280104000200, '批处理任务', 'Operation', '/batch/tasks', 55, 1, '查看批量导入、同步和报表生成等长任务的进度、失败明细和治理动作。', NOW(), 202604280101000001, NOW(), 0, 0, 1, 'batch:task:list', '@/views/batch/BatchTaskView.vue', 1, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202606250104010002, 202606250104010001, '治理批处理任务', '', '', 11, 1, '允许取消批处理任务和重置失败项等待业务 worker 补跑。', NOW(), 202604280101000001, NOW(), 0, 0, 2, 'batch:task:manage', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000400, 0, '流程中心', 'Connection', '', 50, 1, '面向业务用户的申请、待办和面向管理员的流程配置分组。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 0, NULL, NULL, 1, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000419, 202604280104000400, '发起流程', 'Promotion', '/workflow/start', 10, 1, '统一展示请假、采购和报修等可发起流程入口。', '2026-05-12 10:00:00', 202604280101000001, '2026-05-12 10:00:00', 0, 0, 1, 'workflow:instance:start', '@/views/workflow/WorkflowStartView.vue', 1, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000412, 202604280104000400, '请假申请', 'Document', '/workflow/leave', 11, 1, '填写请假单并发起请假审批流程。', '2026-04-29 07:57:24', 202604280101000001, '2026-05-12 10:00:00', 0, 0, 1, 'workflow:instance:start', '@/views/workflow/LeaveRequestView.vue', 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000417, 202604280104000400, '采购申请', 'ShoppingCart', '/workflow/purchase', 12, 1, '填写采购单并按预算金额发起采购审批。', '2026-04-29 07:57:24', 202604280101000001, '2026-05-12 10:00:00', 0, 0, 1, 'workflow:instance:start', '@/views/workflow/PurchaseRequestView.vue', 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000418, 202604280104000400, '报修申请', 'Tools', '/workflow/repair', 13, 1, '填写报修信息并提交运维受理流程。', '2026-04-29 07:57:24', 202604280101000001, '2026-05-12 10:00:00', 0, 0, 1, 'workflow:instance:start', '@/views/workflow/RepairRequestView.vue', 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000413, 202604280104000400, '我的流程', 'Finished', '/workflow/tasks', 30, 1, '查看待办、已办、我发起的和抄送流程。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 1, 'workflow:view', '@/views/workflow/WorkflowTaskCenterView.vue', 1, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000420, 202604280104000400, '流程实例', 'Tickets', '/workflow/instances', 40, 1, '具备流程实例管理权限的管理员监控全部流程实例，并执行催办、转办或终止。', '2026-05-15 10:00:00', 202604280101000001, '2026-05-15 10:00:00', 0, 0, 1, 'workflow:instance:manage', '@/views/workflow/WorkflowInstanceMonitorView.vue', 1, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000414, 202604280104000400, '流程配置', 'Connection', '/workflow/console', 90, 1, '管理员维护流程定义、节点和发布状态。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 1, 'workflow:definition:edit', '@/views/workflow/WorkflowView.vue', 1, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000401, 202604280104000414, '维护流程定义', '', '', 11, 1, '允许发布、停用、删除和调整流程定义。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 2, 'workflow:definition:edit', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000403, 202604280104000419, '提交流程申请', '', '', 10, 1, '允许基于已启用流程定义发起流程实例。', '2026-04-29 07:57:24', 202604280101000001, '2026-05-12 10:00:00', 0, 0, 2, 'workflow:instance:start', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000404, 202604280104000413, '撤回流程', '', '', 13, 1, '允许撤回自己发起的运行中流程。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 2, 'workflow:instance:revoke', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000405, 202604280104000414, '终止流程', '', '', 14, 1, '允许终止运行中的流程实例。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 2, 'workflow:instance:terminate', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000406, 202604280104000413, '同意任务', '', '', 15, 1, '允许处理待办任务并流转到下一节点。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 2, 'workflow:task:approve', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000407, 202604280104000413, '驳回任务', '', '', 16, 1, '允许驳回待办任务并结束流程实例。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 2, 'workflow:task:reject', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000408, 202604280104000413, '转办任务', '', '', 17, 1, '允许将当前待办转交给其他处理人。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 2, 'workflow:task:transfer', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000409, 202604280104000413, '委派任务', '', '', 18, 1, '允许临时委派当前待办给其他处理人。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 2, 'workflow:task:delegate', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000410, 202604280104000413, '退回任务', '', '', 19, 1, '允许将当前任务退回到提交节点或指定节点。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 2, 'workflow:task:return', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000415, 202604280104000413, '加签任务', '', '', 20, 1, '允许给当前审批节点追加处理人，所有加签待办完成后才继续流转。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 2, 'workflow:task:add-sign', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000416, 202604280104000413, '减签任务', '', '', 21, 1, '允许移除当前节点尚未处理的加签待办。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 2, 'workflow:task:remove-sign', NULL, 0, 202604280101000001, 202604280103000001);
INSERT INTO `sys_menu` (`id`, `pid`, `title`, `icon`, `href`, `sort`, `enable`, `remark`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `type`, `permission_code`, `component_path`, `visible`, `create_by`, `create_dept_id`) VALUES (202604280104000411, 202604280104000413, '催办任务', '', '', 22, 1, '允许对待处理任务发起催办留痕。', '2026-04-29 07:57:24', 202604280101000001, '2026-04-29 09:33:24', 0, 0, 2, 'workflow:task:remind', NULL, 0, 202604280101000001, 202604280103000001);

INSERT INTO `sys_user_role` (`id`, `user_id`, `role_id`, `create_by`, `create_dept_id`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `remark`) VALUES
    (202604280105001001, 202604280101000001, 202604280102000005, 202604280101000001, 202604280103000001, NOW(), 202604280101000001, NOW(), 0, 0, '超级管理员默认角色'),
    (202604280105001002, 202604280101000025, 202604280102000012, 202604280101000001, 202604280103000001, NOW(), 202604280101000001, NOW(), 0, 0, '部门负责人测试账号'),
    (202604280105001003, 202604280101000026, 202604280102000013, 202604280101000001, 202604280103000001, NOW(), 202604280101000001, NOW(), 0, 0, '普通员工测试账号'),
    (202604280105001004, 202604280101000016, 202604280102000013, 202604280101000001, 202604280103000001, NOW(), 202604280101000001, NOW(), 0, 0, '产品岗位样例账号'),
    (202604280105001005, 202604280101000017, 202604280102000010, 202604280101000001, 202604280103000001, NOW(), 202604280101000001, NOW(), 0, 0, '审计岗位样例账号'),
    (202604280105001006, 202604280101000018, 202604280102000012, 202604280101000001, 202604280103000001, NOW(), 202604280101000001, NOW(), 0, 0, '研发负责人样例账号'),
    (202604280105001007, 202604280101000024, 202604280102000006, 202604280101000001, 202604280103000001, NOW(), 202604280101000001, NOW(), 0, 0, '运维岗位样例账号');

INSERT INTO `schedule_job` (`job_id`, `job_code`, `job_name`, `job_class_name`, `cron_expression`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `remark`, `enable`, `job_state`, `create_by`, `create_dept_id`) VALUES
    (202604280107000001, 'infra_local_message_retry', '本地消息失败重试', 'com.laker.admin.infrastructure.message.local.LocalMessageRetryJob', '0 0/1 * * * ?', NOW(), 202604280101000001, NOW(), 0, 0, '平台内置任务，用于重试失败的本地消息。', 1, 1, 202604280101000001, 202604280103000001);

INSERT INTO `wf_process_definition` (`id`, `process_key`, `process_name`, `current_version`, `status`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`) VALUES
    (202604280108000001, 'leave_approval', '请假审批', 1, 'ENABLED', '以请假天数分流：3天内部门负责人审批，超过3天追加总经办审批，并支持加签、转办、委派、退回和抄送留痕。', 202604280101000001, NOW(), 202604280101000001, NOW()),
    (202604280108000002, 'expense_approval', '报销审批', 1, 'ENABLED', '部门负责人审批后进入财务复核。', 202604280101000001, NOW(), 202604280101000001, NOW()),
    (202604280108000003, 'purchase_approval', '采购审批', 1, 'ENABLED', '按采购金额流转到部门负责人或总经办审批。', 202604280101000001, NOW(), 202604280101000001, NOW()),
    (202604280108000004, 'repair_approval', '报修审批', 1, 'ENABLED', '员工提交设备、网络和办公设施报修后，由运维受理并抄送审计备案。', 202604280101000001, NOW(), 202604280101000001, NOW());

INSERT INTO `wf_process_definition_version` (`id`, `definition_id`, `version`, `graph_json`, `status`, `published_by`, `published_at`, `created_by`, `created_at`, `updated_by`, `updated_at`) VALUES
    (202604280108010001, 202604280108000001, 1, '{"edges": [{"id": "0ad68573-b18a-43ff-bb04-3f752cf4cef0", "type": "polyline", "endPoint": {"x": 272, "y": 296}, "pointsList": [{"x": 150, "y": 296}, {"x": 272, "y": 296}], "properties": {"style": {"stroke": "#64748b", "strokeWidth": 1.8, "strokeLinecap": "round", "strokeLinejoin": "round"}, "textStyle": {"fill": "#64748b", "fontSize": 12, "background": {"fill": "#ffffff", "radius": 4, "stroke": "#dbe7f5", "strokeWidth": 1, "wrapPadding": "3px 6px"}, "fontWeight": 700}, "labelOffsetX": 0, "labelOffsetY": 0, "labelPlacement": "AUTO"}, "startPoint": {"x": 150, "y": 296}, "sourceNodeId": "start", "targetNodeId": "submit", "sourceAnchorId": "start_1", "targetAnchorId": "submit_3"}, {"id": "5f885871-0fb5-4c02-8963-95c452a23bc3", "type": "polyline", "endPoint": {"x": 544, "y": 296}, "pointsList": [{"x": 440, "y": 296}, {"x": 544, "y": 296}], "properties": {"style": {"stroke": "#64748b", "strokeWidth": 1.8, "strokeLinecap": "round", "strokeLinejoin": "round"}, "textStyle": {"fill": "#64748b", "fontSize": 12, "background": {"fill": "#ffffff", "radius": 4, "stroke": "#dbe7f5", "strokeWidth": 1, "wrapPadding": "3px 6px"}, "fontWeight": 700}, "labelOffsetX": 0, "labelOffsetY": 0, "labelPlacement": "AUTO"}, "startPoint": {"x": 440, "y": 296}, "sourceNodeId": "submit", "targetNodeId": "duration_check", "sourceAnchorId": "submit_1", "targetAnchorId": "duration_check_3"}, {"id": "bb9b5a4d-a2a5-4431-a664-3b0f460b5d3e", "text": "3天内", "type": "polyline", "endPoint": {"x": 752, "y": 146}, "pointsList": [{"x": 648, "y": 296}, {"x": 690, "y": 296}, {"x": 690, "y": 146}, {"x": 752, "y": 146}], "properties": {"style": {"stroke": "#64748b", "strokeWidth": 1.8, "strokeLinecap": "round", "strokeLinejoin": "round"}, "textStyle": {"fill": "#64748b", "fontSize": 12, "background": {"fill": "#ffffff", "radius": 4, "stroke": "#dbe7f5", "strokeWidth": 1, "wrapPadding": "3px 6px"}, "fontWeight": 700}, "labelOffsetX": 0, "labelOffsetY": 0, "conditionType": "EXPRESSION", "labelPlacement": "START", "conditionExpression": "days <= 3"}, "startPoint": {"x": 648, "y": 296}, "sourceNodeId": "duration_check", "targetNodeId": "manager", "sourceAnchorId": "duration_check_1", "targetAnchorId": "manager_3"}, {"id": "22eba25f-dcd8-4794-9903-22d152964cd6", "text": "默认/超过3天", "type": "polyline", "endPoint": {"x": 752, "y": 446}, "pointsList": [{"x": 648, "y": 296}, {"x": 690, "y": 296}, {"x": 690, "y": 446}, {"x": 752, "y": 446}], "properties": {"style": {"stroke": "#64748b", "strokeWidth": 1.8, "strokeLinecap": "round", "strokeLinejoin": "round"}, "textStyle": {"fill": "#64748b", "fontSize": 12, "background": {"fill": "#ffffff", "radius": 4, "stroke": "#dbe7f5", "strokeWidth": 1, "wrapPadding": "3px 6px"}, "fontWeight": 700}, "labelOffsetX": 0, "labelOffsetY": 0, "conditionType": "ALWAYS", "labelPlacement": "AUTO"}, "startPoint": {"x": 648, "y": 296}, "sourceNodeId": "duration_check", "targetNodeId": "manager_long", "sourceAnchorId": "duration_check_1", "targetAnchorId": "manager_long_3"}, {"id": "bf6397a8-94e7-4c14-bea6-86b97c786fa9", "text": "通过", "type": "polyline", "endPoint": {"x": 1232, "y": 296}, "pointsList": [{"x": 920, "y": 146}, {"x": 1174, "y": 146}, {"x": 1174, "y": 296}, {"x": 1232, "y": 296}], "properties": {"style": {"stroke": "#64748b", "strokeWidth": 1.8, "strokeLinecap": "round", "strokeLinejoin": "round"}, "textStyle": {"fill": "#64748b", "fontSize": 12, "background": {"fill": "#ffffff", "radius": 4, "stroke": "#dbe7f5", "strokeWidth": 1, "wrapPadding": "3px 6px"}, "fontWeight": 700}, "labelOffsetX": -76, "labelOffsetY": 2, "labelPlacement": "CUSTOM"}, "startPoint": {"x": 920, "y": 146}, "sourceNodeId": "manager", "targetNodeId": "cc_hr", "sourceAnchorId": "manager_1", "targetAnchorId": "cc_hr_3"}, {"id": "8ef7a80d-1695-42e5-84ce-0f1f02e2b9df", "text": "复核通过", "type": "polyline", "endPoint": {"x": 992, "y": 446}, "pointsList": [{"x": 920, "y": 446}, {"x": 992, "y": 446}], "properties": {"style": {"stroke": "#64748b", "strokeWidth": 1.8, "strokeLinecap": "round", "strokeLinejoin": "round"}, "textStyle": {"fill": "#64748b", "fontSize": 12, "background": {"fill": "#ffffff", "radius": 4, "stroke": "#dbe7f5", "strokeWidth": 1, "wrapPadding": "3px 6px"}, "fontWeight": 700}, "labelOffsetX": 0, "labelOffsetY": 0, "labelPlacement": "AUTO"}, "startPoint": {"x": 920, "y": 446}, "sourceNodeId": "manager_long", "targetNodeId": "office", "sourceAnchorId": "manager_long_1", "targetAnchorId": "office_3"}, {"id": "ff2cb0e0-2189-4b73-aa6e-dd642a3aa7ca", "text": "通过", "type": "polyline", "endPoint": {"x": 1232, "y": 296}, "pointsList": [{"x": 1160, "y": 446}, {"x": 1174, "y": 446}, {"x": 1174, "y": 296}, {"x": 1232, "y": 296}], "properties": {"style": {"stroke": "#64748b", "strokeWidth": 1.8, "strokeLinecap": "round", "strokeLinejoin": "round"}, "textStyle": {"fill": "#64748b", "fontSize": 12, "background": {"fill": "#ffffff", "radius": 4, "stroke": "#dbe7f5", "strokeWidth": 1, "wrapPadding": "3px 6px"}, "fontWeight": 700}, "labelOffsetX": 48, "labelOffsetY": 42, "labelPlacement": "CUSTOM"}, "startPoint": {"x": 1160, "y": 446}, "sourceNodeId": "office", "targetNodeId": "cc_hr", "sourceAnchorId": "office_1", "targetAnchorId": "cc_hr_3"}, {"id": "bdfd95f6-5ae7-4e49-bd05-4058190ad0e1", "type": "polyline", "endPoint": {"x": 1522, "y": 296}, "pointsList": [{"x": 1400, "y": 296}, {"x": 1522, "y": 296}], "properties": {"style": {"stroke": "#64748b", "strokeWidth": 1.8, "strokeLinecap": "round", "strokeLinejoin": "round"}, "textStyle": {"fill": "#64748b", "fontSize": 12, "background": {"fill": "#ffffff", "radius": 4, "stroke": "#dbe7f5", "strokeWidth": 1, "wrapPadding": "3px 6px"}, "fontWeight": 700}, "labelOffsetX": 0, "labelOffsetY": 0, "labelPlacement": "AUTO"}, "startPoint": {"x": 1400, "y": 296}, "sourceNodeId": "cc_hr", "targetNodeId": "end", "sourceAnchorId": "cc_hr_1", "targetAnchorId": "end_3"}], "nodes": [{"x": 116, "y": 296, "id": "start", "text": {"x": 116, "y": 296, "value": "开始"}, "type": "workflow-circle", "properties": {"r": 32, "style": {"fill": "#f8fbff", "stroke": "#2563eb", "strokeWidth": 2}, "width": 64, "height": 64, "nodeType": "START", "textStyle": {"fill": "#1e40af", "fontSize": 13, "fontWeight": 700}}}, {"x": 356, "y": 296, "id": "submit", "text": {"x": 356, "y": 296, "value": "提交请假"}, "type": "workflow-rect", "properties": {"style": {"fill": "#ffffff", "stroke": "#94a3b8", "strokeWidth": 1.8}, "width": 168, "height": 64, "radius": 8, "nodeType": "SUBMIT", "textStyle": {"fill": "#334155", "fontSize": 13, "fontWeight": 700}}}, {"x": 596, "y": 296, "id": "duration_check", "text": {"x": 596, "y": 296, "value": "时长判断"}, "type": "workflow-diamond", "properties": {"rx": 52, "ry": 52, "style": {"fill": "#fffbeb", "stroke": "#d97706", "strokeWidth": 2}, "width": 104, "height": 104, "nodeType": "CONDITION", "textStyle": {"fill": "#9a3412", "fontSize": 13, "fontWeight": 700}, "description": "按请假天数自动分流"}}, {"x": 836, "y": 146, "id": "manager", "text": {"x": 836, "y": 146, "value": "部门负责人审批"}, "type": "workflow-rect", "properties": {"style": {"fill": "#f8fbff", "stroke": "#2563eb", "strokeWidth": 2}, "width": 168, "height": 64, "radius": 8, "nodeType": "APPROVAL", "textStyle": {"fill": "#1e3a8a", "fontSize": 13, "fontWeight": 700}, "approveType": "ANY_ONE", "description": "3天内由直属部门负责人审批，可加签、转办、委派或退回", "approverType": "DEPT_LEADER"}}, {"x": 836, "y": 446, "id": "manager_long", "text": {"x": 836, "y": 446, "value": "部门负责人复核"}, "type": "workflow-rect", "properties": {"style": {"fill": "#f8fbff", "stroke": "#2563eb", "strokeWidth": 2}, "width": 168, "height": 64, "radius": 8, "nodeType": "APPROVAL", "textStyle": {"fill": "#1e3a8a", "fontSize": 13, "fontWeight": 700}, "approveType": "ANY_ONE", "description": "长假先由直属部门负责人复核", "approverType": "DEPT_LEADER"}}, {"x": 1076, "y": 446, "id": "office", "text": {"x": 1076, "y": 446, "value": "总经办审批"}, "type": "workflow-rect", "properties": {"style": {"fill": "#f8fbff", "stroke": "#2563eb", "strokeWidth": 2}, "width": 168, "height": 64, "radius": 8, "nodeType": "APPROVAL", "textStyle": {"fill": "#1e3a8a", "fontSize": 13, "fontWeight": 700}, "approveType": "ANY_ONE", "assigneeIds": ["202604280101000001"], "description": "超过3天追加总经办审批", "approverType": "USER"}}, {"x": 1316, "y": 296, "id": "cc_hr", "text": {"x": 1316, "y": 296, "value": "行政备案"}, "type": "workflow-rect", "properties": {"style": {"fill": "#fbfefc", "stroke": "#16a34a", "strokeWidth": 2, "strokeDasharray": "5 4"}, "width": 168, "height": 64, "radius": 8, "nodeType": "CC", "ccUserIds": ["202604280101000017"], "textStyle": {"fill": "#166534", "fontSize": 13, "fontWeight": 700}, "description": "审批完成后抄送行政/审计留痕"}}, {"x": 1556, "y": 296, "id": "end", "text": {"x": 1556, "y": 296, "value": "结束"}, "type": "workflow-circle", "properties": {"r": 32, "style": {"fill": "#f8fafc", "stroke": "#475569", "strokeWidth": 2}, "width": 64, "height": 64, "nodeType": "END", "textStyle": {"fill": "#334155", "fontSize": 13, "fontWeight": 700}}}]}', 'PUBLISHED', 202604280101000001, NOW(), 202604280101000001, NOW(), 202604280101000001, NOW()),
    (202604280108010002, 202604280108000002, 1, '{"edges": [{"id": "fff7700c-9ca6-47bf-825b-c1bc024876b8", "type": "polyline", "endPoint": {"x": 272, "y": 296}, "pointsList": [{"x": 150, "y": 296}, {"x": 272, "y": 296}], "properties": {"style": {"stroke": "#64748b", "strokeWidth": 1.8, "strokeLinecap": "round", "strokeLinejoin": "round"}, "textStyle": {"fill": "#64748b", "fontSize": 12, "background": {"fill": "#ffffff", "radius": 4, "stroke": "#dbe7f5", "strokeWidth": 1, "wrapPadding": "3px 6px"}, "fontWeight": 700}, "labelOffsetX": 0, "labelOffsetY": 0, "labelPlacement": "AUTO"}, "startPoint": {"x": 150, "y": 296}, "sourceNodeId": "start", "targetNodeId": "submit", "sourceAnchorId": "start_1", "targetAnchorId": "submit_3"}, {"id": "6e792e0b-420b-4feb-979f-aec48af7d7fe", "type": "polyline", "endPoint": {"x": 512, "y": 296}, "pointsList": [{"x": 440, "y": 296}, {"x": 512, "y": 296}], "properties": {"style": {"stroke": "#64748b", "strokeWidth": 1.8, "strokeLinecap": "round", "strokeLinejoin": "round"}, "textStyle": {"fill": "#64748b", "fontSize": 12, "background": {"fill": "#ffffff", "radius": 4, "stroke": "#dbe7f5", "strokeWidth": 1, "wrapPadding": "3px 6px"}, "fontWeight": 700}, "labelOffsetX": 0, "labelOffsetY": 0, "labelPlacement": "AUTO"}, "startPoint": {"x": 440, "y": 296}, "sourceNodeId": "submit", "targetNodeId": "manager", "sourceAnchorId": "submit_1", "targetAnchorId": "manager_3"}, {"id": "49846e5d-2181-4114-951f-67934919a629", "type": "polyline", "endPoint": {"x": 752, "y": 296}, "pointsList": [{"x": 680, "y": 296}, {"x": 752, "y": 296}], "properties": {"style": {"stroke": "#64748b", "strokeWidth": 1.8, "strokeLinecap": "round", "strokeLinejoin": "round"}, "textStyle": {"fill": "#64748b", "fontSize": 12, "background": {"fill": "#ffffff", "radius": 4, "stroke": "#dbe7f5", "strokeWidth": 1, "wrapPadding": "3px 6px"}, "fontWeight": 700}, "labelOffsetX": 0, "labelOffsetY": 0, "labelPlacement": "AUTO"}, "startPoint": {"x": 680, "y": 296}, "sourceNodeId": "manager", "targetNodeId": "finance", "sourceAnchorId": "manager_1", "targetAnchorId": "finance_3"}, {"id": "d8820fe5-99ac-4f1b-8821-99127b9216ef", "text": "通过", "type": "polyline", "endPoint": {"x": 1042, "y": 296}, "pointsList": [{"x": 920, "y": 296}, {"x": 1042, "y": 296}], "properties": {"style": {"stroke": "#64748b", "strokeWidth": 1.8, "strokeLinecap": "round", "strokeLinejoin": "round"}, "textStyle": {"fill": "#64748b", "fontSize": 12, "background": {"fill": "#ffffff", "radius": 4, "stroke": "#dbe7f5", "strokeWidth": 1, "wrapPadding": "3px 6px"}, "fontWeight": 700}, "labelOffsetX": 0, "labelOffsetY": 0, "labelPlacement": "AUTO"}, "startPoint": {"x": 920, "y": 296}, "sourceNodeId": "finance", "targetNodeId": "end", "sourceAnchorId": "finance_1", "targetAnchorId": "end_3"}], "nodes": [{"x": 116, "y": 296, "id": "start", "text": {"x": 116, "y": 296, "value": "开始"}, "type": "workflow-circle", "properties": {"r": 32, "style": {"fill": "#f8fbff", "stroke": "#2563eb", "strokeWidth": 2}, "width": 64, "height": 64, "nodeType": "START", "textStyle": {"fill": "#1e40af", "fontSize": 13, "fontWeight": 700}}}, {"x": 356, "y": 296, "id": "submit", "text": {"x": 356, "y": 296, "value": "提交报销"}, "type": "workflow-rect", "properties": {"style": {"fill": "#ffffff", "stroke": "#94a3b8", "strokeWidth": 1.8}, "width": 168, "height": 64, "radius": 8, "nodeType": "SUBMIT", "textStyle": {"fill": "#334155", "fontSize": 13, "fontWeight": 700}}}, {"x": 596, "y": 296, "id": "manager", "text": {"x": 596, "y": 296, "value": "负责人审批"}, "type": "workflow-rect", "properties": {"style": {"fill": "#f8fbff", "stroke": "#2563eb", "strokeWidth": 2}, "width": 168, "height": 64, "radius": 8, "nodeType": "APPROVAL", "textStyle": {"fill": "#1e3a8a", "fontSize": 13, "fontWeight": 700}, "approveType": "ANY_ONE", "description": "发起人所在部门负责人审批", "approverType": "DEPT_LEADER"}}, {"x": 836, "y": 296, "id": "finance", "text": {"x": 836, "y": 296, "value": "财务复核"}, "type": "workflow-rect", "properties": {"style": {"fill": "#f8fbff", "stroke": "#2563eb", "strokeWidth": 2}, "width": 168, "height": 64, "radius": 8, "nodeType": "APPROVAL", "textStyle": {"fill": "#1e3a8a", "fontSize": 13, "fontWeight": 700}, "approveType": "ANY_ONE", "assigneeIds": ["202604280101000017"], "description": "财务行政部复核", "approverType": "USER"}}, {"x": 1076, "y": 296, "id": "end", "text": {"x": 1076, "y": 296, "value": "结束"}, "type": "workflow-circle", "properties": {"r": 32, "style": {"fill": "#f8fafc", "stroke": "#475569", "strokeWidth": 2}, "width": 64, "height": 64, "nodeType": "END", "textStyle": {"fill": "#334155", "fontSize": 13, "fontWeight": 700}}}]}', 'PUBLISHED', 202604280101000001, NOW(), 202604280101000001, NOW(), 202604280101000001, NOW()),
    (202604280108010003, 202604280108000003, 1, '{"edges": [{"id": "9885a41a-f6c1-4937-886c-5445dc209dd4", "type": "polyline", "endPoint": {"x": 272, "y": 296}, "pointsList": [{"x": 150, "y": 296}, {"x": 272, "y": 296}], "properties": {"style": {"stroke": "#64748b", "strokeWidth": 1.8, "strokeLinecap": "round", "strokeLinejoin": "round"}, "textStyle": {"fill": "#64748b", "fontSize": 12, "background": {"fill": "#ffffff", "radius": 4, "stroke": "#dbe7f5", "strokeWidth": 1, "wrapPadding": "3px 6px"}, "fontWeight": 700}, "labelOffsetX": 0, "labelOffsetY": 0, "labelPlacement": "AUTO"}, "startPoint": {"x": 150, "y": 296}, "sourceNodeId": "start", "targetNodeId": "submit", "sourceAnchorId": "start_1", "targetAnchorId": "submit_3"}, {"id": "b21bca7d-971d-4c4b-bba6-b9b6ff656cbb", "type": "polyline", "endPoint": {"x": 544, "y": 296}, "pointsList": [{"x": 440, "y": 296}, {"x": 544, "y": 296}], "properties": {"style": {"stroke": "#64748b", "strokeWidth": 1.8, "strokeLinecap": "round", "strokeLinejoin": "round"}, "textStyle": {"fill": "#64748b", "fontSize": 12, "background": {"fill": "#ffffff", "radius": 4, "stroke": "#dbe7f5", "strokeWidth": 1, "wrapPadding": "3px 6px"}, "fontWeight": 700}, "labelOffsetX": 0, "labelOffsetY": 0, "labelPlacement": "AUTO"}, "startPoint": {"x": 440, "y": 296}, "sourceNodeId": "submit", "targetNodeId": "condition", "sourceAnchorId": "submit_1", "targetAnchorId": "condition_3"}, {"id": "5ff14879-5734-4952-a1d0-34730b4cd3c6", "text": "小额", "type": "polyline", "endPoint": {"x": 752, "y": 146}, "pointsList": [{"x": 648, "y": 296}, {"x": 690, "y": 296}, {"x": 690, "y": 146}, {"x": 752, "y": 146}], "properties": {"style": {"stroke": "#64748b", "strokeWidth": 1.8, "strokeLinecap": "round", "strokeLinejoin": "round"}, "textStyle": {"fill": "#64748b", "fontSize": 12, "background": {"fill": "#ffffff", "radius": 4, "stroke": "#dbe7f5", "strokeWidth": 1, "wrapPadding": "3px 6px"}, "fontWeight": 700}, "labelOffsetX": 0, "labelOffsetY": 0, "conditionType": "EXPRESSION", "labelPlacement": "START", "conditionExpression": "amount <= 5000"}, "startPoint": {"x": 648, "y": 296}, "sourceNodeId": "condition", "targetNodeId": "manager", "sourceAnchorId": "condition_1", "targetAnchorId": "manager_3"}, {"id": "2d063ca3-644e-4459-b314-541d54500fc0", "text": "默认/大额", "type": "polyline", "endPoint": {"x": 752, "y": 446}, "pointsList": [{"x": 648, "y": 296}, {"x": 690, "y": 296}, {"x": 690, "y": 446}, {"x": 752, "y": 446}], "properties": {"style": {"stroke": "#64748b", "strokeWidth": 1.8, "strokeLinecap": "round", "strokeLinejoin": "round"}, "textStyle": {"fill": "#64748b", "fontSize": 12, "background": {"fill": "#ffffff", "radius": 4, "stroke": "#dbe7f5", "strokeWidth": 1, "wrapPadding": "3px 6px"}, "fontWeight": 700}, "labelOffsetX": 0, "labelOffsetY": 0, "conditionType": "ALWAYS", "labelPlacement": "AUTO"}, "startPoint": {"x": 648, "y": 296}, "sourceNodeId": "condition", "targetNodeId": "office", "sourceAnchorId": "condition_1", "targetAnchorId": "office_3"}, {"id": "2cda4ef8-de6c-4ba5-a3b2-927a45faed65", "type": "polyline", "endPoint": {"x": 1042, "y": 296}, "pointsList": [{"x": 920, "y": 146}, {"x": 984, "y": 146}, {"x": 984, "y": 296}, {"x": 1042, "y": 296}], "properties": {"style": {"stroke": "#64748b", "strokeWidth": 1.8, "strokeLinecap": "round", "strokeLinejoin": "round"}, "textStyle": {"fill": "#64748b", "fontSize": 12, "background": {"fill": "#ffffff", "radius": 4, "stroke": "#dbe7f5", "strokeWidth": 1, "wrapPadding": "3px 6px"}, "fontWeight": 700}, "labelOffsetX": 0, "labelOffsetY": 0, "labelPlacement": "AUTO"}, "startPoint": {"x": 920, "y": 146}, "sourceNodeId": "manager", "targetNodeId": "end", "sourceAnchorId": "manager_1", "targetAnchorId": "end_3"}, {"id": "f35ee3ef-4518-4a67-8520-0a42556376a5", "type": "polyline", "endPoint": {"x": 1042, "y": 296}, "pointsList": [{"x": 920, "y": 446}, {"x": 984, "y": 446}, {"x": 984, "y": 296}, {"x": 1042, "y": 296}], "properties": {"style": {"stroke": "#64748b", "strokeWidth": 1.8, "strokeLinecap": "round", "strokeLinejoin": "round"}, "textStyle": {"fill": "#64748b", "fontSize": 12, "background": {"fill": "#ffffff", "radius": 4, "stroke": "#dbe7f5", "strokeWidth": 1, "wrapPadding": "3px 6px"}, "fontWeight": 700}, "labelOffsetX": 0, "labelOffsetY": 0, "labelPlacement": "AUTO"}, "startPoint": {"x": 920, "y": 446}, "sourceNodeId": "office", "targetNodeId": "end", "sourceAnchorId": "office_1", "targetAnchorId": "end_3"}], "nodes": [{"x": 116, "y": 296, "id": "start", "text": {"x": 116, "y": 296, "value": "开始"}, "type": "workflow-circle", "properties": {"r": 32, "style": {"fill": "#f8fbff", "stroke": "#2563eb", "strokeWidth": 2}, "width": 64, "height": 64, "nodeType": "START", "textStyle": {"fill": "#1e40af", "fontSize": 13, "fontWeight": 700}}}, {"x": 356, "y": 296, "id": "submit", "text": {"x": 356, "y": 296, "value": "提交采购"}, "type": "workflow-rect", "properties": {"style": {"fill": "#ffffff", "stroke": "#94a3b8", "strokeWidth": 1.8}, "width": 168, "height": 64, "radius": 8, "nodeType": "SUBMIT", "textStyle": {"fill": "#334155", "fontSize": 13, "fontWeight": 700}}}, {"x": 596, "y": 296, "id": "condition", "text": {"x": 596, "y": 296, "value": "金额判断"}, "type": "workflow-diamond", "properties": {"rx": 52, "ry": 52, "style": {"fill": "#fffbeb", "stroke": "#d97706", "strokeWidth": 2}, "width": 104, "height": 104, "nodeType": "CONDITION", "textStyle": {"fill": "#9a3412", "fontSize": 13, "fontWeight": 700}, "description": "按金额等条件分流"}}, {"x": 836, "y": 146, "id": "manager", "text": {"x": 836, "y": 146, "value": "负责人审批"}, "type": "workflow-rect", "properties": {"style": {"fill": "#f8fbff", "stroke": "#2563eb", "strokeWidth": 2}, "width": 168, "height": 64, "radius": 8, "nodeType": "APPROVAL", "textStyle": {"fill": "#1e3a8a", "fontSize": 13, "fontWeight": 700}, "approveType": "ANY_ONE", "description": "发起人所在部门负责人审批", "approverType": "DEPT_LEADER"}}, {"x": 836, "y": 446, "id": "office", "text": {"x": 836, "y": 446, "value": "总经办审批"}, "type": "workflow-rect", "properties": {"style": {"fill": "#f8fbff", "stroke": "#2563eb", "strokeWidth": 2}, "width": 168, "height": 64, "radius": 8, "nodeType": "APPROVAL", "textStyle": {"fill": "#1e3a8a", "fontSize": 13, "fontWeight": 700}, "approveType": "ANY_ONE", "assigneeIds": ["202604280101000001"], "description": "总经办审批大额采购", "approverType": "USER"}}, {"x": 1076, "y": 296, "id": "end", "text": {"x": 1076, "y": 296, "value": "结束"}, "type": "workflow-circle", "properties": {"r": 32, "style": {"fill": "#f8fafc", "stroke": "#475569", "strokeWidth": 2}, "width": 64, "height": 64, "nodeType": "END", "textStyle": {"fill": "#334155", "fontSize": 13, "fontWeight": 700}}}]}', 'PUBLISHED', 202604280101000001, NOW(), 202604280101000001, NOW(), 202604280101000001, NOW()),
    (202604280108010004, 202604280108000004, 1, '{"edges": [{"id": "7a3eee32-7b4d-438d-82f0-c1da5c9b1bbb", "type": "polyline", "endPoint": {"x": 272, "y": 296}, "pointsList": [{"x": 150, "y": 296}, {"x": 272, "y": 296}], "properties": {"style": {"stroke": "#64748b", "strokeWidth": 1.8, "strokeLinecap": "round", "strokeLinejoin": "round"}, "textStyle": {"fill": "#64748b", "fontSize": 12, "background": {"fill": "#ffffff", "radius": 4, "stroke": "#dbe7f5", "strokeWidth": 1, "wrapPadding": "3px 6px"}, "fontWeight": 700}, "labelOffsetX": 0, "labelOffsetY": 0, "labelPlacement": "AUTO"}, "startPoint": {"x": 150, "y": 296}, "sourceNodeId": "start", "targetNodeId": "submit", "sourceAnchorId": "start_1", "targetAnchorId": "submit_3"}, {"id": "8a0b517d-4660-4a85-a72c-53af9bb55277", "type": "polyline", "endPoint": {"x": 512, "y": 296}, "pointsList": [{"x": 440, "y": 296}, {"x": 512, "y": 296}], "properties": {"style": {"stroke": "#64748b", "strokeWidth": 1.8, "strokeLinecap": "round", "strokeLinejoin": "round"}, "textStyle": {"fill": "#64748b", "fontSize": 12, "background": {"fill": "#ffffff", "radius": 4, "stroke": "#dbe7f5", "strokeWidth": 1, "wrapPadding": "3px 6px"}, "fontWeight": 700}, "labelOffsetX": 0, "labelOffsetY": 0, "labelPlacement": "AUTO"}, "startPoint": {"x": 440, "y": 296}, "sourceNodeId": "submit", "targetNodeId": "ops", "sourceAnchorId": "submit_1", "targetAnchorId": "ops_3"}, {"id": "b1b9e1bc-63ac-4321-acbb-26c2a8aa0313", "type": "polyline", "endPoint": {"x": 752, "y": 296}, "pointsList": [{"x": 680, "y": 296}, {"x": 752, "y": 296}], "properties": {"style": {"stroke": "#64748b", "strokeWidth": 1.8, "strokeLinecap": "round", "strokeLinejoin": "round"}, "textStyle": {"fill": "#64748b", "fontSize": 12, "background": {"fill": "#ffffff", "radius": 4, "stroke": "#dbe7f5", "strokeWidth": 1, "wrapPadding": "3px 6px"}, "fontWeight": 700}, "labelOffsetX": 0, "labelOffsetY": 0, "conditionType": "ALWAYS", "labelPlacement": "AUTO", "conditionExpression": ""}, "startPoint": {"x": 680, "y": 296}, "sourceNodeId": "ops", "targetNodeId": "cc_audit", "sourceAnchorId": "ops_1", "targetAnchorId": "cc_audit_3"}, {"id": "871b06fd-6f89-4ef5-a919-3c63bba3e650", "type": "polyline", "endPoint": {"x": 1042, "y": 296}, "pointsList": [{"x": 920, "y": 296}, {"x": 1042, "y": 296}], "properties": {"style": {"stroke": "#64748b", "strokeWidth": 1.8, "strokeLinecap": "round", "strokeLinejoin": "round"}, "textStyle": {"fill": "#64748b", "fontSize": 12, "background": {"fill": "#ffffff", "radius": 4, "stroke": "#dbe7f5", "strokeWidth": 1, "wrapPadding": "3px 6px"}, "fontWeight": 700}, "labelOffsetX": 0, "labelOffsetY": 0, "labelPlacement": "AUTO"}, "startPoint": {"x": 920, "y": 296}, "sourceNodeId": "cc_audit", "targetNodeId": "end", "sourceAnchorId": "cc_audit_1", "targetAnchorId": "end_3"}], "nodes": [{"x": 116, "y": 296, "id": "start", "text": {"x": 116, "y": 296, "value": "开始"}, "type": "workflow-circle", "properties": {"r": 32, "style": {"fill": "#f8fbff", "stroke": "#2563eb", "strokeWidth": 2}, "width": 64, "height": 64, "nodeType": "START", "textStyle": {"fill": "#1e40af", "fontSize": 13, "fontWeight": 700}}}, {"x": 356, "y": 296, "id": "submit", "text": {"x": 356, "y": 296, "value": "提交报修"}, "type": "workflow-rect", "properties": {"style": {"fill": "#ffffff", "stroke": "#94a3b8", "strokeWidth": 1.8}, "width": 168, "height": 64, "radius": 8, "nodeType": "SUBMIT", "textStyle": {"fill": "#334155", "fontSize": 13, "fontWeight": 700}}}, {"x": 596, "y": 296, "id": "ops", "text": {"x": 596, "y": 296, "value": "运维受理"}, "type": "workflow-rect", "properties": {"style": {"fill": "#f8fbff", "stroke": "#2563eb", "strokeWidth": 2}, "width": 168, "height": 64, "radius": 8, "nodeType": "APPROVAL", "textStyle": {"fill": "#1e3a8a", "fontSize": 13, "fontWeight": 700}, "approveType": "ANY_ONE", "assigneeIds": ["202604280101000024"], "description": "运维确认故障、安排维修并记录处理结果", "approverType": "USER"}}, {"x": 836, "y": 296, "id": "cc_audit", "text": {"x": 836, "y": 296, "value": "审计备案"}, "type": "workflow-rect", "properties": {"style": {"fill": "#fbfefc", "stroke": "#16a34a", "strokeWidth": 2, "strokeDasharray": "5 4"}, "width": 168, "height": 64, "radius": 8, "nodeType": "CC", "ccUserIds": ["202604280101000017"], "textStyle": {"fill": "#166534", "fontSize": 13, "fontWeight": 700}, "description": "报修通过后抄送审计备案"}}, {"x": 1076, "y": 296, "id": "end", "text": {"x": 1076, "y": 296, "value": "结束"}, "type": "workflow-circle", "properties": {"r": 32, "style": {"fill": "#f8fafc", "stroke": "#475569", "strokeWidth": 2}, "width": 64, "height": 64, "nodeType": "END", "textStyle": {"fill": "#334155", "fontSize": 13, "fontWeight": 700}}}]}', 'PUBLISHED', 202604280101000001, NOW(), 202604280101000001, NOW(), 202604280101000001, NOW());

INSERT INTO `wf_ru_process_instance` (`id`, `definition_id`, `version_id`, `process_key`, `business_type`, `business_id`, `title`, `initiator_id`, `current_node_key`, `status`, `variables_json`, `definition_snapshot_json`, `started_at`, `ended_at`, `created_by`, `created_at`, `updated_by`, `updated_at`)
SELECT 202604280108030001, 202604280108000001, 202604280108010001, 'leave_approval', 'leave', 'LEAVE-202604-001', '林员工请假审批', 202604280101000026, 'manager', 'RUNNING',
       JSON_OBJECT('leaveType', 'ANNUAL', 'startTime', DATE_FORMAT(DATE_ADD(NOW(), INTERVAL 1 DAY), '%Y-%m-%dT%H:%i:%s'), 'endTime', DATE_FORMAT(DATE_ADD(NOW(), INTERVAL 3 DAY), '%Y-%m-%dT%H:%i:%s'), 'days', 2.00, 'duration', 2.00, 'reason', '家庭事务，申请年假。', 'applicantDeptId', 202604280103000103),
       v.`graph_json`, DATE_SUB(NOW(), INTERVAL 2 HOUR), NULL, 202604280101000026, DATE_SUB(NOW(), INTERVAL 2 HOUR), 202604280101000001, NOW()
FROM `wf_process_definition_version` v WHERE v.`id` = 202604280108010001
UNION ALL
SELECT 202604280108030002, 202604280108000002, 202604280108010002, 'expense_approval', 'expense', 'EXP-202604-001', '陈经理差旅报销审批', 202604280101000025, 'finance', 'RUNNING',
       JSON_OBJECT('expenseType', 'TRAVEL', 'amount', 3680.50, 'occurredDate', DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 3 DAY), '%Y-%m-%d'), 'reason', '客户现场差旅费用报销。', 'applicantDeptId', 202604280103000103),
       v.`graph_json`, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, 202604280101000025, DATE_SUB(NOW(), INTERVAL 1 DAY), 202604280101000001, NOW()
FROM `wf_process_definition_version` v WHERE v.`id` = 202604280108010002
UNION ALL
SELECT 202604280108030004, 202604280108000004, 202604280108010004, 'repair_approval', 'repair', 'RP-202604-001', '林员工会议室投影仪报修', 202604280101000026, 'ops', 'RUNNING',
       JSON_OBJECT('repairRequestId', 202604280109000003, 'repairType', 'DEVICE', 'assetName', '会议室投影仪', 'urgency', 'HIGH', 'faultTime', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 3 HOUR), '%Y-%m-%dT%H:%i:%s'), 'location', '深圳总部 12F 会议室', 'description', '投影仪无法开机，影响下午客户演示。', 'attachmentCount', 0, 'repairAttachments', JSON_ARRAY(), 'applicantDeptId', 202604280103000103),
       v.`graph_json`, DATE_SUB(NOW(), INTERVAL 3 HOUR), NULL, 202604280101000026, DATE_SUB(NOW(), INTERVAL 3 HOUR), 202604280101000001, NOW()
FROM `wf_process_definition_version` v WHERE v.`id` = 202604280108010004;

INSERT INTO `wf_hi_process_instance` (`id`, `definition_id`, `version_id`, `process_key`, `business_type`, `business_id`, `title`, `initiator_id`, `current_node_key`, `status`, `variables_json`, `definition_snapshot_json`, `started_at`, `ended_at`, `created_by`, `created_at`, `updated_by`, `updated_at`)
SELECT 202604280108030003, 202604280108000003, 202604280108010003, 'purchase_approval', 'purchase', 'PO-202604-001', '李产品采购审批', 202604280101000016, NULL, 'APPROVED',
       JSON_OBJECT('itemName', '研发测试设备', 'category', 'IT_EQUIPMENT', 'quantity', 1, 'amount', 12800.00, 'estimatedAmount', 12800.00, 'requiredDate', DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL 7 DAY), '%Y-%m-%d'), 'reason', '补充研发测试环境设备。', 'applicantDeptId', 202604280103000102),
       v.`graph_json`, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 202604280101000016, DATE_SUB(NOW(), INTERVAL 2 DAY), 202604280101000001, NOW()
FROM `wf_process_definition_version` v WHERE v.`id` = 202604280108010003;

INSERT INTO `biz_leave_request` (`id`, `request_no`, `applicant_id`, `applicant_dept_id`, `leave_type`, `start_time`, `end_time`, `days`, `reason`, `status`, `workflow_instance_id`, `created_by`, `created_at`, `updated_by`, `updated_at`) VALUES
    (202604280109000001, 'LEAVE-202604-001', 202604280101000026, 202604280103000103, 'ANNUAL', DATE_ADD(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 3 DAY), 2.00, '家庭事务，申请年假。', 'APPROVING', 202604280108030001, 202604280101000026, DATE_SUB(NOW(), INTERVAL 2 HOUR), 202604280101000026, NOW());

INSERT INTO `biz_purchase_request` (`id`, `request_no`, `applicant_id`, `applicant_dept_id`, `item_name`, `category`, `quantity`, `estimated_amount`, `required_date`, `reason`, `status`, `workflow_instance_id`, `created_by`, `created_at`, `updated_by`, `updated_at`) VALUES
    (202604280109000002, 'PO-202604-001', 202604280101000016, 202604280103000102, '研发测试设备', 'IT_EQUIPMENT', 1, 12800.00, DATE_ADD(CURDATE(), INTERVAL 7 DAY), '补充研发测试环境设备。', 'APPROVED', 202604280108030003, 202604280101000016, DATE_SUB(NOW(), INTERVAL 2 DAY), 202604280101000016, NOW());

INSERT INTO `biz_repair_request` (`id`, `request_no`, `applicant_id`, `applicant_dept_id`, `repair_type`, `asset_name`, `urgency`, `fault_time`, `location`, `description`, `attachments_json`, `status`, `workflow_instance_id`, `created_by`, `created_at`, `updated_by`, `updated_at`) VALUES
    (202604280109000003, 'RP-202604-001', 202604280101000026, 202604280103000103, 'DEVICE', '会议室投影仪', 'HIGH', DATE_SUB(NOW(), INTERVAL 3 HOUR), '深圳总部 12F 会议室', '投影仪无法开机，影响下午客户演示。', '[]', 'APPROVING', 202604280108030004, 202604280101000026, DATE_SUB(NOW(), INTERVAL 3 HOUR), 202604280101000026, NOW());

INSERT INTO `wf_ru_task` (`id`, `instance_id`, `node_key`, `node_name`, `assignee_id`, `assignee_dept_id`, `status`, `approve_comment`, `started_at`, `finished_at`, `created_by`, `created_at`, `updated_by`, `updated_at`) VALUES
    (202604280108020001, 202604280108030001, 'manager', '部门负责人审批', 202604280101000025, 202604280103000103, 'PENDING', NULL, NOW(), NULL, 202604280101000026, NOW(), 202604280101000001, NOW()),
    (202604280108020002, 202604280108030002, 'finance', '财务复核', 202604280101000017, 202604280103000105, 'PENDING', NULL, NOW(), NULL, 202604280101000025, NOW(), 202604280101000001, NOW()),
    (202604280108020004, 202604280108030004, 'ops', '运维受理', 202604280101000024, 202604280103000104, 'PENDING', NULL, NOW(), NULL, 202604280101000026, NOW(), 202604280101000001, NOW());

INSERT INTO `wf_ru_cc` (`id`, `instance_id`, `node_key`, `node_name`, `receiver_id`, `read_status`, `read_at`, `created_at`) VALUES
    (202604280108050001, 202604280108030004, 'start', '发起抄送', 202604280101000017, 0, NULL, NOW());

INSERT INTO `wf_hi_task` (`id`, `instance_id`, `node_key`, `node_name`, `assignee_id`, `assignee_dept_id`, `status`, `approve_comment`, `started_at`, `finished_at`, `created_by`, `created_at`, `updated_by`, `updated_at`) VALUES
    (202604280108020003, 202604280108030003, 'manager', '负责人审批', 202604280101000025, 202604280103000103, 'APPROVED', '同意，预算内执行。', DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 202604280101000016, NOW(), 202604280101000001, NOW());

INSERT INTO `wf_hi_event` (`id`, `instance_id`, `task_id`, `operator_id`, `action`, `from_node_key`, `to_node_key`, `target_user_id`, `comment`, `created_at`) VALUES
    (202604280108040001, 202604280108030001, NULL, 202604280101000026, 'SUBMIT', NULL, 'manager', NULL, '提交请假申请。', DATE_SUB(NOW(), INTERVAL 2 HOUR)),
    (202604280108040002, 202604280108030002, NULL, 202604280101000025, 'SUBMIT', NULL, 'manager', NULL, '提交差旅报销。', DATE_SUB(NOW(), INTERVAL 1 DAY)),
    (202604280108040003, 202604280108030002, 202604280108020002, 202604280101000025, 'APPROVE', 'manager', 'finance', NULL, '负责人已同意，转财务复核。', DATE_SUB(NOW(), INTERVAL 12 HOUR)),
    (202604280108040004, 202604280108030003, NULL, 202604280101000016, 'SUBMIT', NULL, 'manager', NULL, '提交采购申请。', DATE_SUB(NOW(), INTERVAL 2 DAY)),
    (202604280108040005, 202604280108030003, 202604280108020003, 202604280101000025, 'APPROVE', 'manager', NULL, NULL, '同意，预算内执行。', DATE_SUB(NOW(), INTERVAL 1 DAY)),
    (202604280108040006, 202604280108030004, NULL, 202604280101000026, 'SUBMIT', NULL, 'ops', NULL, '提交报修申请。', DATE_SUB(NOW(), INTERVAL 3 HOUR));

INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_resource_id`, `create_by`, `create_dept_id`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `remark`)
SELECT 202604290106010000 + ROW_NUMBER() OVER (ORDER BY permission_resource.`id`),
       role_table.`id`,
       permission_resource.`id`,
       202604280101000001,
       202604280103000001,
       NOW(),
       202604280101000001,
       NOW(),
       0,
       0,
       '超级管理员内置授权'
FROM `sys_role` role_table
JOIN `sys_menu` permission_resource ON permission_resource.`deleted` = 0
                         AND permission_resource.`enable` = 1
WHERE role_table.`role_code` = 'admin'
  AND role_table.`deleted` = 0;

INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_resource_id`, `create_by`, `create_dept_id`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `remark`)
SELECT 202604290106020000 + ROW_NUMBER() OVER (ORDER BY permission_resource.`id`),
       role_table.`id`,
       permission_resource.`id`,
       202604280101000001,
       202604280103000001,
       NOW(),
       202604280101000001,
       NOW(),
       0,
       0,
       '部门负责人默认授权'
FROM `sys_role` role_table
JOIN `sys_menu` permission_resource ON permission_resource.`deleted` = 0
                         AND permission_resource.`enable` = 1
                         AND (
                             permission_resource.`permission_code` IN (
                                 'dashboard:view',
                                 'report:view',
                                 'sys:user:list',
                                 'sys:user:add',
                                 'sys:user:edit',
                                 'sys:user:import',
                                 'sys:user:export',
                                 'sys:user:reset-password',
                                 'sys:dept:list',
                                 'sys:dept:edit',
                                 'workflow:instance:start',
                                 'workflow:view',
                                 'workflow:task:approve',
                                 'workflow:task:reject',
                                 'workflow:task:transfer',
                                 'workflow:task:delegate',
                                 'workflow:task:return',
                                 'workflow:task:add-sign',
                                 'workflow:task:remove-sign',
                                 'workflow:task:remind',
                                 'message:view',
                                 'message:read'
                             )
                             OR EXISTS (
                                 SELECT 1
                                 FROM `sys_menu` child
                                 LEFT JOIN `sys_menu` parent ON parent.`id` = child.`pid`
                                                              AND parent.`deleted` = 0
                                                              AND parent.`enable` = 1
                                 WHERE child.`deleted` = 0
                                   AND child.`enable` = 1
                                   AND child.`permission_code` IN (
                                       'dashboard:view',
                                       'report:view',
                                       'sys:user:list',
                                       'sys:user:add',
                                       'sys:user:edit',
                                       'sys:user:import',
                                       'sys:user:export',
                                       'sys:user:reset-password',
                                       'sys:dept:list',
                                       'sys:dept:edit',
                                       'workflow:instance:start',
                                       'workflow:view',
                                       'workflow:task:approve',
                                       'workflow:task:reject',
                                       'workflow:task:transfer',
                                       'workflow:task:delegate',
                                       'workflow:task:return',
                                       'workflow:task:add-sign',
                                       'workflow:task:remove-sign',
                                       'workflow:task:remind',
                                       'message:view',
                                       'message:read'
                                   )
                                   AND (child.`pid` = permission_resource.`id` OR parent.`pid` = permission_resource.`id`)
                             )
                         )
WHERE role_table.`role_code` = 'dept_manager'
  AND role_table.`deleted` = 0;

INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_resource_id`, `create_by`, `create_dept_id`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `remark`)
SELECT 202604290106030000 + ROW_NUMBER() OVER (ORDER BY permission_resource.`id`),
       role_table.`id`,
       permission_resource.`id`,
       202604280101000001,
       202604280103000001,
       NOW(),
       202604280101000001,
       NOW(),
       0,
       0,
       '普通员工默认授权'
FROM `sys_role` role_table
JOIN `sys_menu` permission_resource ON permission_resource.`deleted` = 0
                         AND permission_resource.`enable` = 1
                         AND (
                             permission_resource.`permission_code` IN (
                                 'dashboard:view',
                                 'workflow:instance:start',
                                 'workflow:view',
                                 'workflow:instance:revoke',
                                 'workflow:task:approve',
                                 'workflow:task:remind',
                                 'message:view',
                                 'message:read'
                             )
                             OR EXISTS (
                                 SELECT 1
                                 FROM `sys_menu` child
                                 LEFT JOIN `sys_menu` parent ON parent.`id` = child.`pid`
                                                              AND parent.`deleted` = 0
                                                              AND parent.`enable` = 1
                                 WHERE child.`deleted` = 0
                                   AND child.`enable` = 1
                                   AND child.`permission_code` IN (
                                       'dashboard:view',
                                       'workflow:instance:start',
                                       'workflow:view',
                                       'workflow:instance:revoke',
                                       'workflow:task:approve',
                                       'workflow:task:remind',
                                       'message:view',
                                       'message:read'
                                   )
                                   AND (child.`pid` = permission_resource.`id` OR parent.`pid` = permission_resource.`id`)
                             )
                         )
WHERE role_table.`role_code` = 'staff'
  AND role_table.`deleted` = 0;

INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_resource_id`, `create_by`, `create_dept_id`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `remark`)
SELECT 202604290106040000 + ROW_NUMBER() OVER (ORDER BY permission_resource.`id`),
       role_table.`id`,
       permission_resource.`id`,
       202604280101000001,
       202604280103000001,
       NOW(),
       202604280101000001,
       NOW(),
       0,
       0,
       '运维人员默认授权'
FROM `sys_role` role_table
JOIN `sys_menu` permission_resource ON permission_resource.`deleted` = 0
                         AND permission_resource.`enable` = 1
                         AND (
                             permission_resource.`permission_code` IN (
                                 'dashboard:view',
                                 'monitor:server:view',
                                 'monitor:online:view',
                                 'auth:session:revoke',
                                 'monitor:cache:view',
                                 'monitor:cache:clear',
                                 'monitor:weblog:view',
                                 'monitor:weblog:level',
                                 'schedule:job:list',
                                 'schedule:job:edit',
                                 'batch:task:list',
                                 'batch:task:manage',
                                 'workflow:instance:start',
                                 'workflow:view',
                                 'workflow:task:approve',
                                 'workflow:task:reject',
                                 'workflow:task:transfer',
                                 'workflow:task:delegate',
                                 'workflow:task:return',
                                 'workflow:task:add-sign',
                                 'workflow:task:remove-sign',
                                 'message:view',
                                 'message:read'
                             )
                             OR EXISTS (
                                 SELECT 1
                                 FROM `sys_menu` child
                                 LEFT JOIN `sys_menu` parent ON parent.`id` = child.`pid`
                                                              AND parent.`deleted` = 0
                                                              AND parent.`enable` = 1
                                 WHERE child.`deleted` = 0
                                   AND child.`enable` = 1
                                   AND child.`permission_code` IN (
                                       'dashboard:view',
                                       'monitor:server:view',
                                       'monitor:online:view',
                                       'auth:session:revoke',
                                       'monitor:cache:view',
                                       'monitor:cache:clear',
                                       'monitor:weblog:view',
                                       'monitor:weblog:level',
                                       'schedule:job:list',
                                       'schedule:job:edit',
                                       'batch:task:list',
                                       'batch:task:manage',
                                       'workflow:instance:start',
                                       'workflow:view',
                                       'workflow:task:approve',
                                       'workflow:task:reject',
                                       'workflow:task:transfer',
                                       'workflow:task:delegate',
                                       'workflow:task:return',
                                       'workflow:task:add-sign',
                                       'workflow:task:remove-sign',
                                       'message:view',
                                       'message:read'
                                   )
                                   AND (child.`pid` = permission_resource.`id` OR parent.`pid` = permission_resource.`id`)
                             )
                         )
WHERE role_table.`role_code` = 'ops'
  AND role_table.`deleted` = 0;

INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_resource_id`, `create_by`, `create_dept_id`, `create_time`, `update_by`, `update_time`, `deleted`, `version`, `remark`)
SELECT 202604290106050000 + ROW_NUMBER() OVER (ORDER BY permission_resource.`id`),
       role_table.`id`,
       permission_resource.`id`,
       202604280101000001,
       202604280103000001,
       NOW(),
       202604280101000001,
       NOW(),
       0,
       0,
       '审计人员默认授权'
FROM `sys_role` role_table
JOIN `sys_menu` permission_resource ON permission_resource.`deleted` = 0
                         AND permission_resource.`enable` = 1
                         AND (
                             permission_resource.`permission_code` IN (
                                 'dashboard:view',
                                 'report:view',
                                 'sys:role:list',
                                 'sys:menu:list',
                                 'audit:behavior:view',
                                 'workflow:instance:start',
                                 'workflow:view',
                                 'workflow:task:approve',
                                 'workflow:task:reject',
                                 'workflow:task:remind',
                                 'message:view',
                                 'message:read'
                             )
                             OR EXISTS (
                                 SELECT 1
                                 FROM `sys_menu` child
                                 LEFT JOIN `sys_menu` parent ON parent.`id` = child.`pid`
                                                              AND parent.`deleted` = 0
                                                              AND parent.`enable` = 1
                                 WHERE child.`deleted` = 0
                                   AND child.`enable` = 1
                                   AND child.`permission_code` IN (
                                       'dashboard:view',
                                       'report:view',
                                       'sys:role:list',
                                       'sys:menu:list',
                                       'audit:behavior:view',
                                       'workflow:instance:start',
                                       'workflow:view',
                                       'workflow:task:approve',
                                       'workflow:task:reject',
                                       'workflow:task:remind',
                                       'message:view',
                                       'message:read'
                                   )
                                   AND (child.`pid` = permission_resource.`id` OR parent.`pid` = permission_resource.`id`)
                             )
                         )
WHERE role_table.`role_code` = 'auditor'
  AND role_table.`deleted` = 0;
