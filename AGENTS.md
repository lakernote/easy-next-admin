# EasyNextAdmin Agent Guide

本文件是仓库级 Agent 入口，适用于整个 `easy-next-admin` 项目。Codex / Claude / 其他 coding agent 进入本仓库时，先读这里，再按任务读取 `README.md` 和 `docs/`。

## 项目定位

EasyNextAdmin 是面向中文企业后台二次开发的开源脚手架，采用 Spring Boot 3 + Vue 3 前后端分离架构。默认能力应围绕企业后台真实工作流展开：权限管理、组织与用户、用户导入导出、运行监控、行为审计、在线 WebLog、动态定时任务、轻量工作流、消息中心和文件中心。

不要把 EasyNextAdmin 做成低代码平台、BI 平台、完整 BPM 平台或模板展示站。产品 UI 和公开 API 不暴露抽象“扩展中心”概念，只展示真实用户能力。

## 主要目录

```text
easy-next-admin-server   Spring Boot 3 服务端
easy-next-admin-web      Vue 3 + Vite + Element Plus 前端
docs                开源项目文档
docker-compose.yml  本地 MySQL、Redis 依赖
Dockerfile          服务端镜像构建
```

当前仓库不使用根目录 `scripts/`。不要重新创建 `scripts/` 辅助脚本，除非用户明确要求。

## 本地命令

启动本地依赖：

```bash
docker compose up -d
```

启动服务端：

```bash
cd easy-next-admin-server
mvn spring-boot:run
```

启动前端：

```bash
cd easy-next-admin-web
npm ci
npm run dev
```

后端构建：

```bash
mvn -pl easy-next-admin-server -am -DskipTests package
```

前端构建：

```bash
cd easy-next-admin-web
npm run build
```

涉及前端文件时，完成前必须运行 `npm run build`。涉及后端 Java、SQL 或接口契约时，优先运行 Maven 测试或至少运行后端打包命令。

## 后端规则

- 使用 Java 17、Spring Boot 3、MyBatis-Plus、Flyway。
- 控制器返回 `Response<T>` 或 `PageResponse<T>`，不要临时发明响应结构。
- 真实写操作和敏感查询必须使用 `@EasyPermission` 保护。
- 关键业务动作优先接入 `@EasyAudit` 或审计采集器。
- 数据权限相关查询要尊重当前用户、角色和部门范围，不要绕过数据范围上下文，除非是认证、初始化或明确的管理入口。
- 新表结构通过 Flyway 迁移维护，保持 MySQL 初始化数据和本地开发说明一致。
- 新功能按 `module/<domain>` 分层，保持 controller、service、entity、mapper 边界清楚。

## 前端规则

- 使用 Vue 3、TypeScript、Vite、Pinia、Vue Router、Axios、Element Plus。
- 默认中文优先，适合国内企业后台：高效 CRUD、密集信息、清晰权限、内网稳定部署。
- 运行时资产保持本地，不加 CDN、在线图标或在线字体。
- 页面使用可读 Vue SFC，避免为了抽象而抽象。
- 页面不直接写 Axios 请求，接口封装放到 `easy-next-admin-web/src/features/<domain>/api.ts`。
- 新页面必须在服务端 `sys_menu` 登记目录/页面/按钮资源，页面资源写 `component_path`，前端通过动态路由解析到本地 `src/views` 页面。
- 页面权限来自后端菜单资源，按钮权限走 `v-permission`，后端接口权限用 `@EasyPermission` 兜底。
- 新系统页应同时包含 API wrapper、`sys_menu` 资源、permission strings 和基础错误处理。

## 文档规则

- 开源入口文档只保留真实存在、可启动、可验证的能力。
- 新功能需要同步更新 `README.md` 或 `docs/features-and-components.md`。
- 启动、部署、架构和参考项目说明分别维护在 `docs/getting-started.md`、`docs/deployment.md`、`docs/architecture.md`、`docs/reference-projects.md`。
- 不把规划项写成已完成功能。未实现内容放 issue、roadmap 或单独设计稿。
- 不恢复历史迁移文档、内部执行计划、模板品牌页或无关中间件清单。

## Vibe Coding 工作流

1. 先用 `rg` / `rg --files` 查当前实现，不凭记忆改代码。
2. 找到相关后端接口、前端 API、页面、`sys_menu` 资源和文档，保持契约一致。
3. 小步修改，优先复用现有组件、样式、权限码和响应结构。
4. 不复制整套后台模板，不引入无关依赖。
5. 修改完成后运行能证明结果的命令，并在回复里说明验证结果。

## Codex Skill

仓库内置一份项目技能：

```text
.codex/skills/easy-next-admin-vibecoding/SKILL.md
```

需要让 Codex 自动发现时，可把该目录复制到当前机器的 `$CODEX_HOME/skills` 或 `~/.codex/skills`。技能内容应与本文件保持一致。
