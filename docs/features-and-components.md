# 功能与组件

本文按“用户能看到什么、开发者如何接入、底层如何实现”整理 EasyNextAdmin 当前已经具备的功能和组件。

## 功能总览

| 功能域 | 页面入口 | 前端目录 | 后端模块 | 主要权限 |
| --- | --- | --- | --- | --- |
| 工作台 | `/dashboard` | `src/views/dashboard`、`src/features/dashboard` | `module.system`、`module.workflow` | `dashboard:view` |
| 认证授权 | `/login`、`/profile/security`、`/monitor/online` | `src/views/login/LoginView.vue`、`src/stores/auth.ts`、`src/api/request.ts` | `infrastructure.security`、`module.system` | 登录入口公开，业务接口按 `@EasyPermission` |
| 用户管理 | `/system/users` | `src/views/system/UserView.vue`、`src/features/system/userApi.ts` | `module.system` | `sys:user:list/add/edit/delete/import/export` |
| 角色权限 | `/system/roles` | `src/views/system/RoleView.vue` | `module.system` | `sys:role:list/edit` |
| 菜单配置 | `/system/menus` | `src/views/system/MenuView.vue` | `module.system` | `sys:menu:list/edit` |
| 组织架构 | `/system/departments` | `src/views/system/DepartmentView.vue` | `module.system` | `sys:dept:list/edit` |
| 文件中心 | `/system/files` | `src/views/system/FileCenterView.vue` | `module.system` | `sys:file:list/upload/delete` |
| 报表中心 | `/reports/enterprise` | `src/views/report/EnterpriseReportView.vue`、`src/features/report` | `module.report` | `report:view` |
| 运行监控 | `/monitor/server` | `src/views/monitor/MonitorView.vue` | `module.monitor` | `monitor:server:view` |
| 在线用户 | `/monitor/online` | `src/views/monitor/OnlineUserView.vue` | `infrastructure.security` | `monitor:online:view`、`auth:session:revoke` |
| 缓存监控 | `/monitor/cache` | `src/views/monitor/CacheMonitorView.vue` | `module.monitor` | `monitor:cache:view/clear` |
| 缓存列表 | `/monitor/cache-list` | `src/views/monitor/CacheListView.vue` | `module.monitor` | `monitor:cache:view/clear` |
| 实时日志 | `/monitor/weblog` | `src/views/monitor/WebLogView.vue` | `module.system`、`infrastructure.observability` | `monitor:weblog:view/level` |
| 审计中心 | `/audit/behavior` | `src/views/audit/BehaviorAuditView.vue` | `module.audit`、`infrastructure.audit` | `audit:behavior:view` |
| 任务调度 | `/schedule/jobs` | `src/views/schedule/JobView.vue` | `module.schedule` | `schedule:job:list/edit` |
| 流程中心 | `/workflow/*` | `src/views/workflow`、`src/features/workflow` | `module.workflow` | `workflow:*` |
| 消息中心 | `/messages` | `src/views/message/MessageCenterView.vue` | `module.message` | `message:view/read` |
| 接口文档 | `/developer/api-docs` | `src/views/developer/ApiDocsView.vue` | `config.api`、springdoc-openapi | `developer:api-docs:view` |
| 个人中心 | `/profile/security` | `src/views/profile/ProfileSecurityView.vue` | `module.system` | 登录用户可访问 |

## 功能组件使用与设计

| 功能组件 | 使用方式 | 设计要点 |
| --- | --- | --- |
| 工作台 | 登录后进入 `/dashboard`，查看待办、消息、系统状态和快捷入口。 | 只做运营入口，不承载复杂配置；待办、消息和监控指标均来自真实模块数据。 |
| 认证授权 | 登录页调用 `/api/auth/login`，请求拦截器携带当前会话凭证，后端过滤器恢复当前用户，接口注解完成授权。 | 当前代码使用 Bearer token 和服务端会话快照，适合本地开发和前后端分离调试；权限版本让角色、菜单和用户授权变化能立即让旧会话失效。`/api/auth/demo-accounts` 只在 `local` profile 返回演示账号。生产安全路线固定为 HttpOnly Cookie 会话 + CSRF 防护，不把浏览器可读 token 作为生产验收方案。 |
| 系统管理 | 通过用户、角色、菜单、部门页面维护组织和授权；用户管理页支持下载 CSV 模板、导入用户和按筛选条件导出用户。 | 菜单权限、按钮权限和后端权限码同源；用户详情、编辑、启停、删除、重置密码和部门维护都走数据权限边界。用户页维护直属上级并预览审批关系，部门页维护部门负责人，供工作流运行时派单。用户批量导入由 `SysUserImportExportService` 校验 CSV 类型、2MB 大小、1000 行上限、部门名称、角色编码和重复用户名；导出 CSV 会防 Excel 公式注入。 |
| 文件中心 | 在 `/system/files` 上传、预览、下载和清理文件；图片、PDF 和文本类文件可直接预览。 | 业务模块通过文件 API 保存文件元数据，预览和下载复用鉴权下载接口，不在页面拼接裸地址。上传会校验大小、扩展名、MIME 和常见文件头签名，拒绝把可执行文件伪装成图片、PDF 或压缩包；病毒扫描和敏感内容检测应在企业网关或对象存储侧继续补齐。 |
| 报表中心 | 在 `/reports/enterprise` 查看组织人员台账和采购流程复核两张 A4 纸质报表，并可使用浏览器打印。 | 报表接口只读，走 `report:view` 权限和当前账号数据范围；页面用固定版式表格、签核栏和报表编号呈现，不做 BI 配置器或大屏图表。 |
| 运行监控 | 在监控菜单查看服务器、缓存、缓存列表、在线用户和实时日志。 | 面向内网运维排障，默认展示实时状态和 logback 当前文件日志；缓存列表只展示脱敏后的 key/value 预览并支持精确清理单个 key；实时日志级别调整使用独立权限和审计，不做外部 APM 替代。 |
| 审计中心 | 在 `/audit/behavior` 查询登录、操作、异常、接口访问和敏感数据变更记录。 | 关键接口用 `@EasyAudit` 或审计采集器记录；审计查询通过 `AuditVisibilitySupport` 按当前账号数据范围过滤操作者或登录用户，只有全部数据范围可看全局统计；敏感字段在入库前统一交给 `EasySensitiveDataMasker` 脱敏。 |
| 任务调度 | 在 `/schedule/jobs` 查看和维护动态任务。 | 任务通过 `@EasyJob` 声明，数据库维护 Cron、启停状态和执行日志；页面默认展示全宽任务列表，行操作进入单任务日志抽屉，右上角可查看全部日志。 |
| 工作流 | 在 `/workflow/start` 直接填写业务申请，在 `/workflow/tasks` 处理待办和查看我发起的流程，具备流程实例管理权限的管理员在 `/workflow/instances` 监控全部流程实例，在定义页维护轻量审批图。 | 流程定义保存图 JSON，并同步生成节点和连线结构化投影；启用、发布和发起前都会校验图结构；实例详情优先使用发起时快照，避免定义变更影响历史实例。流程实例页用纸张式申请单展示业务详情、申请人、单号和审批记录，并保留流程图和处理动态。审批节点支持任一人、全部、顺序三种审批方式，处理人规则支持指定成员、职能角色、发起人直属上级、发起人部门负责人、发起人上级部门负责人和发起人自选；节点配置里的转办、委派、加签、减签、退回开关会在运行时校验。抄送已读只能由接收人本人或超级管理员操作。参与人统一读取 `/api/system/users/assignees`，历史任务和历史抄送也参与可见性判断。 |
| 消息中心 | 顶部铃铛显示未读数，`/messages` 处理流程、审计和任务消息。 | 消息接口集中在 `src/features/message/api.ts`，顶部铃铛使用 `headerMessages.ts`，避免布局组件直接写请求细节；流程催办消息带业务关联和任务中心跳转链接。消息中心只保留查看和已读处理。 |
| 接口文档 | 在 `/developer/api-docs` 内嵌查看 Swagger UI，也可新窗口打开 `/swagger-ui.html`。 | OpenAPI 入口默认只在 `local` profile 开启；前端开发代理同时转发 `/swagger-ui.html`、`/swagger-ui/*` 和 `/v3/api-docs`。 |
| 个人中心 | 在 `/profile/security` 修改资料、头像和密码。 | 头像使用文件上传和裁剪，不让用户手填图片地址；无头像时使用姓名首字作为占位。 |

## 项目内示例索引

二开时先找当前项目中的真实落点，再复制同类结构。不要为了展示组件单独写空 demo。

### 功能组件示例

| 功能组件 | 页面或接口示例 | 代码位置 | 适合复用的点 |
| --- | --- | --- | --- |
| 工作台 | `/dashboard` | `EnterpriseWorkbenchController`、`EnterpriseWorkbenchService`、`src/views/dashboard` | 聚合统计、快捷入口、待办和消息摘要。 |
| 认证授权 | `/api/auth/login`、`/api/auth/logout` | `AuthController`、`EasyAuthService`、`EasyAuthFilter` | 登录、会话快照、退出、会话校验和权限版本。 |
| 用户管理 | `/api/system/users` | `SysUserController`、`SysUserServiceImpl`、`UserView.vue` | 标准 CRUD、角色绑定、状态切换、数据权限校验、导入导出。 |
| 角色权限 | `/api/system/roles` | `SysRoleController`、`SysRoleServiceImpl`、`RoleView.vue` | 授权配置、数据范围、敏感变更审计和权限版本刷新。 |
| 菜单配置 | `/api/system/menus` | `SysMenuController`、`SysMenuServiceImpl`、`MenuView.vue` | 菜单树、按钮权限、前后端权限码同源维护。 |
| 组织架构 | `/api/system/departments` | `SysDeptController`、`SysDeptServiceImpl`、`DepartmentView.vue` | 树形组织、数据权限部门边界。 |
| 文件中心 | `/api/system/files` | `SysFileController`、`EasyStorageFacade`、`FileCenterView.vue` | 上传、鉴权下载、图片/PDF/文本预览和下载审计。 |
| 报表中心 | `/api/reports/enterprise-paper` | `EnterpriseReportController`、`EnterpriseReportService`、`EnterpriseReportView.vue` | 组织人员台账和采购流程复核的纸质报表数据，按当前账号数据范围生成。 |
| 运行监控 | `/api/monitor/system`、`/api/monitor/statistics` | `SystemStatusController`、`MonitorStatisticsController`、`MonitorView.vue` | JVM、CPU、内存、磁盘、健康状态、接口、在线用户、远程调用和任务统计展示。 |
| 缓存监控 | `/api/monitor/cache` | `CacheMonitorController`、`CacheMonitorService`、`CacheMonitorView.vue`、`CacheListView.vue` | 查看缓存 provider、命中率、大小、key/value 预览和精确清理缓存项。 |
| 在线用户 | `/api/monitor/statistics/online-users` | `MonitorStatisticsController`、`AuthSessionStore`、`OnlineUserView.vue` | 在线会话查询、当前会话标记和踢人下线。 |
| 审计中心 | `/api/audit/*` | `module.audit`、`AuditLogCollector`、`BehaviorAuditView.vue` | 登录、操作、异常、接口访问和敏感变更查询。 |
| 定时任务 | `/api/schedule/jobs` | `ScheduleJobController`、`ScheduleJobManager`、`JobView.vue` | 动态 Cron、启停任务和执行日志。 |
| 工作流 | `/api/workflow/*` | `module.workflow`、`WorkflowTaskCenterView.vue`、`WorkflowInstanceMonitorView.vue` | 流程定义、流程实例监控、待办、审批、转办、加签、催办和消息联动。 |
| 消息中心 | `/api/messages` | `UserMessageController`、`UserMessageService`、`MessageCenterView.vue` | 未读数、流程消息、审计提醒、任务消息、前往业务详情。 |
| 接口文档 | `/swagger-ui.html`、`/v3/api-docs` | `OpenApiConfig`、`ApiDocsView.vue` | 按模块查看 OpenAPI 分组，开发调试时复制 Bearer token 后可在 Swagger UI 调用接口。 |
| 个人中心 | `/api/profile/*` | `ProfileSecurityController`、`ProfileSecurityService`、`ProfileSecurityView.vue` | 资料维护、头像上传裁剪、改密、登录历史和本人会话治理。 |

### 技术组件示例

| 技术组件 | 项目内示例 | 关键文件 | 接入要点 |
| --- | --- | --- | --- |
| 统一响应和异常码 | 所有 JSON Controller | `Response`、`PageResponse`、`GlobalExceptionHandler` | Controller 返回标准响应，业务异常使用 `ErrorCode`，不要裸数字错误码。 |
| 接口权限 | 用户、角色、菜单、工作流接口 | `@EasyPermission`、`EasyPermissionInterceptor`、`EasyPermissions` | 页面按钮隐藏只是体验，后端注解才是真实边界。 |
| 认证会话 | 登录、退出、在线用户 | `EasyAuthService`、`EasyAuthFilter`、`AuthSessionStore` | token 只保存摘要，服务端会话保存权限快照和权限版本。 |
| 数据权限 | 用户、部门、任务查询 | `@DataScope`、`EasyDataScopeInnerInterceptor`、`EasyDataScopeContext` | Mapper 声明范围列，特殊系统查询用 `ignore` 明确绕过。 |
| 缓存 | `/api/monitor/cache`、业务命名缓存 | `EasyCacheConfig`、`CacheMonitorService` | 命名缓存统一 TTL，监控页展示命中率、容量和脱敏 value 预览；敏感或强数据权限详情不做共享缓存。 |
| 审计 | 退出登录、清理缓存、角色授权 | `@EasyAudit`、`AuditLogCollector`、`SensitiveAuditService` | 操作审计走注解，敏感数据变更走显式服务。 |
| 脱敏 | 审计参数、接口访问日志、实时日志 | `EasySensitiveDataMasker`、`@EasyMask` | DTO 输出用注解，Map/请求参数/日志文本用组件。 |
| Trace / MDC | HTTP、定时任务、Kafka、异步线程池 | `EasyTraceIdFilter`、`EasyTraceIdContext`、`EasyMdcContext`、`EasyNextAdminMdcThreadPoolExecutor`、`logback.xml` | 入口没有 `X-Trace-Id` 时统一创建，跨线程、Kafka 生产消费和远程调用透传，日志打印 `traceId` 和认证后的 `userId`。 |
| 指标采集 | 用户管理、审计、监控 Controller | `@EasyMetrics`、`EasyMetricsAspect` | 给接口层采集耗时、结果和异常计数。 |
| 轻量 Trace Tree | HTTP、定时任务、Kafka Consumer、MyBatis 查询/更新 | `TraceContext`、`@EasyTrace`、`TraceCodeBlock`、`EasyHttpSlowRequestInterceptor`、`EasyMybatisTraceInterceptor` | 不依赖外部 tracing；入口超出阈值或异常时打印本地调用树，MyBatis 层 tag 只保留 `SqlCommandType`，连续重复叶子节点聚合为 `count/total/min/max`。 |
| 定时任务 | 本地消息重试 | `@EasyJob`、`ScheduleJobManager` | 任务实现 `EasyJobHandler`，最终启停和 Cron 以数据库为准。 |
| 本地消息 | 失败消息重试 | `EasyLocalMessageTemplate`、`LocalMessageRetryJob` | 本地事务先落消息，远程/耗时操作失败后由任务重试。 |
| 幂等 | 重复提交保护 | `@Idempotent`、`IdempotentAspect` | 用业务 key 防止重试或重复提交造成重复写入。 |
| 重复请求限制 | 用户保存 `POST /api/system/users` | `@EasyDuplicateRequestLimiter`、`ConcurrentHashMapDuplicateRequestLimiter` | 适合表单短时间重复点击，不替代长期幂等。 |
| 限流 | 登录和验证码接口 | `@EasyRateLimit`、`EasyRateLimiterAspect`、`InMemoryRateLimiter` | 适合登录、验证码、导出等高风险入口按 IP、用户或全局限流。 |
| 分布式锁 | 锁基础设施 | `IEasyLocker`、`MysqlEasyLocker`、`RedisEasyLocker` | 适合跨实例互斥任务，优先放在任务或关键业务服务边界。 |
| 安全分页查询 | 参数解析组件和单元测试 | `PageRequestArgumentResolver`、`@PageQuery`、`PageRequestArgumentResolverTest` | 新列表接口可直接接入字段白名单，避免前端控制 SQL 列名。 |

### 系统管理模型

用户、角色、菜单权限和组织是脚手架的基础域，不建议在业务模块里直接散写 SQL。当前实现把常用关系查询收口到专门的 Mapper / Service：

| 场景 | 入口 | 设计要点 |
| --- | --- | --- |
| 用户列表 | `SysUserServiceImpl#pageUsers`、`SysUserRelationService` | 用户分页只查主表；部门名称和角色绑定用批量投影补齐，避免逐行查部门、逐行查角色。 |
| 审批关系 | `SysUserServiceImpl#saveUser`、`SysDeptServiceImpl#saveDepartment`、`SysUserRelationService` | 用户表维护 `manager_user_id` 作为直属上级，部门表维护 `leader_user_id` 作为部门负责人；用户列表批量补齐直属上级、部门负责人和上级部门负责人，流程配置页直接使用这些企业组织关系。 |
| 用户角色绑定 | `ISysUserRoleService`、`SysUserRoleMapper` | `sys_user_role` 是纯关系表，保存前按用户硬删旧绑定，再批量插入新绑定；初始化 SQL 对 `(user_id, role_id)` 加唯一约束，防止重复授权。 |
| 角色列表用户数 | `SysRoleServiceImpl#pageRoles`、`SysUserRoleMapper#countUsersByRoleIds` | 用户数由数据库按角色聚合，不把整张关系表拉回应用层再分组。 |
| 角色权限保存 | `SysRoleServiceImpl#saveRolePermissions`、`ISysRolePermissionService` | 保存授权时先按角色硬删旧权限，再批量写入选中权限及其父级导航；保存后递增权限版本并写敏感变更审计。 |
| 当前用户菜单 | `SysMenuMapper#findEnabledByUserId` | 普通用户菜单通过 `sys_user_role -> sys_role_permission -> sys_menu` 一次 JOIN 查询；超级管理员直接读取启用资源。 |
| 组织层级 | `SysDeptServiceImpl#saveDepartment` | 保存部门时统一计算 `full_name` 和 `tree_path`，校验父级存在、禁止把父级改成自己或下级；删除部门前校验下级部门和部门用户。 |

二开建议：

- 关系表如果只表达绑定关系，优先使用“硬删除旧关系 + 批量插入新关系 + 唯一约束”的模式，避免逻辑删除关系表反复改动后出现重复数据或唯一键冲突。
- 列表展示的派生字段优先做批量补齐或数据库聚合，不要在循环里调用 Service / Mapper。
- 组织树字段由服务层统一维护，页面只提交 `deptName`、`pid`、`address`、`leaderUserId`、`status`、`sort` 等业务输入，不要让前端直接拼 `treePath`。

## 菜单与权限资源

EasyNextAdmin 不再维护前端硬编码菜单清单。目录、页面、按钮和角色授权资源统一来自服务端 `sys_menu`，角色授权页、侧边栏、页面路由和页面权限判断都读同一份数据，避免“授权配置”和“真实导航”对不上的问题。

角色授权页通过 `/api/system/roles/permission-resources` 实时读取 `sys_menu`，保存授权时后端会校验每个权限码是否真实存在，不再静默丢弃无效权限。用户创建和编辑时也会校验绑定角色必须存在且启用，避免导入、接口调用和页面选择出现不一致授权。

页面资源最少需要维护这些字段：

- `type = 1`：页面节点；`type = 0` 表示目录分组，`type = 2` 表示按钮。
- `href`：页面路由，例如 `/messages`。
- `permission_code`：页面权限码，例如 `message:view`。
- `component_path`：本地页面路径，例如 `@/views/message/MessageCenterView.vue`。
- `visible`、`enable`、`sort`：控制菜单可见性、资源启停和排序。

按钮资源使用 `type = 2` 挂在页面节点下，只维护按钮名称、权限码和用途说明。后端控制器使用同名 `EasyPermissions` 常量兜底，前端按钮用 `v-permission` 隐藏或禁用。

新增页面的最小 SQL 示例：

```sql
INSERT INTO sys_menu
    (id, title, type, href, icon, permission_code, component_path, pid, sort, visible, enable)
VALUES
    (9000, '消息中心', 1, '/messages', 'Bell', 'message:view',
     '@/views/message/MessageCenterView.vue', 0, 120, 1, 1);

INSERT INTO sys_menu
    (id, title, type, permission_code, pid, sort, visible, enable)
VALUES
    (9001, '全部已读', 2, 'message:read-all', 9000, 10, 0, 1);
```

前端动态路由由 `easy-next-admin-web/src/router/dynamicRoutes.ts` 负责。它只在 Vite 已知的 `src/views/**/*.vue` 中解析 `component_path`，不会执行数据库传入的任意前端代码。

## 权限使用

页面可见性由 `/api/auth/me` 返回的授权菜单决定。页面路由生成后会把响应字段 `permissionCode` 写入 route meta，路由守卫统一校验。

按钮权限：

```vue
<el-button v-permission="'sys:user:add'" type="primary">
  新增用户
</el-button>
```

后端接口权限：

```java
@EasyPermission(EasyPermissions.System.USER_ADD)
@PostMapping
public Response<Void> save(@RequestBody UserRequest request) {
    userService.saveUser(request);
    return Response.ok();
}
```

实现原则：

- 前端负责隐藏不可见菜单和按钮，提升使用体验。
- 后端 `@EasyPermission` 是真正的权限边界，所有写操作和敏感查询都必须配置。
- 权限码在 `sys_menu`、后端 `EasyPermissions` 和前端 `PermissionCodes` 中保持同名；`sys_menu` 决定资源树，后端注解决定接口边界，前端常量只用于按钮指令。

## 通用前端组件

### EasyChart

位置：`easy-next-admin-web/src/components/charts/EasyChart.vue`

用于封装 ECharts 初始化、响应式尺寸和销毁逻辑。适合监控和工作台图表。

```vue
<EasyChart :option="chartOption" height="320px" />
```

### TableToolbar

位置：`easy-next-admin-web/src/components/table/TableToolbar.vue`

用于标准 CRUD 页面的查询区、刷新、新增、导出等工具栏承载。页面应保持“筛选区 + 工具栏 + 表格 + 分页”的企业后台结构。

### v-permission

位置：`easy-next-admin-web/src/directives/permission.ts`

用于按钮级权限控制。默认会禁用无权限按钮并显示原因，避免布局跳动。

```vue
<el-button v-permission="{ permissions: 'sys:user:export', mode: 'disable' }">
  导出
</el-button>
```

### API 请求封装

位置：

```text
easy-next-admin-web/src/api/request.ts
easy-next-admin-web/src/features/*/api.ts
```

页面不直接写 Axios 请求。新增接口时先放到对应 `features/*/api.ts`，页面只调用业务函数。

当前前端把登录响应中的 access token 写入 Pinia 并持久化到 `localStorage`，然后由 `request.ts` 放入 `Authorization: Bearer ...` 请求头。这种方式便于本地开发和前后端分离调试，但不应作为公开生产环境的最终会话方案：一旦页面出现 XSS、浏览器扩展注入、第三方脚本污染或调试台泄露，脚本可以直接读取 `localStorage` 中的 token 并转移到攻击者环境。

企业级生产路线固定为服务端会话 + `HttpOnly; Secure; SameSite` Cookie，并为写接口配置 CSRF 防护。当前 Bearer token 路线只保留为开发调试路线：

- 生产环境把会话标识放入 `HttpOnly; Secure; SameSite=Lax/Strict` Cookie，前端 JavaScript 不读取会话标识。
- 写接口增加 CSRF 防护，例如 SameSite Cookie + CSRF token 请求头，或双提交 Cookie。
- 会话通过服务端续期和撤销控制，并支持空闲超时、绝对超时、权限版本和服务端主动撤销。
- CORS 使用 `easy.web.cors` 明确白名单，不反射任意 Origin；安全响应头通过 `easy.web.security-headers` 配置，生产 HTTPS 可开启 HSTS 并逐步收紧 CSP。
- 前端只保存用户展示信息、菜单和权限展示状态；不要把可直接调用接口的密钥、refresh token 或长期凭证放入浏览器可读存储。

### 侧边栏与标签页

位置：

```text
easy-next-admin-web/src/layout/AppLayout.vue
easy-next-admin-web/src/layout/SidebarMenuNode.vue
easy-next-admin-web/src/layout/TagsView.vue
```

侧边栏菜单来自 `/api/auth/me` 的 `menus`，前端按 `visible`、`enable` 和父子层级渲染。标签页状态由 Pinia store `src/stores/tagsView.ts` 管理。

## 后端基础组件

### 统一响应

位置：

```text
common/model/Response.java
common/model/PageResponse.java
common/exception/ErrorCode.java
infrastructure/web/handler/EasyResponseBodyAdvice.java
infrastructure/web/handler/GlobalExceptionHandler.java
```

普通接口返回 `Response<T>`，分页接口返回 `PageResponse<T>`。响应体固定使用 `code`、`message` 和业务 `data`。参数校验失败、字段错误等错误明细放 `details`，但 `details` 只在非空时返回；不要返回 `details: null`，也不要把错误数组混入业务数据。成功与否只看 `code == 0`，不再返回派生字段 `success`，避免同一响应里出现 `code` 和 `success` 不一致。

`PageResponse<T>` 的泛型 `T` 表示单条记录类型，不是列表类型。正确写法是 `PageResponse<SysUser>`，响应里的 `data` 固定是 `PageData<SysUser>`；不要写 `PageResponse<List<SysUser>>`，否则接口语义会变成“分页响应里再套一层列表类型”，生成的 OpenAPI 和前端类型都会变得不直观。分页数据只包含 `list` 和 `total`：`list` 是当前页记录，`total` 是匹配条件的总数。当前页码和每页数量属于请求条件，前端状态已经持有，不在响应体里重复派生。

错误码由 `ErrorCode` 统一维护，HTTP 状态表达协议层结果，响应体 `code` 表达稳定业务错误。成功固定为 `0`；错误码采用“HTTP 状态码 + 三位业务序号”，例如 `400004` 表示参数校验失败，`400100` 表示普通业务失败。前端可按 `Math.trunc(code / 1000)` 识别 401、403 等大类，但不要把业务码当成 HTTP 状态码。

常用错误码：

| 错误码 | HTTP 状态 | 含义 |
| --- | --- | --- |
| `0` | 200 | 成功 |
| `400000` | 400 | 请求参数错误 |
| `400004` | 400 | 参数校验失败，字段明细在 `details` |
| `400100` | 400 | 业务处理失败 |
| `401000` | 401 | 未登录或登录已过期 |
| `401001` | 401 | 用户名或密码不正确 |
| `401002` | 401 | 验证码错误、过期或缺失 |
| `401003` | 401 | 会话已过期 |
| `401004` | 401 | 权限版本已变化，需要重新登录 |
| `403000` | 403 | 无访问权限 |
| `403001` | 403 | 账号已被禁用 |
| `404000` | 404 | 资源不存在 |
| `409000` | 409 | 资源冲突或重复 |
| `413000` | 413 | 上传文件过大 |
| `415000` | 415 | 不支持当前媒体类型 |
| `429000` | 429 | 请求过于频繁 |
| `500000` | 500 | 服务端未知异常 |

本项目的公开响应保留数字型 `code`。数字码适合前端分组判断、日志检索、监控聚合和文档表格；字符串语义由 `ErrorCode` 枚举名承载，代码里不要再额外发明一套字符串码。只有在项目后续要开放给外部第三方长期集成时，才建议新增 `errorKey` 字段，并让它直接来自 `ErrorCode.name()`，避免“双码表”不一致。

业务代码抛 `BusinessException` 时，默认归类为 `400100`；确实需要表达不存在、冲突、文件过大、媒体类型不支持等稳定语义时传入具体 `ErrorCode`。不要写 `new BusinessException("xxx", 400)` 这类裸数字构造；错误语义必须收敛到 `ErrorCode`，否则后续检索、监控和前端处理都会分叉。

认证和授权单独分层：认证失败抛 `EasyAuthException`，只能使用 401 系列错误码；已登录但无权访问抛 `EasyForbiddenException`，只能使用 403 系列错误码。登录密码错误、验证码错误、会话过期和权限版本变化要传入对应认证错误码；账号禁用、按钮权限不足、数据不可见等属于 403。异常统一由 `GlobalExceptionHandler` 转换为一致错误结构。链路追踪号只使用 `X-Trace-Id` 请求头和响应头，不再维护额外请求编号；服务端会把 `traceId` 和认证后的 `userId` 写入 MDC，日志排查时可直接按这两个字段检索。

### 安全分页查询

位置：

```text
infrastructure/web/mvc/PageRequest.java
infrastructure/web/mvc/PageQuery.java
infrastructure/web/mvc/PageQueryField.java
infrastructure/web/mvc/PageRequestArgumentResolver.java
```

用于需要“分页 + 通用筛选 + 通用排序”的列表接口。Controller 不能裸用 `PageRequest`，必须用 `@PageQuery` 声明字段白名单：

```java
enum UserQueryField implements PageQueryField {
    USERNAME("username", "username"),
    STATUS("status", "status"),
    CREATED_AT("createdAt", "created_at");

    private final String paramName;
    private final String columnName;

    UserQueryField(String paramName, String columnName) {
        this.paramName = paramName;
        this.columnName = columnName;
    }

    public String paramName() {
        return paramName;
    }

    public String columnName() {
        return columnName;
    }
}

@GetMapping
public PageResponse<SysUser> page(@PageQuery(fields = UserQueryField.class, maxSize = 100) PageRequest pageRequest) {
    Page<?> page = pageRequest.toPage();
    QueryWrapper<?> wrapper = pageRequest.getQueryWrapper();
    // 继续叠加数据权限、业务固定条件，再交给 Mapper / Service 查询。
}
```

前端请求示例：

```text
GET /api/system/users?page=1&size=10&filter=username|like|admin,status|eq|1&sort=createdAt|desc
```

设计规则：

- 前端只传 `paramName`，例如 `createdAt`；真实列名 `created_at` 只来自服务端枚举。
- 未在枚举中声明的筛选字段和排序字段会返回 `400004 参数校验失败`。
- `size` 必须大于等于 1，且不能超过 `@PageQuery.maxSize`。
- 字段枚举可通过 `filterable()`、`sortable()` 和 `allowedOperators()` 限制某个字段是否允许筛选、排序或使用指定操作符。
- 该组件只解决通用列表条件解析；业务强约束、数据权限和固定过滤条件仍应在 Service 层显式追加。

典型响应示例：

用户分页查询成功，HTTP 状态为 200：

```json
{
  "code": 0,
  "message": "操作成功",
  "data": {
    "list": [
      {
        "userId": 202604280101000001,
        "userName": "admin",
        "nickName": "超级管理员"
      }
    ],
    "total": 7
  }
}
```

登录请求缺少用户名，属于入参校验失败，HTTP 状态为 400：

```json
{
  "code": 400004,
  "message": "参数校验失败",
  "data": null,
  "details": [
    {
      "field": "username",
      "message": "请输入用户名"
    }
  ]
}
```

登录用户名或密码错误，属于认证失败，HTTP 状态为 401：

```json
{
  "code": 401001,
  "message": "用户名或密码不正确"
}
```

创建用户时未选择部门，属于可预期业务规则失败，HTTP 状态为 400：

```json
{
  "code": 400100,
  "message": "请选择部门"
}
```

创建用户触发唯一约束或重复资源，属于资源冲突，HTTP 状态为 409：

```json
{
  "code": 409000,
  "message": "数据库中已存在该记录"
}
```

### 缓存组件

位置：

```text
config/EasyCacheConfig.java
module/system/service/impl/SysUserServiceImpl.java
module/monitor/service/CacheMonitorService.java
```

缓存组件按“命名缓存 + 明确 TTL”配置。`easy.features.redis=true` 时缓存管理器使用 Redis / Redisson，并通过 Micrometer 读取 Redisson Spring Cache 的命中、未命中和清理统计；关闭 Redis 时自动回退到 Caffeine，本地 fallback 也保持 `CACHE_NAME_1H`、`CACHE_NAME_12H`、`CACHE_NAME_24H` 各自对应的过期语义。

Redis 是能力级开关，不需要分别维护“缓存是否用 Redis、会话是否用 Redis、锁是否用 Redis”等细碎配置。相关开关和连接参数都放在 `easy` 命名空间下：`easy.features.redis` 控制是否启用 Redis 能力，`easy.spring.redis.*` 控制连接地址、数据库、密码和客户端名称。启用后，会话、验证码、重复请求、幂等、限流和分布式锁都会通过 Redis/Redisson 实现；未启用时分别回退到内存、Caffeine 或 MySQL。

Kafka 也是能力级开关。`easy.features.kafka=true` 只负责启用 Kafka 基础设施 Bean，包括 Producer、Consumer、Topic Admin、消费慢链路追踪和健康检查；未启用时这些 Bean 不会创建。业务消息目前仍以数据库本地消息表作为默认最终一致性方案，后续如果要把具体业务事件切到 Kafka，需要先抽象明确的事件发布端口，再按业务场景逐步接入。

Feign、调度、监控、WebLog、OSS、本地消息和 Influx 指标导出也保持“显式开关 + 默认不误连外部依赖”的原则。`easy.features.feign` 只启用远程调用基础配置，不代表默认存在外部服务调用；Influx 指标导出默认关闭，需要企业已有指标平台时再通过 `MANAGEMENT_INFLUX_METRICS_EXPORT_ENABLED=true` 接入。

当前项目的缓存以命名缓存和监控治理为主：

```text
GET    /api/monitor/cache/overview
GET    /api/monitor/cache/{cacheName}/entries
DELETE /api/monitor/cache/{cacheName}
DELETE /api/monitor/cache/{cacheName}/entries?key=...
```

`CacheMonitorService` 会读取 Spring Cache / Redisson / Caffeine 的运行状态，展示 provider、TTL、估算大小、命中率、淘汰次数和 key/value 预览。value 预览在返回前会经过 `EasySensitiveDataMasker` 脱敏，并限制最大长度，避免缓存监控页面变成敏感数据泄露入口。

数据权限组件使用独立命名缓存：`DATA_SCOPE_DEPT_TREE` 缓存有效部门树，部门维护通过 `@CacheEvict` 失效；`DATA_SCOPE_CUSTOM_DEPT_IDS` 按用户缓存自定义部门范围，角色授权或用户角色变化后失效。`CacheManager` 统一使用事务感知代理，缓存写入和失效在事务提交后生效。

用户详情、用户编辑、删除、重置密码和部门维护这类强数据权限接口不做共享详情缓存，避免“缓存命中绕过当前用户数据范围”的问题。需要缓存的新业务必须先判断数据是否和当前用户权限强相关；如果强相关，优先不缓存或把权限范围纳入 key。

接入规则：

- 优先缓存读多写少、可容忍短 TTL、且不依赖当前用户数据范围的查询结果，例如字典、菜单树和聚合统计。
- 缓存 key 必须带业务前缀，例如 `system:menu:tree:{scope}` 或 `dict:item:{dictCode}`，避免同一个 TTL 缓存里不同业务 key 冲突。
- 写操作必须精确驱逐相关 key；只有无法计算影响范围时才考虑清理整个命名缓存。
- 不要缓存包含明文密码、token、验证码、临时授权码或大文件内容的对象。
- 缓存运行状态在 `/monitor/cache` 查看；缓存 key/value 在 `/monitor/cache-list` 查看。清理缓存和清理 key 动作都会进入审计。

### 限流与防重复提交

位置：

```text
infrastructure/ratelimit
infrastructure/idempotency/duplicate
module/system/controller/AuthController.java
module/system/controller/SysUserController.java
```

限流用于保护高风险入口，例如登录和验证码。当前项目在认证接口上按客户端 IP 限制访问频率：

```java
@EasyRateLimit(
        key = "auth:login",
        limit = 10,
        timeWindow = 1,
        timeUnit = TimeUnit.MINUTES,
        type = EasyRateLimitType.CLIENT_IP,
        message = "登录请求过于频繁，请稍后再试")
@PostMapping("/login")
public Response<AuthTokenResponse> login(@Valid @RequestBody AuthLoginRequest loginRequest,
                                         HttpServletRequest servletRequest) {
    return Response.ok(authService.login(loginRequest, servletRequest));
}
```

防重复提交用于处理用户短时间重复点击。当前项目在用户保存接口上按“当前用户 + 用户 ID / 用户名”生成短期重复提交 key：

```java
@EasyDuplicateRequestLimiter(
        businessKey = "system:user:save",
        businessParam = "#userRequest.userId == null ? #userRequest.userName : #userRequest.userId",
        timeout = 2)
@PostMapping
public Response<Boolean> saveOrUpdate(@RequestBody UserRequest userRequest) {
    return Response.ok(sysUserService.saveUser(userRequest));
}
```

使用边界：

- `@EasyRateLimit` 是访问频率保护，适合登录、验证码、导出、上传等入口。
- `@EasyDuplicateRequestLimiter` 是短窗口重复提交保护，适合表单保存按钮，不保证长期幂等。
- 长期幂等需要业务幂等 key 或 `@Idempotent`，例如本地消息重试场景。

### 认证会话

位置：

```text
infrastructure/security/filter/EasyAuthFilter.java
infrastructure/security/service/EasyAuthService.java
infrastructure/security/store
```

登录成功后服务端创建访问令牌和会话快照。启用 Redis 时会话、验证码和登录失败标记存入 Redis；未启用 Redis 时回退内存。

完整链路：

1. 账号创建：`SysUserController` 调用 `SysUserServiceImpl`，新密码通过 `EasyPasswordHasher` 写成 BCrypt 摘要，用户角色关系写入 `sys_user_role`。
2. 登录：`AuthController.login` 调用 `EasyAuthService.login`，先判断验证码风险标记，再按用户名查用户、校验 BCrypt、检查账号启用状态。
3. 会话创建：登录成功后生成随机 access token，只把 token 摘要保存到 `AuthSessionStore`，会话快照保存用户、角色、权限、部门、数据范围和权限版本。空闲超时默认 `30m`，绝对超时默认 `8h`，可通过 `easy.auth.session.*` 或对应环境变量覆盖。生产路线应把会话标识写入 HttpOnly Cookie，不暴露给前端脚本。
4. 会话验证：`EasyAuthFilter` 在当前实现中从 `Authorization` 头读取 Bearer token，查会话、检查状态、空闲过期时间和绝对过期时间，必要时滑动续期，但不会超过绝对超时。生产 Cookie 会话落地时应从 Cookie 恢复会话，并对写接口执行 CSRF 校验。
5. 认证上下文：认证成功后把 `AuthPrincipal` 和原始 token 写入 `EasySecurityContext`。请求结束必须清理上下文，避免 Servlet 线程复用导致串用户。
6. 鉴权：`EasyPermissionInterceptor` 读取 Controller 或方法上的 `@EasyPermission`，按当前用户权限码判断是否放行；超级管理员角色编码 `admin` 可以跳过权限码校验。
7. 退出和会话治理：`/api/auth/logout` 撤销当前会话；在线用户和个人中心会话管理通过 `AuthSessionStore.revoke` 下线指定会话；修改密码后撤销其他会话。

权限版本：

`sys_user.permission_version` 是当前用户授权快照的版本号。登录时会把版本号放入会话快照；每次请求恢复会话时，后端会读取用户当前版本并和快照比较。如果用户角色绑定、角色权限、角色数据范围、菜单资源或账号状态发生变化，相关服务会递增权限版本；旧会话下一次请求发现版本不一致后立即失效并要求重新登录。

这样做的目的不是防止密码攻击，而是解决“权限已经收回但旧 token 还带着旧权限”的问题。二开时只要改动会影响用户实际权限，就要调用 `PermissionVersionService`：

- 修改某个用户角色、账号启停或重置关键授权状态：`increaseForUser(userId)`。
- 修改某个角色的权限、数据范围或状态：`increaseForRole(roleId)`。
- 修改菜单权限资源或全局授权语义：`increaseForAllUsers()`。

生产强化建议：

- 演示账号接口只在本地或体验环境开放，生产关闭或不返回密码。
- 默认密码改为一次性临时密码或邀请链接，首次登录强制修改密码。
- 登录失败按账号、IP 和设备维度限速，达到阈值后冷却或锁定。
- 高危动作如角色授权、踢人下线、重置密码可叠加二次确认或 MFA。
- 对 `/api/**` 建议采用默认拒绝策略：除 `@EasyIgnoreAuth`、`@EasyPermission` 或明确登录态接口外，其余接口不放行。

### 数据权限

位置：

```text
infrastructure/security/datascope
infrastructure/security/datascope/model/DataScopeType.java
```

角色上保存数据范围。查询执行时，MyBatis 拦截器根据当前登录用户、角色数据范围和部门树自动追加过滤条件。

完整使用方式、原理和方案取舍见 [数据权限组件](components/security/data-scope.md)。

### 通用脱敏组件

位置：

```text
infrastructure/security/masking/EasyMask.java
infrastructure/security/masking/EasyMaskType.java
infrastructure/security/masking/EasySensitiveDataMasker.java
```

成熟企业后台通常不会只靠一种脱敏方式。EasyNextAdmin 采用“字段注解 + 边界组件”的分层方案：

- 字段注解：响应 DTO、导出 DTO、审计快照 DTO 上使用 `@EasyMask`，由 Jackson 序列化时自动输出脱敏值。
- 边界组件：请求参数、Map、URI、异常文本、接口日志和审计日志使用 `EasySensitiveDataMasker`，因为这些场景没有稳定 DTO 字段注解可依赖。
- 业务存储：数据库仍按业务需要保存原值；进入日志、审计、导出文件或对外响应前必须转换成脱敏结果。
- 前端展示：优先展示服务端已经脱敏的字段，不把浏览器端遮盖当成安全边界。

`@EasyMask` 适合明确的字段级输出：

```java
public record UserProfileView(
        @EasyMask(type = EasyMaskType.NAME) String realName,
        @EasyMask(type = EasyMaskType.PHONE) String phone,
        @EasyMask(type = EasyMaskType.EMAIL) String email,
        @EasyMask(type = EasyMaskType.BANK_CARD) String bankCard,
        @EasyMask String token) {
}
```

`EasySensitiveDataMasker` 是后端统一脱敏组件，审计、接口日志、异常日志和后续业务模块都应复用它，不要在各模块重复写正则或 JSON 处理逻辑。它提供四类能力：

- `toSanitizedJson(value)`：把对象或 Map 转成 JSON，并按字段名脱敏。
- `toSanitizedCompactJson(value)`：脱敏后移除空字符串和 `null`，适合审计请求参数。
- `sanitizeJsonText(text)`：处理已有 JSON 字符串，保留结构并脱敏。
- `maskText(text)` / `maskUri(uri)`：处理普通文本和 URL 查询参数。

默认脱敏规则：

- `password`、`token`、`authorization`、`captcha`、`secret`、`apikey` 等字段全量替换为 `******`。
- `phone`、`mobile`、`tel` 等手机号字段保留前三后四。
- `email`、`mail` 等邮箱字段保留首字母和域名。
- `realName` 保留首字。
- `idCard`、`identityNo` 等证件号保留前六后四。
- `bankCard`、`cardNo` 等卡号保留前四后四。
- `ServletRequest`、`ServletResponse`、`MultipartFile`、`BindingResult`、输入输出流和 `Principal` 这类框架对象不会作为业务参数落审计。

使用示例：

```java
@Service
public class BusinessAuditService {
    private final EasySensitiveDataMasker masker;

    public BusinessAuditService(EasySensitiveDataMasker masker) {
        this.masker = masker;
    }

    public String safePayload(Object payload) {
        return masker.toSanitizedCompactJson(payload);
    }
}
```

不同场景的使用方式：

| 场景 | 推荐方式 | 说明 |
| --- | --- | --- |
| API 响应 DTO | `@EasyMask` | 字段语义清晰，直接在序列化阶段输出脱敏值。 |
| 导出文件 DTO | `@EasyMask` 或导出前调用组件 | 导出属于数据外发边界，不能直接复用数据库实体原值。 |
| 审计请求参数 | `AuditRequestPayloadFormatter` + `EasySensitiveDataMasker` | 过滤框架对象、空值和 `null`，再按字段名脱敏。 |
| 接口访问日志 / 实时日志 | `EasySensitiveDataMasker` | Map、数组、原始请求体和响应片段没有稳定注解。 |
| URL 查询参数 | `maskUri(uri)` | 防止 token、验证码、授权参数出现在日志和审计详情里。 |
| 异常消息 / 普通文本 | `maskText(text)` | 用作兜底，优先级低于结构化 JSON 脱敏。 |
| 数据库持久化 | 原值按业务保存，另建脱敏视图或 DTO | 脱敏不是加密；需要保护存储时应使用加密或哈希。 |

设计原则：

- 脱敏发生在入库、写日志或对外展示前，原始请求对象只在当前请求内使用。
- 优先使用结构化 JSON 脱敏，不把对象先拼成字符串再处理。
- 新增字段级输出时优先加 `@EasyMask`；新增通用字段名时修改组件的字段集合，并补充单元测试。
- 组件只负责“遮盖和压缩可记录内容”，不承担权限判断、加密或数据访问控制。

### 审计

位置：

```text
infrastructure/audit
module/audit
infrastructure/security/masking
```

`@EasyAudit` 和审计采集器记录登录、关键操作、数据变更、错误和 API 访问日志。系统管理、文件中心、消息已读、定时任务和工作流处理等关键写入口使用语义化 `@EasyAudit` 标注模块、动作、业务类型和变更类型。审计请求参数由 `AuditRequestPayloadFormatter` 组装成有字段名的 JSON，并复用 `EasySensitiveDataMasker` 过滤框架对象、脱敏敏感字段和移除空值，避免出现 `HttpServletRequest` 包装对象、数组式参数或 `null` 噪声。审计中心按类型聚合展示。涉及菜单发布、权限授权等敏感变更时，业务服务调用 `SensitiveAuditService` 写入数据变更日志，页面的“敏感变更”视图展示这些真实记录。

### 运行监控

位置：

```text
module/monitor
infrastructure/observability
```

服务监控读取 JVM、CPU、内存、线程、磁盘和 GC 水位，并用图表展示资源压力、内存结构、磁盘容量和 GC 状态；实时日志读取 logback 当前文件日志的尾部快照，支持关键词、级别和行数过滤。服务监控页面不展示 Actuator 明细清单。

### 动态定时任务

位置：

```text
module/schedule
```

任务定义存入数据库，调度管理器按任务编码、Cron、启停状态注册运行任务，并记录执行日志。

### 轻量工作流

位置：

```text
module/workflow
easy-next-admin-web/src/views/workflow
easy-next-admin-web/src/features/workflow
```

流程定义使用图结构 JSON 保存。保存当前版本和发布新版本时，后端会把节点、审批规则和连线条件同步到 `wf_process_node`、`wf_process_transition`，让图设计、接口契约和表结构形成可查询闭环。发布后生成版本，实例绑定发起时版本。启用、发布和发起前会校验图结构，拒绝环路、多出口无默认路径、不支持的条件表达式、无效审批人和无可用成员的角色。运行时拆分为流程实例、待办任务、历史任务、抄送和事件记录；实例监控页和“我发起的”按运行中流程和历史流程分开查询，单范围查询走数据库分页。前端使用 LogicFlow 展示和维护流程图，审批节点使用人员图标表达“需要人处理”。审批方式由 `approveType` 控制：`ANY_ONE` 任一人处理即流转，`ALL` 所有人处理后流转，`SEQUENTIAL` 按处理人顺序逐个生成待办；节点动作开关控制转办、委派、加签、减签和退回是否允许，避免配置字段只展示不生效。

审批人解析遵循“组织关系优先、职能角色补充”的企业后台模型：直属上级来自用户管理的 `manager_user_id`，部门负责人来自组织架构的 `leader_user_id`，跨层级审批使用上级部门负责人，财务、审计、运维等横向职能用角色解析。发起人就是本部门负责人时，部门负责人规则会自动上跳到上级部门负责人，避免自审。每个待办都会记录 `assignment_rule_type`、`assignment_rule_name` 和 `assignment_resolve_path`，用于审计和问题排查；实例详情只展示处理节点、处理人、时间、耗时和意见，避免把内部派单规则直接暴露到业务流水里。

催办属于流程事件，同时会在消息中心给当前待办处理人生成未读流程消息，消息链接可打开对应流程详情。

### 用户导入导出

位置：

```text
module/system
easy-next-admin-web/src/views/system/UserView.vue
easy-next-admin-web/src/features/system/userApi.ts
```

用户管理页提供“导入用户”和“导出用户”按钮。导入流程先下载 CSV 模板，模板使用“部门名称”和“角色编码”完成组织和角色映射；上传后端会逐行校验必填项、重复用户名、部门状态和角色状态，并返回成功数、失败数和行级错误。导出按当前筛选条件直接返回用户 CSV，适合二开时复制到其他业务模块各自实现，不保留独立中心页。

## 新增功能建议流程

新增后台页面时按 [新增系统页二开流程](development/adding-system-page.md) 执行，重点保持这些契约同步：

1. 后端 `module/<domain>` 控制器、服务、实体、Mapper、DTO 和标准响应结构。
2. `@EasyPermission`、`EasyPermissions`、前端 `PermissionCodes`、MySQL `sys_menu` 和 H2 seed。
3. 前端 `src/features/<domain>` API/types、`src/views/<domain>` 页面和 `component_path`。
4. 本文档中的功能表、权限说明和示例索引。
5. 后端 `verify` 或打包、前端 `npm run build`，按影响范围补充单元测试。
