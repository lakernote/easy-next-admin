CREATE TABLE infra_distributed_lock (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  lock_key VARCHAR(50) NOT NULL UNIQUE,
  token VARCHAR(50) NOT NULL,
  thread_id VARCHAR(50) NOT NULL,
  expire BIGINT NOT NULL
);

CREATE TABLE infra_idempotent_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  `key` VARCHAR(255) NOT NULL UNIQUE,
  expire_time TIMESTAMP NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE infra_local_message (
  id BIGINT PRIMARY KEY,
  name VARCHAR(100),
  status VARCHAR(32),
  create_time TIMESTAMP,
  update_time TIMESTAMP,
  retry_count INT DEFAULT 0,
  param TEXT,
  process_tag VARCHAR(128),
  create_by BIGINT,
  create_dept_id BIGINT,
  update_by BIGINT,
  deleted TINYINT DEFAULT 0,
  version INT DEFAULT 0,
  remark VARCHAR(500)
);

CREATE TABLE audit_api_log (
  log_id BIGINT PRIMARY KEY,
  user_id BIGINT,
  trace_id VARCHAR(64),
  ip VARCHAR(64),
  city VARCHAR(64),
  client VARCHAR(255),
  uri VARCHAR(255),
  method VARCHAR(20),
  request TEXT,
  response TEXT,
  status TINYINT,
  cost INT,
  create_by BIGINT,
  create_dept_id BIGINT,
  create_time TIMESTAMP,
  update_by BIGINT,
  update_time TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  version INT DEFAULT 0,
  remark VARCHAR(500)
);

CREATE TABLE audit_login_log (
  id BIGINT PRIMARY KEY,
  user_id BIGINT,
  user_name VARCHAR(80),
  login_result VARCHAR(20),
  fail_reason VARCHAR(255),
  ip VARCHAR(64),
  user_agent VARCHAR(500),
  client_type VARCHAR(40),
  trace_id VARCHAR(64),
  login_time TIMESTAMP
);

CREATE TABLE audit_operation_log (
  id BIGINT PRIMARY KEY,
  trace_id VARCHAR(64),
  module VARCHAR(80),
  action VARCHAR(80),
  operator_id BIGINT,
  operator_name VARCHAR(80),
  request_method VARCHAR(20),
  request_uri VARCHAR(255),
  request_params TEXT,
  response_status VARCHAR(20),
  error_message VARCHAR(1000),
  ip VARCHAR(64),
  user_agent VARCHAR(500),
  duration_ms INT,
  created_at TIMESTAMP
);

CREATE TABLE audit_data_change_log (
  id BIGINT PRIMARY KEY,
  trace_id VARCHAR(64),
  biz_type VARCHAR(80),
  biz_id VARCHAR(80),
  table_name VARCHAR(80),
  change_type VARCHAR(20),
  before_json TEXT,
  after_json TEXT,
  changed_fields VARCHAR(1000),
  operator_id BIGINT,
  created_at TIMESTAMP
);

CREATE TABLE audit_error_log (
  id BIGINT PRIMARY KEY,
  trace_id VARCHAR(64),
  request_uri VARCHAR(255),
  request_method VARCHAR(20),
  error_type VARCHAR(255),
  error_message VARCHAR(1000),
  stack_trace TEXT,
  operator_id BIGINT,
  created_at TIMESTAMP
);

CREATE TABLE user_message (
  id BIGINT PRIMARY KEY,
  receiver_id BIGINT,
  sender_id BIGINT,
  title VARCHAR(120),
  content VARCHAR(1000),
  category VARCHAR(40),
  level VARCHAR(20),
  biz_type VARCHAR(80),
  biz_id VARCHAR(100),
  link VARCHAR(255),
  read_status TINYINT,
  read_at TIMESTAMP,
  created_at TIMESTAMP
);

CREATE TABLE observability_remote_call_log (
  id BIGINT PRIMARY KEY,
  trace_id VARCHAR(64),
  target VARCHAR(160),
  method VARCHAR(160),
  success TINYINT,
  duration_ms BIGINT,
  error_message VARCHAR(500),
  create_by BIGINT,
  create_dept_id BIGINT,
  create_time TIMESTAMP,
  update_by BIGINT,
  update_time TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  version INT DEFAULT 0,
  remark VARCHAR(500)
);

CREATE TABLE sys_dept (
  id BIGINT PRIMARY KEY,
  dept_name VARCHAR(100),
  full_name VARCHAR(255),
  address VARCHAR(255),
  pid BIGINT,
  tree_path VARCHAR(500),
  leader_user_id BIGINT,
  status TINYINT,
  sort INT,
  create_by BIGINT,
  create_dept_id BIGINT,
  create_time TIMESTAMP,
  update_by BIGINT,
  update_time TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  version INT DEFAULT 0,
  remark VARCHAR(500)
);

CREATE TABLE sys_role (
  id BIGINT PRIMARY KEY,
  role_name VARCHAR(80),
  role_code VARCHAR(80),
  details VARCHAR(255),
  enable TINYINT,
  role_level INT,
  data_scope VARCHAR(32) NOT NULL DEFAULT 'SELF' CHECK (data_scope IN ('ALL','DEPT_AND_CHILDREN','DEPT','SELF','DEPT_SETS')),
  create_by BIGINT,
  create_dept_id BIGINT,
  create_time TIMESTAMP,
  update_by BIGINT,
  update_time TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  version INT DEFAULT 0,
  remark VARCHAR(500),
  UNIQUE (role_code, deleted)
);

CREATE TABLE sys_user (
  id BIGINT PRIMARY KEY,
  user_name VARCHAR(80),
  password_hash VARCHAR(100),
  nick_name VARCHAR(80),
  dept_id BIGINT,
  manager_user_id BIGINT,
  phone VARCHAR(32),
  enable TINYINT,
  email VARCHAR(120),
  avatar VARCHAR(255),
  create_by BIGINT,
  create_dept_id BIGINT,
  create_time TIMESTAMP,
  update_by BIGINT,
  update_time TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  version INT DEFAULT 0,
  permission_version BIGINT DEFAULT 1,
  remark VARCHAR(500),
  employee_no VARCHAR(64),
  real_name VARCHAR(80),
  position_name VARCHAR(80),
  last_login_time TIMESTAMP,
  UNIQUE (user_name, deleted),
  UNIQUE (employee_no, deleted)
);

CREATE TABLE sys_menu (
  id BIGINT PRIMARY KEY,
  pid BIGINT,
  title VARCHAR(80),
  icon VARCHAR(80),
  href VARCHAR(255),
  sort INT,
  enable TINYINT,
  remark VARCHAR(500),
  create_time TIMESTAMP,
  update_by BIGINT,
  update_time TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  version INT DEFAULT 0,
  type INT,
  permission_code VARCHAR(160),
  component_path VARCHAR(255),
  visible TINYINT,
  create_by BIGINT,
  create_dept_id BIGINT
);

CREATE TABLE sys_user_role (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  create_by BIGINT,
  create_dept_id BIGINT,
  create_time TIMESTAMP,
  update_by BIGINT,
  update_time TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  version INT DEFAULT 0,
  remark VARCHAR(500),
  UNIQUE (user_id, role_id)
);

CREATE TABLE sys_role_permission (
  id BIGINT PRIMARY KEY,
  role_id BIGINT NOT NULL,
  permission_resource_id BIGINT NOT NULL,
  create_by BIGINT,
  create_dept_id BIGINT,
  create_time TIMESTAMP,
  update_by BIGINT,
  update_time TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  version INT DEFAULT 0,
  remark VARCHAR(500),
  UNIQUE (role_id, permission_resource_id)
);

CREATE TABLE sys_role_dept (
  id BIGINT PRIMARY KEY,
  role_id BIGINT NOT NULL,
  dept_id BIGINT NOT NULL,
  create_by BIGINT,
  create_dept_id BIGINT,
  create_time TIMESTAMP,
  update_by BIGINT,
  update_time TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  version INT DEFAULT 0,
  remark VARCHAR(500),
  UNIQUE (role_id, dept_id, deleted)
);

CREATE TABLE sys_file (
  id BIGINT PRIMARY KEY,
  user_id BIGINT,
  nick_name VARCHAR(50),
  file_path VARCHAR(255),
  file_name VARCHAR(255),
  original_name VARCHAR(255),
  storage_name VARCHAR(255),
  storage_type VARCHAR(20) DEFAULT 'LOCAL',
  file_size BIGINT DEFAULT 0,
  content_type VARCHAR(120),
  business_type VARCHAR(80),
  business_id BIGINT,
  create_by BIGINT,
  create_dept_id BIGINT,
  create_time TIMESTAMP,
  update_by BIGINT,
  update_time TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  version INT DEFAULT 0,
  remark VARCHAR(500)
);

CREATE TABLE schedule_job (
  job_id BIGINT PRIMARY KEY,
  job_code VARCHAR(100),
  job_name VARCHAR(120),
  job_class_name VARCHAR(255),
  cron_expression VARCHAR(80),
  create_time TIMESTAMP,
  enable TINYINT,
  job_state INT,
  create_by BIGINT,
  create_dept_id BIGINT,
  update_by BIGINT,
  update_time TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  version INT DEFAULT 0,
  remark VARCHAR(500),
  UNIQUE (job_code)
);

CREATE TABLE schedule_job_log (
  job_log_id BIGINT PRIMARY KEY,
  job_code VARCHAR(100),
  start_time TIMESTAMP,
  end_time TIMESTAMP,
  status INT,
  cost INT,
  thread_name VARCHAR(120),
  create_by BIGINT,
  create_dept_id BIGINT,
  create_time TIMESTAMP,
  update_by BIGINT,
  update_time TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  version INT DEFAULT 0,
  remark VARCHAR(500)
);

CREATE TABLE wf_process_definition (
  id BIGINT PRIMARY KEY,
  process_key VARCHAR(100),
  process_name VARCHAR(120),
  current_version INT,
  status VARCHAR(20),
  remark VARCHAR(500),
  created_by BIGINT,
  created_at TIMESTAMP,
  updated_by BIGINT,
  updated_at TIMESTAMP
);

CREATE TABLE wf_process_definition_version (
  id BIGINT PRIMARY KEY,
  definition_id BIGINT,
  version INT,
  graph_json CLOB,
  status VARCHAR(20),
  published_by BIGINT,
  published_at TIMESTAMP,
  created_by BIGINT,
  created_at TIMESTAMP,
  updated_by BIGINT,
  updated_at TIMESTAMP,
  UNIQUE (definition_id, version)
);

CREATE TABLE wf_process_node (
  id BIGINT PRIMARY KEY,
  version_id BIGINT NOT NULL,
  node_key VARCHAR(100) NOT NULL,
  node_name VARCHAR(100) NOT NULL,
  node_type VARCHAR(32) NOT NULL,
  approve_type VARCHAR(32),
  approver_type VARCHAR(32),
  approver_value VARCHAR(500),
  allow_transfer TINYINT DEFAULT 1,
  allow_delegate TINYINT DEFAULT 1,
  allow_add_sign TINYINT DEFAULT 1,
  allow_remove_sign TINYINT DEFAULT 0,
  allow_return TINYINT DEFAULT 1,
  sort_order INT DEFAULT 0,
  UNIQUE (version_id, node_key)
);

CREATE TABLE wf_process_transition (
  id BIGINT PRIMARY KEY,
  version_id BIGINT NOT NULL,
  from_node_key VARCHAR(100) NOT NULL,
  to_node_key VARCHAR(100) NOT NULL,
  condition_type VARCHAR(32) DEFAULT 'ALWAYS',
  condition_json CLOB,
  priority INT DEFAULT 0
);

CREATE TABLE wf_ru_process_instance (
  id BIGINT PRIMARY KEY,
  definition_id BIGINT,
  version_id BIGINT,
  process_key VARCHAR(100),
  business_type VARCHAR(100),
  business_id VARCHAR(100),
  title VARCHAR(200),
  initiator_id BIGINT,
  current_node_key VARCHAR(100),
  status VARCHAR(32),
  variables_json CLOB,
  definition_snapshot_json CLOB,
  started_at TIMESTAMP,
  ended_at TIMESTAMP,
  created_by BIGINT,
  created_at TIMESTAMP,
  updated_by BIGINT,
  updated_at TIMESTAMP,
  version INT DEFAULT 0
);

CREATE TABLE wf_hi_process_instance (
  id BIGINT PRIMARY KEY,
  definition_id BIGINT,
  version_id BIGINT,
  process_key VARCHAR(100),
  business_type VARCHAR(100),
  business_id VARCHAR(100),
  title VARCHAR(200),
  initiator_id BIGINT,
  current_node_key VARCHAR(100),
  status VARCHAR(32),
  variables_json CLOB,
  definition_snapshot_json CLOB,
  started_at TIMESTAMP,
  ended_at TIMESTAMP,
  created_by BIGINT,
  created_at TIMESTAMP,
  updated_by BIGINT,
  updated_at TIMESTAMP,
  version INT DEFAULT 0
);

CREATE TABLE wf_ru_task (
  id BIGINT PRIMARY KEY,
  instance_id BIGINT,
  node_key VARCHAR(100),
  node_name VARCHAR(120),
  assignee_id BIGINT,
  assignee_dept_id BIGINT,
  assignment_rule_type VARCHAR(64),
  assignment_rule_name VARCHAR(100),
  assignment_resolve_path VARCHAR(500),
  status VARCHAR(20),
  approve_comment VARCHAR(500),
  started_at TIMESTAMP,
  finished_at TIMESTAMP,
  created_by BIGINT,
  created_at TIMESTAMP,
  updated_by BIGINT,
  updated_at TIMESTAMP,
  version INT DEFAULT 0
);

CREATE TABLE wf_hi_task (
  id BIGINT PRIMARY KEY,
  instance_id BIGINT,
  node_key VARCHAR(100),
  node_name VARCHAR(120),
  assignee_id BIGINT,
  assignee_dept_id BIGINT,
  assignment_rule_type VARCHAR(64),
  assignment_rule_name VARCHAR(100),
  assignment_resolve_path VARCHAR(500),
  status VARCHAR(20),
  approve_comment VARCHAR(500),
  started_at TIMESTAMP,
  finished_at TIMESTAMP,
  created_by BIGINT,
  created_at TIMESTAMP,
  updated_by BIGINT,
  updated_at TIMESTAMP,
  version INT DEFAULT 0
);

CREATE TABLE wf_hi_event (
  id BIGINT PRIMARY KEY,
  instance_id BIGINT,
  task_id BIGINT,
  operator_id BIGINT,
  action VARCHAR(32),
  from_node_key VARCHAR(100),
  to_node_key VARCHAR(100),
  target_user_id BIGINT,
  comment VARCHAR(1000),
  created_at TIMESTAMP
);

CREATE TABLE wf_ru_cc (
  id BIGINT PRIMARY KEY,
  instance_id BIGINT,
  node_key VARCHAR(100),
  node_name VARCHAR(120),
  receiver_id BIGINT,
  read_status TINYINT,
  read_at TIMESTAMP,
  created_at TIMESTAMP
);

CREATE TABLE wf_hi_cc (
  id BIGINT PRIMARY KEY,
  instance_id BIGINT,
  node_key VARCHAR(100),
  node_name VARCHAR(120),
  receiver_id BIGINT,
  read_status TINYINT,
  read_at TIMESTAMP,
  created_at TIMESTAMP
);

CREATE TABLE wf_task_delegation (
  id BIGINT PRIMARY KEY,
  task_id BIGINT NOT NULL,
  from_user_id BIGINT NOT NULL,
  to_user_id BIGINT NOT NULL,
  delegation_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) DEFAULT 'PENDING',
  created_at TIMESTAMP
);

CREATE TABLE biz_number_rule (
  id BIGINT PRIMARY KEY,
  rule_code VARCHAR(64) NOT NULL,
  rule_name VARCHAR(100) NOT NULL,
  prefix VARCHAR(16) NOT NULL,
  date_pattern VARCHAR(16) NOT NULL DEFAULT 'yyyyMMdd',
  number_separator VARCHAR(4) NOT NULL DEFAULT '-',
  sequence_width INT NOT NULL DEFAULT 6,
  sequence_step INT NOT NULL DEFAULT 1,
  initial_value BIGINT NOT NULL DEFAULT 0,
  enable BOOLEAN NOT NULL DEFAULT TRUE,
  create_by BIGINT,
  create_dept_id BIGINT,
  create_time TIMESTAMP,
  update_by BIGINT,
  update_time TIMESTAMP,
  deleted INT DEFAULT 0,
  version INT DEFAULT 0,
  remark VARCHAR(500)
);

CREATE UNIQUE INDEX uk_biz_number_rule_code_deleted ON biz_number_rule(rule_code, deleted);
CREATE INDEX idx_biz_number_rule_enable_deleted ON biz_number_rule(enable, deleted);

CREATE TABLE biz_number_sequence (
  sequence_key VARCHAR(128) PRIMARY KEY,
  rule_code VARCHAR(64) NOT NULL,
  segment VARCHAR(32) NOT NULL,
  current_value BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP
);

CREATE INDEX idx_biz_number_sequence_rule_segment ON biz_number_sequence(rule_code, segment);

CREATE TABLE batch_task (
  id BIGINT PRIMARY KEY,
  task_type VARCHAR(64) NOT NULL,
  task_name VARCHAR(120) NOT NULL,
  business_key VARCHAR(128),
  trigger_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
  trigger_ref_id VARCHAR(128),
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  total_count INT NOT NULL DEFAULT 0,
  success_count INT NOT NULL DEFAULT 0,
  failed_count INT NOT NULL DEFAULT 0,
  skipped_count INT NOT NULL DEFAULT 0,
  progress_percent INT NOT NULL DEFAULT 0,
  cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
  started_at TIMESTAMP,
  finished_at TIMESTAMP,
  trace_id VARCHAR(128),
  error_message VARCHAR(1000),
  result_message VARCHAR(1000),
  create_by BIGINT,
  create_dept_id BIGINT,
  create_time TIMESTAMP,
  update_by BIGINT,
  update_time TIMESTAMP,
  deleted INT DEFAULT 0,
  version INT DEFAULT 0,
  remark VARCHAR(500)
);

CREATE UNIQUE INDEX uk_batch_task_type_business_deleted ON batch_task(task_type, business_key, deleted);
CREATE INDEX idx_batch_task_status_time ON batch_task(status, create_time);
CREATE INDEX idx_batch_task_trigger ON batch_task(trigger_type, trigger_ref_id);

CREATE TABLE batch_task_item (
  id BIGINT PRIMARY KEY,
  task_id BIGINT NOT NULL,
  item_key VARCHAR(128) NOT NULL,
  item_name VARCHAR(200),
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  retry_count INT NOT NULL DEFAULT 0,
  payload CLOB,
  error_message VARCHAR(1000),
  result_message VARCHAR(1000),
  started_at TIMESTAMP,
  finished_at TIMESTAMP,
  create_by BIGINT,
  create_dept_id BIGINT,
  create_time TIMESTAMP,
  update_by BIGINT,
  update_time TIMESTAMP,
  deleted INT DEFAULT 0,
  version INT DEFAULT 0,
  remark VARCHAR(500)
);

CREATE UNIQUE INDEX uk_batch_task_item_key_deleted ON batch_task_item(task_id, item_key, deleted);
CREATE INDEX idx_batch_task_item_status ON batch_task_item(task_id, status);

CREATE TABLE biz_leave_request (
  id BIGINT PRIMARY KEY,
  request_no VARCHAR(64) NOT NULL,
  applicant_id BIGINT NOT NULL,
  applicant_dept_id BIGINT,
  leave_type VARCHAR(32) NOT NULL,
  start_time TIMESTAMP NOT NULL,
  end_time TIMESTAMP NOT NULL,
  days DECIMAL(6,2) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  status VARCHAR(32) DEFAULT 'DRAFT',
  workflow_instance_id BIGINT,
  created_by BIGINT,
  created_at TIMESTAMP,
  updated_by BIGINT,
  updated_at TIMESTAMP
);

CREATE TABLE biz_purchase_request (
  id BIGINT PRIMARY KEY,
  request_no VARCHAR(64) NOT NULL,
  applicant_id BIGINT NOT NULL,
  applicant_dept_id BIGINT,
  item_name VARCHAR(120) NOT NULL,
  category VARCHAR(32) NOT NULL,
  quantity INT NOT NULL,
  estimated_amount DECIMAL(12,2) NOT NULL,
  required_date DATE NOT NULL,
  reason VARCHAR(500) NOT NULL,
  status VARCHAR(32) DEFAULT 'DRAFT',
  workflow_instance_id BIGINT,
  created_by BIGINT,
  created_at TIMESTAMP,
  updated_by BIGINT,
  updated_at TIMESTAMP
);

CREATE TABLE biz_repair_request (
  id BIGINT PRIMARY KEY,
  request_no VARCHAR(64) NOT NULL,
  applicant_id BIGINT NOT NULL,
  applicant_dept_id BIGINT,
  repair_type VARCHAR(32) NOT NULL,
  asset_name VARCHAR(120) NOT NULL,
  urgency VARCHAR(32) NOT NULL,
  fault_time TIMESTAMP NOT NULL,
  location VARCHAR(120) NOT NULL,
  description VARCHAR(500) NOT NULL,
  attachments_json CLOB,
  status VARCHAR(32) DEFAULT 'DRAFT',
  workflow_instance_id BIGINT,
  created_by BIGINT,
  created_at TIMESTAMP,
  updated_by BIGINT,
  updated_at TIMESTAMP
);

INSERT INTO sys_dept (id, dept_name, full_name, pid, tree_path, leader_user_id, status, sort, create_by, create_dept_id, create_time, update_by, update_time, deleted, version)
VALUES
  (202604280103000001, '易企科技有限公司', '易企科技有限公司', 0, '/202604280103000001/', 202604280101000001, 1, 10, 202604280101000001, 202604280103000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0),
  (202604280103000101, '总经办', '易企科技有限公司 / 总经办', 202604280103000001, '/202604280103000001/202604280103000101/', 202604280101000001, 1, 20, 202604280101000001, 202604280103000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0),
  (202604280103000102, '产品研发中心', '易企科技有限公司 / 产品研发中心', 202604280103000001, '/202604280103000001/202604280103000102/', 202604280101000018, 1, 30, 202604280101000001, 202604280103000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0),
  (202604280103000103, '客户成功中心', '易企科技有限公司 / 客户成功中心', 202604280103000001, '/202604280103000001/202604280103000103/', 202604280101000025, 1, 40, 202604280101000001, 202604280103000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0),
  (202604280103000104, '运营交付中心', '易企科技有限公司 / 运营交付中心', 202604280103000001, '/202604280103000001/202604280103000104/', 202604280101000024, 1, 50, 202604280101000001, 202604280103000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0),
  (202604280103000105, '财务行政部', '易企科技有限公司 / 财务行政部', 202604280103000001, '/202604280103000001/202604280103000105/', 202604280101000017, 1, 60, 202604280101000001, 202604280103000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0);

INSERT INTO sys_role (id, role_name, role_code, details, enable, role_level, data_scope, create_by, create_dept_id, create_time, update_by, update_time, deleted, version)
VALUES
  (202604280102000005, '超级管理员', 'admin', '测试管理员', 1, 1, 'ALL', 202604280101000001, 202604280103000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0),
  (202604280102000006, '运维人员', 'ops', '负责监控、缓存、实时日志和任务调度。', 1, 30, 'DEPT', 202604280101000001, 202604280103000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0),
  (202604280102000010, '审计人员', 'auditor', '查看审计和财务复核类流程。', 1, 40, 'ALL', 202604280101000001, 202604280103000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0),
  (202604280102000012, '部门负责人', 'dept_manager', '维护部门用户并处理部门流程。', 1, 20, 'DEPT_AND_CHILDREN', 202604280101000001, 202604280103000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0),
  (202604280102000013, '普通员工', 'staff', '基础菜单入口和个人流程处理能力。', 1, 50, 'SELF', 202604280101000001, 202604280103000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0);

INSERT INTO sys_user (id, user_name, password_hash, nick_name, dept_id, manager_user_id, phone, enable, email, create_by, create_dept_id, create_time, update_by, update_time, deleted, version, permission_version, employee_no, real_name, position_name)
VALUES
  (202604280101000001, 'admin', '$2a$12$nIMLkCUCotpAOa9SXEZfj.WMf4Vl18DXifv6.js7CsKq.1Gdhx7mu', '超级管理员', 202604280103000101, NULL, '13800000000', 1, 'admin@easynextadmin.local', 202604280101000001, 202604280103000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'EA000001', '系统管理员', '平台管理员'),
  (202604280101000016, 'product_li', '$2a$12$EiB3avTyJ5c3CV4SnAVrE.Q08PllqdC9C00m4G1YPLQGw3caJTUU6', '李产品', 202604280103000102, 202604280101000018, '13800000003', 1, 'product.li@easynextadmin.local', 202604280101000001, 202604280103000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'EA000201', '李产品', '产品经理'),
  (202604280101000017, 'auditor', '$2a$12$EiB3avTyJ5c3CV4SnAVrE.Q08PllqdC9C00m4G1YPLQGw3caJTUU6', '王审计', 202604280103000105, 202604280101000001, '13800000006', 1, 'auditor@easynextadmin.local', 202604280101000001, 202604280103000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'EA000401', '王审计', '内控审计'),
  (202604280101000018, 'tech_zhang', '$2a$12$EiB3avTyJ5c3CV4SnAVrE.Q08PllqdC9C00m4G1YPLQGw3caJTUU6', '张技术', 202604280103000102, 202604280101000001, '13800000004', 1, 'tech.zhang@easynextadmin.local', 202604280101000001, 202604280103000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'EA000202', '张技术', '研发负责人'),
  (202604280101000024, 'ops', '$2a$12$EiB3avTyJ5c3CV4SnAVrE.Q08PllqdC9C00m4G1YPLQGw3caJTUU6', '周运维', 202604280103000104, 202604280101000001, '13800000005', 1, 'ops@easynextadmin.local', 202604280101000001, 202604280103000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'EA000301', '周运维', '应用运维工程师'),
  (202604280101000025, 'manager', '$2a$12$EiB3avTyJ5c3CV4SnAVrE.Q08PllqdC9C00m4G1YPLQGw3caJTUU6', '陈经理', 202604280103000103, 202604280101000001, '13800000001', 1, 'manager@easynextadmin.local', 202604280101000001, 202604280103000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'EA000101', '陈经理', '客户成功经理'),
  (202604280101000026, 'staff', '$2a$12$EiB3avTyJ5c3CV4SnAVrE.Q08PllqdC9C00m4G1YPLQGw3caJTUU6', '林员工', 202604280103000103, 202604280101000025, '13800000002', 1, 'staff@easynextadmin.local', 202604280101000001, 202604280103000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'EA000102', '林员工', '客户运营专员');

INSERT INTO audit_login_log (id, user_id, user_name, login_result, fail_reason, ip, user_agent, client_type, trace_id, login_time)
VALUES (202605080101000001, 202604280101000001, 'admin', 'SUCCESS', NULL, '127.0.0.1', 'Seed', 'web', 'seed-admin-login', CURRENT_TIMESTAMP);

INSERT INTO schedule_job (job_id, job_code, job_name, job_class_name, cron_expression, create_time, enable, job_state, create_by, create_dept_id, update_by, update_time, deleted, version, remark)
VALUES
  (202604280107000001, 'infra_local_message_retry', '本地消息失败重试', 'com.laker.admin.infrastructure.message.local.LocalMessageRetryJob', '0 0/1 * * * ?', CURRENT_TIMESTAMP, 1, 1, 202604280101000001, 202604280103000001, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, '平台内置任务，用于重试失败的本地消息。');

INSERT INTO biz_number_rule
(id, rule_code, rule_name, prefix, date_pattern, number_separator, sequence_width, sequence_step, initial_value, enable, create_by, create_dept_id, create_time, update_by, update_time, deleted, version, remark)
VALUES
  (202606250112000001, 'LEAVE_REQUEST', '请假申请单号', 'LV', 'yyyyMMdd', '-', 6, 1, 0, TRUE, 202604280101000001, 202604280103000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, '流程请假申请使用的业务编号规则。'),
  (202606250112000002, 'PURCHASE_REQUEST', '采购申请单号', 'PR', 'yyyyMMdd', '-', 6, 1, 0, TRUE, 202604280101000001, 202604280103000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, '流程采购申请使用的业务编号规则。'),
  (202606250112000003, 'REPAIR_REQUEST', '报修申请单号', 'RP', 'yyyyMMdd', '-', 6, 1, 0, TRUE, 202604280101000001, 202604280103000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, '流程报修申请使用的业务编号规则。');

INSERT INTO sys_user_role (id, user_id, role_id, create_by, create_dept_id, create_time, update_by, update_time, deleted, version)
VALUES
  (202604280105001001, 202604280101000001, 202604280102000005, 202604280101000001, 202604280103000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0),
  (202604280105001002, 202604280101000025, 202604280102000012, 202604280101000001, 202604280103000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0),
  (202604280105001003, 202604280101000026, 202604280102000013, 202604280101000001, 202604280103000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0),
  (202604280105001004, 202604280101000016, 202604280102000013, 202604280101000001, 202604280103000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0),
  (202604280105001005, 202604280101000017, 202604280102000010, 202604280101000001, 202604280103000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0),
  (202604280105001006, 202604280101000018, 202604280102000012, 202604280101000001, 202604280103000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0),
  (202604280105001007, 202604280101000024, 202604280102000006, 202604280101000001, 202604280103000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0);

INSERT INTO sys_menu (id, pid, title, icon, href, sort, enable, remark, create_time, update_by, update_time, deleted, version, type, permission_code, component_path, visible, create_by, create_dept_id)
VALUES
  (202604280104000001, 0, '工作台', 'DataBoard', '/dashboard', 10, 1, '进入企业工作台并查看系统概览。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'dashboard:view', '@/views/dashboard/WorkspaceView.vue', 1, 202604280101000001, 202604280103000001),
  (202604280104000100, 0, '系统管理', 'Setting', '', 20, 1, '系统基础能力分组。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 0, NULL, NULL, 1, 202604280101000001, 202604280103000001),
  (202604280104000101, 202604280104000100, '用户管理', 'User', '/system/users', 10, 1, '维护企业账号、部门、状态和角色绑定。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'sys:user:list', '@/views/system/UserView.vue', 1, 202604280101000001, 202604280103000001),
  (202604280104000102, 202604280104000100, '角色权限', 'Key', '/system/roles', 20, 1, '维护角色资料、菜单权限、按钮权限和数据范围。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'sys:role:list', '@/views/system/RoleView.vue', 1, 202604280101000001, 202604280103000001),
  (202604280104000103, 202604280104000100, '菜单配置', 'Menu', '/system/menus', 30, 1, '维护目录、页面和按钮权限。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'sys:menu:list', '@/views/system/MenuView.vue', 1, 202604280101000001, 202604280103000001),
  (202604280104000104, 202604280104000100, '组织架构', 'OfficeBuilding', '/system/departments', 40, 1, '维护企业部门树和负责人。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'sys:dept:list', '@/views/system/DepartmentView.vue', 1, 202604280101000001, 202604280103000001),
  (202604280104000111, 202604280104000101, '新增用户', '', '', 11, 1, '允许创建账号并绑定部门、角色。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'sys:user:add', NULL, 0, 202604280101000001, 202604280103000001),
  (202604280104000112, 202604280104000101, '编辑用户', '', '', 12, 1, '允许修改账号资料、启停状态和角色绑定。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'sys:user:edit', NULL, 0, 202604280101000001, 202604280103000001),
  (202604280104000113, 202604280104000101, '删除用户', '', '', 13, 1, '允许删除非超级管理员账号。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'sys:user:delete', NULL, 0, 202604280101000001, 202604280103000001),
  (202604280104000114, 202604280104000101, '导入用户', '', '', 14, 1, '允许下载用户导入模板并批量导入账号。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'sys:user:import', NULL, 0, 202604280101000001, 202604280103000001),
  (202604280104000115, 202604280104000101, '导出用户', '', '', 15, 1, '允许按当前筛选条件导出用户 CSV。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'sys:user:export', NULL, 0, 202604280101000001, 202604280103000001),
  (202604280104000116, 202604280104000101, '重置密码', '', '', 16, 1, '允许将非高权限用户密码重置为系统默认密码。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'sys:user:reset-password', NULL, 0, 202604280101000001, 202604280103000001),
  (202604280104000121, 202604280104000102, '编辑角色与授权配置', '', '', 11, 1, '允许维护角色资料并配置菜单权限、按钮权限和数据范围策略。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'sys:role:edit', NULL, 0, 202604280101000001, 202604280103000001),
  (202604280104000131, 202604280104000103, '编辑权限资源', '', '', 11, 1, '允许维护目录、页面和按钮权限。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'sys:menu:edit', NULL, 0, 202604280101000001, 202604280103000001),
  (202604280104000141, 202604280104000104, '编辑部门', '', '', 11, 1, '允许维护部门和组织树。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'sys:dept:edit', NULL, 0, 202604280101000001, 202604280103000001),
  (202604280104000151, 202604280104000100, '文件中心', 'Document', '/system/files', 50, 1, '进入文件中心，查看文件元数据并通过鉴权接口下载。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'sys:file:list', '@/views/system/FileCenterView.vue', 1, 202604280101000001, 202604280103000001),
  (202604280104000152, 202604280104000151, '上传文件', '', '', 11, 1, '允许上传白名单范围内的企业附件。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'sys:file:upload', NULL, 0, 202604280101000001, 202604280103000001),
  (202604280104000153, 202604280104000151, '删除文件', '', '', 12, 1, '允许删除文件元数据和存储对象。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'sys:file:delete', NULL, 0, 202604280101000001, 202604280103000001),
  (202606250104000001, 202604280104000100, '编号规则', 'Tickets', '/system/business-numbers', 60, 1, '维护业务单号、工单号、申请单号等可读编号规则。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'business:number:list', '@/views/system/BusinessNumberRuleView.vue', 1, 202604280101000001, 202604280103000001),
  (202606250104000002, 202606250104000001, '维护编号规则', '', '', 11, 1, '允许新增、编辑、停用和删除业务编号规则。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'business:number:edit', NULL, 0, 202604280101000001, 202604280103000001),
  (202606250104000003, 202606250104000001, '人工生成编号', '', '', 12, 1, '允许运维或管理员在编号规则页人工生成测试编号。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'business:number:generate', NULL, 0, 202604280101000001, 202604280103000001),
  (202605260104000001, 0, '报表中心', 'DataAnalysis', '/reports/enterprise', 28, 1, '查看组织人员台账和采购流程复核纸质报表。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'report:view', '@/views/report/EnterpriseReportView.vue', 1, 202604280101000001, 202604280103000001),
  (202604280104000200, 0, '运行监控', 'Monitor', '', 30, 1, '服务监控、在线用户、缓存监控、缓存列表、实时日志和任务调度分组。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 0, NULL, NULL, 1, 202604280101000001, 202604280103000001),
  (202604280104000201, 202604280104000200, '服务监控', 'Monitor', '/monitor/server', 10, 1, '查看服务健康、JVM、CPU、内存、线程、磁盘和 GC 水位。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'monitor:server:view', '@/views/monitor/MonitorView.vue', 1, 202604280101000001, 202604280103000001),
  (202604280104000204, 202604280104000200, '在线用户', 'UserFilled', '/monitor/online', 20, 1, '查看在线账号、客户端、来源 IP 和最近活跃时间。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'monitor:online:view', '@/views/monitor/OnlineUserView.vue', 1, 202604280101000001, 202604280103000001),
  (202604280104000211, 202604280104000204, '下线会话', '', '', 11, 1, '允许运维人员终止异常在线会话。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'auth:session:revoke', NULL, 0, 202604280101000001, 202604280103000001),
  (202604280104000205, 202604280104000200, '缓存监控', 'Coin', '/monitor/cache', 30, 1, '查看缓存名称、容量、命中率和淘汰次数，并支持按名称清理缓存。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'monitor:cache:view', '@/views/monitor/CacheMonitorView.vue', 1, 202604280101000001, 202604280103000001),
  (202604280104000213, 202604280104000200, '缓存列表', 'Tickets', '/monitor/cache-list', 31, 1, '查看缓存 key 和 value 预览，支持按 key 精确清理缓存项。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'monitor:cache:view', '@/views/monitor/CacheListView.vue', 1, 202604280101000001, 202604280103000001),
  (202604280104000212, 202604280104000205, '清理缓存', '', 'DELETE /api/monitor/cache/{cacheName}, DELETE /api/monitor/cache/{cacheName}/entries', 11, 1, '允许按缓存名称清理缓存，或按 key 精确清理缓存项。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'monitor:cache:clear', NULL, 0, 202604280101000001, 202604280103000001),
  (202604280104000203, 202604280104000200, '实时日志', 'Document', '/monitor/weblog', 40, 1, '查看 logback 当前文件日志，支持关键词、级别和行数过滤。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'monitor:weblog:view', '@/views/monitor/WebLogView.vue', 1, 202604280101000001, 202604280103000001),
  (202605130104000001, 202604280104000203, '调整日志级别', '', '', 11, 1, '允许在白名单范围内临时调整实时日志级别。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'monitor:weblog:level', NULL, 0, 202604280101000001, 202604280103000001),
  (202604280104000250, 0, '审计中心', 'Operation', '', 35, 1, '登录、操作、数据变更、异常和接口访问审计分组。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 0, NULL, NULL, 1, 202604280101000001, 202604280103000001),
  (202604280104000202, 202604280104000250, '行为审计', 'Operation', '/audit/behavior', 10, 1, '查看登录、操作、数据变更、异常和接口访问审计。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'audit:behavior:view', '@/views/audit/BehaviorAuditView.vue', 1, 202604280101000001, 202604280103000001),
  (202604280104000500, 0, '消息中心', 'Bell', '/messages', 38, 1, '查看流程待办、抄送、审计提醒和任务消息。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'message:view', '@/views/message/MessageCenterView.vue', 1, 202604280101000001, 202604280103000001),
  (202604280104000501, 202604280104000500, '标记消息已读', '', '', 11, 1, '允许将自己的站内消息标记为已读。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'message:read', NULL, 0, 202604280101000001, 202604280103000001),
  (202604280104000510, 0, '个人中心', 'UserFilled', '/profile/security', 39, 1, '维护个人资料、头像、密码、登录历史和会话。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'profile:view', '@/views/profile/ProfileSecurityView.vue', 0, 202604280101000001, 202604280103000001),
  (202604280104000511, 202604280104000510, '编辑个人资料', '', '', 11, 1, '允许维护自己的基础资料和头像。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'profile:edit', NULL, 0, 202604280101000001, 202604280103000001),
  (202604280104000512, 202604280104000510, '修改个人密码', '', '', 12, 1, '允许修改自己的登录密码。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'profile:password:change', NULL, 0, 202604280101000001, 202604280103000001),
  (202604280104000513, 202604280104000510, '管理个人会话', '', '', 13, 1, '允许查看并撤销自己的登录会话。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'profile:session:manage', NULL, 0, 202604280101000001, 202604280103000001),
  (202605110104000001, 0, '接口文档', 'DocumentChecked', '/developer/api-docs', 45, 1, '查看 OpenAPI Swagger UI 接口文档。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'developer:api-docs:view', '@/views/developer/ApiDocsView.vue', 1, 202604280101000001, 202604280103000001),
  (202604280104000300, 202604280104000200, '定时任务', 'Timer', '/schedule/jobs', 50, 1, '查看任务定义、运行状态和最近执行结果。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'schedule:job:list', '@/views/schedule/JobView.vue', 1, 202604280101000001, 202604280103000001),
  (202604280104000301, 202604280104000300, '维护定时任务', '', '', 11, 1, '允许新增、暂停、恢复、立即执行和编辑任务。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'schedule:job:edit', NULL, 0, 202604280101000001, 202604280103000001),
  (202606250104010001, 202604280104000200, '批处理任务', 'Operation', '/batch/tasks', 55, 1, '查看批量导入、同步和报表生成等长任务的进度、失败明细和治理动作。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'batch:task:list', '@/views/batch/BatchTaskView.vue', 1, 202604280101000001, 202604280103000001),
  (202606250104010002, 202606250104010001, '治理批处理任务', '', '', 11, 1, '允许取消批处理任务和重置失败项等待业务 worker 补跑。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'batch:task:manage', NULL, 0, 202604280101000001, 202604280103000001),
  (202604280104000400, 0, '流程中心', 'Connection', '', 50, 1, '面向业务用户的申请、待办和面向管理员的流程配置分组。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 0, NULL, NULL, 1, 202604280101000001, 202604280103000001),
  (202604280104000419, 202604280104000400, '发起流程', 'Promotion', '/workflow/start', 10, 1, '统一展示请假、采购和报修等可发起流程入口。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'workflow:instance:start', '@/views/workflow/WorkflowStartView.vue', 1, 202604280101000001, 202604280103000001),
  (202604280104000412, 202604280104000400, '请假申请', 'Document', '/workflow/leave', 11, 1, '填写请假单并发起请假审批流程。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'workflow:instance:start', '@/views/workflow/LeaveRequestView.vue', 0, 202604280101000001, 202604280103000001),
  (202604280104000417, 202604280104000400, '采购申请', 'ShoppingCart', '/workflow/purchase', 12, 1, '填写采购单并按预算金额发起采购审批。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'workflow:instance:start', '@/views/workflow/PurchaseRequestView.vue', 0, 202604280101000001, 202604280103000001),
  (202604280104000418, 202604280104000400, '报修申请', 'Tools', '/workflow/repair', 13, 1, '填写报修信息并提交运维受理流程。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'workflow:instance:start', '@/views/workflow/RepairRequestView.vue', 0, 202604280101000001, 202604280103000001),
  (202604280104000413, 202604280104000400, '我的流程', 'Finished', '/workflow/tasks', 30, 1, '查看待办、已办、我发起的和抄送流程。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'workflow:view', '@/views/workflow/WorkflowTaskCenterView.vue', 1, 202604280101000001, 202604280103000001),
  (202604280104000420, 202604280104000400, '流程实例', 'Tickets', '/workflow/instances', 40, 1, '具备流程实例管理权限的管理员监控全部流程实例，并执行催办、转办或终止。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'workflow:instance:manage', '@/views/workflow/WorkflowInstanceMonitorView.vue', 1, 202604280101000001, 202604280103000001),
  (202604280104000414, 202604280104000400, '流程配置', 'Connection', '/workflow/console', 90, 1, '管理员维护流程定义、节点和发布状态。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 1, 'workflow:definition:edit', '@/views/workflow/WorkflowView.vue', 1, 202604280101000001, 202604280103000001),
  (202604280104000401, 202604280104000414, '维护流程定义', '', '', 11, 1, '允许发布、停用、删除和调整流程定义。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'workflow:definition:edit', NULL, 0, 202604280101000001, 202604280103000001),
  (202604280104000403, 202604280104000419, '提交流程申请', '', '', 10, 1, '允许基于已启用流程定义发起流程实例。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'workflow:instance:start', NULL, 0, 202604280101000001, 202604280103000001),
  (202604280104000404, 202604280104000413, '撤回流程', '', '', 13, 1, '允许撤回自己发起的运行中流程。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'workflow:instance:revoke', NULL, 0, 202604280101000001, 202604280103000001),
  (202604280104000405, 202604280104000414, '终止流程', '', '', 14, 1, '允许终止运行中的流程实例。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'workflow:instance:terminate', NULL, 0, 202604280101000001, 202604280103000001),
  (202604280104000406, 202604280104000413, '同意任务', '', '', 15, 1, '允许处理待办任务并流转到下一节点。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'workflow:task:approve', NULL, 0, 202604280101000001, 202604280103000001),
  (202604280104000407, 202604280104000413, '驳回任务', '', '', 16, 1, '允许驳回待办任务并结束流程实例。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'workflow:task:reject', NULL, 0, 202604280101000001, 202604280103000001),
  (202604280104000408, 202604280104000413, '转办任务', '', '', 17, 1, '允许将当前待办转交给其他处理人。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'workflow:task:transfer', NULL, 0, 202604280101000001, 202604280103000001),
  (202604280104000409, 202604280104000413, '委派任务', '', '', 18, 1, '允许临时委派当前待办给其他处理人。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'workflow:task:delegate', NULL, 0, 202604280101000001, 202604280103000001),
  (202604280104000410, 202604280104000413, '退回任务', '', '', 19, 1, '允许将当前任务退回到提交节点或指定节点。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'workflow:task:return', NULL, 0, 202604280101000001, 202604280103000001),
  (202604280104000415, 202604280104000413, '加签任务', '', '', 20, 1, '允许给当前审批节点追加处理人，所有加签待办完成后才继续流转。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'workflow:task:add-sign', NULL, 0, 202604280101000001, 202604280103000001),
  (202604280104000416, 202604280104000413, '减签任务', '', '', 21, 1, '允许移除当前节点尚未处理的加签待办。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'workflow:task:remove-sign', NULL, 0, 202604280101000001, 202604280103000001),
  (202604280104000411, 202604280104000413, '催办任务', '', '', 22, 1, '允许对待处理任务发起催办留痕。', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 0, 0, 2, 'workflow:task:remind', NULL, 0, 202604280101000001, 202604280103000001);

INSERT INTO sys_role_permission (id, role_id, permission_resource_id, create_by, create_dept_id, create_time, update_by, update_time, deleted, version, remark)
SELECT 202604290106010000 + ROW_NUMBER() OVER (ORDER BY permission_resource.id),
       role_table.id,
       permission_resource.id,
       202604280101000001,
       202604280103000001,
       CURRENT_TIMESTAMP,
       202604280101000001,
       CURRENT_TIMESTAMP,
       0,
       0,
       '超级管理员内置授权'
FROM sys_role role_table
JOIN sys_menu permission_resource ON permission_resource.deleted = 0
                         AND permission_resource.enable = 1
WHERE role_table.role_code = 'admin'
  AND role_table.deleted = 0;

INSERT INTO sys_role_permission (id, role_id, permission_resource_id, create_by, create_dept_id, create_time, update_by, update_time, deleted, version, remark)
SELECT 202604290106020000 + ROW_NUMBER() OVER (ORDER BY permission_resource.id),
       role_table.id,
       permission_resource.id,
       202604280101000001,
       202604280103000001,
       CURRENT_TIMESTAMP,
       202604280101000001,
       CURRENT_TIMESTAMP,
       0,
       0,
       '部门负责人默认授权'
FROM sys_role role_table
JOIN sys_menu permission_resource ON permission_resource.deleted = 0
                         AND permission_resource.enable = 1
                         AND (
                             permission_resource.permission_code IN (
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
                                 FROM sys_menu child
                                 LEFT JOIN sys_menu parent ON parent.id = child.pid
                                                          AND parent.deleted = 0
                                                          AND parent.enable = 1
                                 WHERE child.deleted = 0
                                   AND child.enable = 1
                                   AND child.permission_code IN (
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
                                   AND (child.pid = permission_resource.id OR parent.pid = permission_resource.id)
                             )
                         )
WHERE role_table.role_code = 'dept_manager'
  AND role_table.deleted = 0;

INSERT INTO sys_role_permission (id, role_id, permission_resource_id, create_by, create_dept_id, create_time, update_by, update_time, deleted, version, remark)
SELECT 202604290106030000 + ROW_NUMBER() OVER (ORDER BY permission_resource.id),
       role_table.id,
       permission_resource.id,
       202604280101000001,
       202604280103000001,
       CURRENT_TIMESTAMP,
       202604280101000001,
       CURRENT_TIMESTAMP,
       0,
       0,
       '普通员工默认授权'
FROM sys_role role_table
JOIN sys_menu permission_resource ON permission_resource.deleted = 0
                         AND permission_resource.enable = 1
                         AND (
                             permission_resource.permission_code IN (
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
                                 FROM sys_menu child
                                 LEFT JOIN sys_menu parent ON parent.id = child.pid
                                                          AND parent.deleted = 0
                                                          AND parent.enable = 1
                                 WHERE child.deleted = 0
                                   AND child.enable = 1
                                   AND child.permission_code IN (
                                       'dashboard:view',
                                       'workflow:instance:start',
                                       'workflow:view',
                                       'workflow:instance:revoke',
                                       'workflow:task:approve',
                                       'workflow:task:remind',
                                       'message:view',
                                       'message:read'
                                   )
                                   AND (child.pid = permission_resource.id OR parent.pid = permission_resource.id)
                             )
                         )
WHERE role_table.role_code = 'staff'
  AND role_table.deleted = 0;

INSERT INTO sys_role_permission (id, role_id, permission_resource_id, create_by, create_dept_id, create_time, update_by, update_time, deleted, version, remark)
SELECT 202604290106040000 + ROW_NUMBER() OVER (ORDER BY permission_resource.id),
       role_table.id,
       permission_resource.id,
       202604280101000001,
       202604280103000001,
       CURRENT_TIMESTAMP,
       202604280101000001,
       CURRENT_TIMESTAMP,
       0,
       0,
       '运维人员默认授权'
FROM sys_role role_table
JOIN sys_menu permission_resource ON permission_resource.deleted = 0
                         AND permission_resource.enable = 1
                         AND (
                             permission_resource.permission_code IN (
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
                                 FROM sys_menu child
                                 LEFT JOIN sys_menu parent ON parent.id = child.pid
                                                          AND parent.deleted = 0
                                                          AND parent.enable = 1
                                 WHERE child.deleted = 0
                                   AND child.enable = 1
                                   AND child.permission_code IN (
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
                                   AND (child.pid = permission_resource.id OR parent.pid = permission_resource.id)
                             )
                         )
WHERE role_table.role_code = 'ops'
  AND role_table.deleted = 0;

INSERT INTO sys_role_permission (id, role_id, permission_resource_id, create_by, create_dept_id, create_time, update_by, update_time, deleted, version, remark)
SELECT 202604290106050000 + ROW_NUMBER() OVER (ORDER BY permission_resource.id),
       role_table.id,
       permission_resource.id,
       202604280101000001,
       202604280103000001,
       CURRENT_TIMESTAMP,
       202604280101000001,
       CURRENT_TIMESTAMP,
       0,
       0,
       '审计人员默认授权'
FROM sys_role role_table
JOIN sys_menu permission_resource ON permission_resource.deleted = 0
                         AND permission_resource.enable = 1
                         AND (
                             permission_resource.permission_code IN (
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
                                 FROM sys_menu child
                                 LEFT JOIN sys_menu parent ON parent.id = child.pid
                                                          AND parent.deleted = 0
                                                          AND parent.enable = 1
                                 WHERE child.deleted = 0
                                   AND child.enable = 1
                                   AND child.permission_code IN (
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
                                   AND (child.pid = permission_resource.id OR parent.pid = permission_resource.id)
                             )
                         )
WHERE role_table.role_code = 'auditor'
  AND role_table.deleted = 0;

INSERT INTO wf_process_definition (id, process_key, process_name, current_version, status, remark, created_by, created_at, updated_by, updated_at)
VALUES (202604280108000001, 'leave_approval', '请假审批', 1, 'ENABLED', '员工提交请假后，由直属负责人审批。', 202604280101000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP);

INSERT INTO wf_process_definition_version (id, definition_id, version, graph_json, status, published_by, published_at, created_by, created_at, updated_by, updated_at)
VALUES (202604280108010001, 202604280108000001, 1, '{"nodes":[{"id":"start","type":"circle","x":100,"y":180,"text":"开始"},{"id":"approve","type":"rect","x":300,"y":180,"text":"负责人审批"},{"id":"end","type":"circle","x":500,"y":180,"text":"结束"}],"edges":[{"sourceNodeId":"start","targetNodeId":"approve","type":"polyline"},{"sourceNodeId":"approve","targetNodeId":"end","type":"polyline"}]}', 'PUBLISHED', 202604280101000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP);

INSERT INTO wf_process_definition (id, process_key, process_name, current_version, status, remark, created_by, created_at, updated_by, updated_at)
VALUES
  (202604280108000003, 'purchase_approval', '采购审批', 1, 'ENABLED', '按采购金额流转到部门负责人或总经办审批。', 202604280101000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP),
  (202604280108000004, 'repair_approval', '报修审批', 1, 'ENABLED', '员工提交报修后，由运维受理并抄送审计备案。', 202604280101000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP);

INSERT INTO wf_process_definition_version (id, definition_id, version, graph_json, status, published_by, published_at, created_by, created_at, updated_by, updated_at)
VALUES
  (202604280108010003, 202604280108000003, 1, '{"nodes":[{"id":"start","type":"circle","text":"开始","properties":{"nodeType":"START"}},{"id":"submit","type":"rect","text":"提交采购","properties":{"nodeType":"SUBMIT"}},{"id":"condition","type":"diamond","text":"金额判断","properties":{"nodeType":"CONDITION"}},{"id":"manager","type":"rect","text":"负责人审批","properties":{"nodeType":"APPROVAL","approveType":"ANY_ONE","approverType":"DEPT_LEADER"}},{"id":"office","type":"rect","text":"总经办审批","properties":{"nodeType":"APPROVAL","approveType":"ANY_ONE","approverType":"USER","assigneeIds":["202604280101000001"]}},{"id":"end","type":"circle","text":"结束","properties":{"nodeType":"END"}}],"edges":[{"sourceNodeId":"start","targetNodeId":"submit","type":"polyline"},{"sourceNodeId":"submit","targetNodeId":"condition","type":"polyline"},{"sourceNodeId":"condition","targetNodeId":"manager","type":"polyline","properties":{"conditionType":"EXPRESSION","conditionExpression":"amount <= 5000"}},{"sourceNodeId":"condition","targetNodeId":"office","type":"polyline","text":"默认/大额","properties":{"conditionType":"ALWAYS"}},{"sourceNodeId":"manager","targetNodeId":"end","type":"polyline"},{"sourceNodeId":"office","targetNodeId":"end","type":"polyline"}]}', 'PUBLISHED', 202604280101000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP),
  (202604280108010004, 202604280108000004, 1, '{"nodes":[{"id":"start","type":"circle","text":"开始","properties":{"nodeType":"START"}},{"id":"submit","type":"rect","text":"提交报修","properties":{"nodeType":"SUBMIT"}},{"id":"ops","type":"rect","text":"运维受理","properties":{"nodeType":"APPROVAL","approveType":"ANY_ONE","approverType":"USER","assigneeIds":["202604280101000024"]}},{"id":"cc_audit","type":"rect","text":"审计备案","properties":{"nodeType":"CC","ccUserIds": ["202604280101000017"]}},{"id":"end","type":"circle","text":"结束","properties":{"nodeType":"END"}}],"edges":[{"sourceNodeId":"start","targetNodeId":"submit","type":"polyline"},{"sourceNodeId":"submit","targetNodeId":"ops","type":"polyline"},{"sourceNodeId":"ops","targetNodeId":"cc_audit","type":"polyline"},{"sourceNodeId":"cc_audit","targetNodeId":"end","type":"polyline"}]}', 'PUBLISHED', 202604280101000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP);

INSERT INTO wf_ru_process_instance (id, definition_id, version_id, process_key, business_type, business_id, title, initiator_id, current_node_key, status, variables_json, definition_snapshot_json, started_at, ended_at, created_by, created_at, updated_by, updated_at)
VALUES (202604280108030001, 202604280108000001, 202604280108010001, 'leave_approval', 'leave', 'LEAVE-TEST-001', '测试请假审批', 202604280101000001, 'approve', 'RUNNING', '{"leaveType":"ANNUAL","startTime":"2026-05-10T09:00:00","endTime":"2026-05-11T18:00:00","days":2.0,"duration":2.0,"reason":"测试请假申请。","applicantDeptId":202604280103000001}', '{"nodes":[{"id":"start","type":"circle","x":100,"y":180,"text":"开始"},{"id":"approve","type":"rect","x":300,"y":180,"text":"负责人审批"},{"id":"end","type":"circle","x":500,"y":180,"text":"结束"}],"edges":[{"sourceNodeId":"start","targetNodeId":"approve","type":"polyline"},{"sourceNodeId":"approve","targetNodeId":"end","type":"polyline"}]}', CURRENT_TIMESTAMP, NULL, 202604280101000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP);

INSERT INTO wf_ru_task (id, instance_id, node_key, node_name, assignee_id, assignee_dept_id, status, started_at, created_by, created_at, updated_by, updated_at)
VALUES (202604280108020001, 202604280108030001, 'approve', '负责人审批', 202604280101000001, 202604280103000001, 'PENDING', CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP, 202604280101000001, CURRENT_TIMESTAMP);

INSERT INTO wf_hi_event (id, instance_id, task_id, operator_id, action, from_node_key, to_node_key, comment, created_at)
VALUES (202604280108040001, 202604280108030001, NULL, 202604280101000001, 'SUBMIT', NULL, 'approve', '测试流程提交。', CURRENT_TIMESTAMP);
