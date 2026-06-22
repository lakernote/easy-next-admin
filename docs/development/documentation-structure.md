# 文档结构整理设计稿

本文是 EasyNextAdmin 企业级文档整理的设计稿。它只规划文档目录、写作模板和推进顺序，不把尚未整理完成的组件写成已完成文档。

## 目标

让二开团队能按两条线阅读项目：

- 业务模块：用户能看到什么，入口在哪里，权限和数据边界是什么。
- 技术组件：开发者如何接入，内部原理是什么，为什么采用当前方案，其他方案有什么 tradeoff。

整理过程中允许发现并记录更优雅的代码方案，但默认先以文档暴露问题，不把无关重构混入单篇组件文档。

## 非目标

- 不把 EasyNextAdmin 扩展成低代码、BI 或完整 BPM 平台。
- 不新增模板品牌页、演示组件页或无业务落点的中间件清单。
- 不把规划项写进 `README.md` 当成已完成功能。
- 不为每个 Java 类生成 API 文档，避免把源码注释搬运成文档。

## 目录规划

```text
docs/
├── README.md
├── getting-started.md
├── deployment.md
├── architecture.md
├── features-and-components.md
├── reference-projects.md
├── modules/
│   ├── README.md
│   ├── system.md
│   ├── workflow.md
│   ├── monitor.md
│   ├── audit.md
│   ├── schedule.md
│   ├── message.md
│   └── report.md
├── components/
│   ├── README.md
│   ├── _template.md
│   ├── security/
│   │   ├── auth-session.md
│   │   ├── permission.md
│   │   ├── data-scope.md
│   │   ├── masking.md
│   │   ├── rate-limit.md
│   │   └── waf-cors-headers.md
│   ├── persistence/
│   │   ├── response-contract.md
│   │   ├── page-query.md
│   │   ├── mybatis-trace.md
│   │   └── flyway.md
│   ├── runtime/
│   │   ├── cache.md
│   │   ├── scheduler.md
│   │   ├── local-message.md
│   │   ├── idempotency.md
│   │   ├── duplicate-request.md
│   │   └── distributed-lock.md
│   ├── observability/
│   │   ├── audit-collector.md
│   │   ├── trace-id.md
│   │   ├── trace-tree.md
│   │   ├── metrics.md
│   │   └── weblog.md
│   └── frontend/
│       ├── dynamic-routes.md
│       ├── permission-directive.md
│       ├── feature-api.md
│       ├── table-toolbar.md
│       └── easy-chart.md
└── development/
    ├── documentation-structure.md
    ├── adding-system-page.md
    ├── adding-backend-module.md
    ├── adding-permission-resource.md
    └── release-checklist.md
```

`modules/` 写业务能力，`components/` 写技术机制，`development/` 写二开流程和设计稿。`features-and-components.md` 保留总览和索引，不继续承载所有细节。

## 组件文档模板

每篇组件文档固定使用以下结构：

```text
1. 适用场景
2. 如何使用
3. 请求或执行流程
4. 原理
5. 关键类、配置和表
6. Tradeoff：当前方案与备选方案的优劣
7. 常见坑
8. 扩展建议
```

写作规则：

- 先从真实后台场景切入，再解释组件名词。
- 先写调用者如何使用，再写内部原理。
- Tradeoff 控制在 2-3 个方案内，说明优点、缺点和适用边界。
- 如果发现代码设计问题，写入“扩展建议”或单独 issue，不在文档中假装已经优化。

## 优先整理顺序

第一批先整理和企业后台安全边界强相关的组件：

1. `components/security/data-scope.md`
2. `components/security/permission.md`
3. `components/security/auth-session.md`
4. `components/security/masking.md`

第二批整理稳定性和运行治理组件：

1. `components/runtime/cache.md`
2. `components/runtime/scheduler.md`
3. `components/runtime/local-message.md`
4. `components/observability/audit-collector.md`
5. `components/observability/trace-tree.md`

第三批整理前端二开入口：

1. `components/frontend/dynamic-routes.md`
2. `components/frontend/permission-directive.md`
3. `components/frontend/feature-api.md`
4. `development/adding-system-page.md`

业务模块文档在组件样板稳定后推进，避免先写业务页时反复调整组件文档口径。

## 代码整理原则

整理文档时如果发现更好的代码方案，按下面规则处理：

- 只影响命名、注释、测试补充的小改动，可以随对应组件文档一起做。
- 影响运行链路、权限边界、SQL 生成、响应契约或前后端协议的改动，必须单独开任务。
- 每个组件整理完成后至少运行窄范围测试；涉及前端文件时运行 `npm run build`。
- 不为了文档美观改接口，不为了统一目录移动稳定业务代码。

## 数据权限组件样板范围

首篇样板文档为 `docs/components/security/data-scope.md`，覆盖以下真实实现：

- `@DataScope` 标记 Mapper 查询。
- `CurrentUserDataScopeResolver` 根据当前账号、角色数据范围和部门树计算范围。
- `DataScopeMetadataRepository` 读取部门树和自定义部门授权元数据，`CachedDataScopeMetadataRepository` 通过 `@Cacheable` 命名缓存降低查询频率。
- `EasyDataScopeInnerInterceptor` 在 MyBatis 查询前改写 SQL。
- `DataScopeSqlRewriter` 包装原 SQL 并追加条件。
- `EasyDataScopeContext.ignore(...)` 显式绕过系统内部查询。
- `EasyDataScopeInnerInterceptorTest`、`CurrentUserDataScopeResolverTest`、`DataScopeSqlRewriterTest` 作为验证入口。
