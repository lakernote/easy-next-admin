# 新增系统页二开流程

本文面向二开开发者和 code agent，说明如何在 EasyNextAdmin 中新增一个真实企业后台页面。目标是让后端接口、前端页面、菜单资源、权限码、初始化数据、测试和文档保持同一套契约。

不适用场景：

- 只展示 UI 的 demo 页。
- 低代码、BI、完整 BPM 或抽象扩展中心。
- 没有后端菜单资源和接口权限兜底的纯前端页面。

## 先找同类实现

修改前先用 `rg` / `rg --files` 找一个最接近的现有模块。常用参考：

| 场景 | 后端参考 | 前端参考 |
| --- | --- | --- |
| 标准 CRUD | `SysUserController`、`SysUserServiceImpl` | `src/views/system/UserView.vue`、`src/features/system/userApi.ts` |
| 角色授权 / 树形权限 | `SysRoleController`、`SysRoleServiceImpl` | `src/views/system/RoleView.vue`、`src/features/system/roleApi.ts` |
| 文件上传下载 | `SysFileController`、`EasyStorageFacade` | `src/views/system/FileCenterView.vue`、`src/features/system/fileApi.ts` |
| 只读报表 | `EnterpriseReportController`、`EnterpriseReportService` | `src/views/report/EnterpriseReportView.vue`、`src/features/report/api.ts` |
| 任务和日志 | `ScheduleJobController`、`ScheduleJobLogController` | `src/views/schedule/JobView.vue`、`src/features/schedule/api.ts` |

不要复制整页后再保留无关按钮、空接口或模板品牌。复制后必须收窄到当前业务。

## 后端落点

按 `module/<domain>` 分层，常见文件包括：

```text
easy-next-admin-server/src/main/java/com/laker/admin/module/<domain>/controller
easy-next-admin-server/src/main/java/com/laker/admin/module/<domain>/service
easy-next-admin-server/src/main/java/com/laker/admin/module/<domain>/entity
easy-next-admin-server/src/main/java/com/laker/admin/module/<domain>/mapper
easy-next-admin-server/src/main/java/com/laker/admin/module/<domain>/dto
```

接口规则：

- Controller 返回 `Response<T>` 或 `PageResponse<T>`。
- 分页响应写成 `PageResponse<RowView>`，不要写 `PageResponse<List<RowView>>`。
- 对外响应优先使用 DTO / View，不直接暴露持久化实体。
- 写操作和敏感查询必须加 `@EasyPermission`。
- 重要业务动作加 `@EasyAudit` 或显式审计采集器。
- 列表查询需要组织或本人边界时，在 Mapper 上声明 `@DataScope`，特殊内部查询才用 `EasyDataScopeContext.ignore(...)`。
- 分页排序字段可由前端传入时，优先用 `@PageQuery` 字段白名单。

权限码放在 `EasyPermissions`。命名保持 `<domain>:<resource>:<action>` 或现有同类风格，例如：

```java
public static final class Report {
    public static final String VIEW = "report:view";
}
```

## 数据库与初始化资源

新表结构走 Flyway。当前仓库同时维护 MySQL 初始化脚本和 H2 测试脚本：

```text
easy-next-admin-server/src/main/resources/db/migration
easy-next-admin-server/src/test/resources/db/migration-h2
```

新增页面资源必须写入 `sys_menu`，并保持 MySQL 和 H2 测试 seed 同步。页面资源最少包含：

| 字段 | 要求 |
| --- | --- |
| `type` | 页面为 `1`，目录为 `0`，按钮为 `2` |
| `href` | 页面路由，例如 `/system/users` |
| `permission_code` | 页面或按钮权限码 |
| `component_path` | 本地 Vue 页面路径，例如 `@/views/system/UserView.vue` |
| `pid` / `sort` | 控制目录层级和排序 |
| `visible` / `enable` | 控制侧边栏可见性和资源启停 |

按钮资源挂在页面节点下，使用 `type = 2`，一般不维护页面组件路径。按钮权限码要和后端 `@EasyPermission`、前端 `PermissionCodes` 同名。

角色初始化授权使用权限码筛选，不要写死菜单 ID 列表。现有测试 `PermissionSeedSqlTest` 会检查内置权限码、H2 seed 和页面组件路径。

## 前端落点

业务请求封装在 `src/features/<domain>`，页面放在 `src/views/<domain>`：

```text
easy-next-admin-web/src/features/<domain>/api.ts
easy-next-admin-web/src/features/<domain>/types.ts
easy-next-admin-web/src/views/<domain>/<PageName>View.vue
```

前端规则：

- 页面只调用 feature API，不直接 import `src/api/request.ts` 或 Axios。
- 页面需要 loading、empty 和基础错误处理。
- 受限按钮使用 `v-permission`，权限码来自 `src/permissions/codes.ts`。
- 新页面的 `component_path` 必须能被 `src/router/dynamicRoutes.ts` 通过 Vite `import.meta.glob('/src/views/**/*.vue')` 找到。
- 大页面优先抽子组件或 feature helper，不继续把所有表单、弹窗、格式化和领域逻辑塞进一个 SFC。

权限常量示例：

```ts
export const PermissionCodes = {
  report: {
    view: 'report:view'
  }
} as const
```

## 文档同步

用户能看到的新能力，需要更新至少一个入口文档：

- `README.md`：只写适合开源首页展示的核心能力。
- `docs/features-and-components.md`：更新功能总览、页面入口、权限和示例索引。
- `docs/architecture.md`：只有当请求链路、权限、数据范围、审计、工作流、调度、监控等机制变化时更新。
- `docs/deployment.md`：只有当 profile、Docker、Nginx、端口、构建产物或生产配置变化时更新。

不要把规划项写成已实现功能。未实现内容放 issue、roadmap 或设计稿。

## 验证矩阵

按影响范围选择最窄但足够证明结果的命令：

| 影响范围 | 必跑命令 |
| --- | --- |
| 后端 Java / SQL / 接口契约 | `mvn -pl easy-next-admin-server -am verify`；时间紧时至少 `mvn -pl easy-next-admin-server -am -DskipTests package` |
| 前端页面 / feature API / 权限常量 | `cd easy-next-admin-web && npm run build` |
| 前端逻辑测试相关 | `cd easy-next-admin-web && npm run test:unit` |
| skill / agent 入口 | `python3 /Users/lonli2/.codex/skills/.system/skill-creator/scripts/quick_validate.py .codex/skills/easy-next-admin-vibecoding` |
| 只改文档 | `git diff --check` |

建议优先补窄范围测试：

- 后端权限、菜单、初始化资源：补或更新 `PermissionSeedSqlTest`、Controller contract test。
- 数据权限：补 Mapper / resolver / service 测试。
- 前端纯逻辑：放到 `src/features/<domain>/*.test.ts`。
- 路由或菜单解析：更新 `dynamicRoutes.test.ts` 或相关布局测试。

## 完成前检查

- 后端接口是否全部使用标准响应结构。
- 写操作和敏感查询是否有 `@EasyPermission`。
- 权限码是否同时存在于 `EasyPermissions`、`PermissionCodes`、MySQL `sys_menu`、H2 seed。
- 页面 `component_path` 是否指向真实 `src/views/**/*.vue`。
- 页面是否只调用 feature API。
- 按钮是否使用 `v-permission`。
- 是否同步文档，且没有把计划写成已完成。
- 是否运行并记录验证命令。
