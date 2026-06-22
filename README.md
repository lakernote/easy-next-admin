# EasyNextAdmin

[![CI](https://github.com/lakernote/easy-next-admin/actions/workflows/ci.yml/badge.svg)](https://github.com/lakernote/easy-next-admin/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://adoptium.net/)
[![Vue](https://img.shields.io/badge/Vue-3-42b883.svg)](https://vuejs.org/)

EasyNextAdmin 是一个面向中文企业后台二次开发的开源脚手架，采用 Spring Boot 3 + Vue 3 前后端分离架构。项目默认提供权限管理、组织与用户管理、用户导入导出、企业报表、运行监控、行为审计、实时日志、动态定时任务、轻量工作流、消息中心和文件中心，目标是让团队拿到代码后可以直接进入业务开发。

项目不定位为低代码平台、BI 平台或完整 BPM 平台。EasyNextAdmin 参考成熟开源项目的后台信息架构、权限模型和组件交互方式，但保留自己的产品边界和实现，不复制第三方模板品牌、演示页或无关功能。

## 项目状态

当前版本：`0.1.0-alpha.0`。这是首个公开 alpha 版本，核心功能已具备本地启动、测试和构建验证；生产上线前仍应替换数据库、Redis、域名、HTTPS、默认账号密码和会话安全方案。

| 适合 | 不适合 |
| --- | --- |
| 中文企业后台二次开发、权限/组织/审计/流程类内网系统、需要前后端分离脚手架的团队 | 低代码平台、BI 平台、完整 BPM 引擎、只展示 UI 模板的项目 |

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.5、MyBatis-Plus、Flyway、Spring Actuator、springdoc-openapi、Redisson、Caffeine |
| 前端 | Vue 3、TypeScript、Vite、Pinia、Vue Router、Axios、Element Plus、ECharts、LogicFlow |
| 本地依赖 | MySQL 8.4 LTS、Redis 7.4 |
| 部署 | 后端可打 JAR 或 Docker 镜像，前端输出静态资源并由 Nginx 托管 |

## 快速启动

环境要求：

- JDK 17+
- Maven 3.9+
- Node.js 22 LTS 或 24 LTS，以及随 Node.js 安装的 npm
- Docker 和 Docker Compose

启动 MySQL、Redis：

```bash
docker compose up -d mysql redis
```

如需重建本地库并导入初始化数据：

```bash
docker compose up -d mysql
docker exec easy-next-admin-mysql mysql -uroot -p123456 -e "DROP DATABASE IF EXISTS \`easy-next-admin\`; CREATE DATABASE \`easy-next-admin\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
docker exec -i easy-next-admin-mysql mysql --default-character-set=utf8mb4 -uroot -p123456 easy-next-admin < easy-next-admin-server/src/main/resources/db/migration/V1__init.sql
```

启动后端：

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

访问地址：

- 前端：http://127.0.0.1:5174
- 后端：http://127.0.0.1:8080
- OpenAPI：http://127.0.0.1:8080/swagger-ui.html

默认演示账号。登录页只在 `local` / `demo` profile 通过 `/api/auth/demo-accounts` 返回这些账号；生产 profile 不返回演示密码，正式环境必须替换初始化密码：

| 角色 | 账号 | 密码 | 用途 |
| --- | --- | --- | --- |
| 超级管理员 | `admin` | `admin` | 查看和维护全部内置能力 |
| 部门负责人 | `manager` | `easynext` | 组织、流程和部门数据 |
| 普通员工 | `staff` | `easynext` | 工作台和个人流程入口 |
| 审计人员 | `auditor` | `easynext` | 审计记录和财务复核类待办 |

更完整的本地开发说明见 [本地开发](docs/getting-started.md)。

## 内置能力

| 能力 | 页面入口 | 说明 |
| --- | --- | --- |
| 工作台 | `/dashboard` | 聚合个人待办、常用申请、系统能力和关键统计 |
| 系统管理 | `/system/users`、`/system/roles`、`/system/menus`、`/system/departments`、`/system/files` | 用户、直属上级、用户导入导出、角色、菜单权限、部门负责人、组织架构和文件中心 |
| 报表中心 | `/reports/enterprise` | 组织人员台账和采购流程复核的 A4 纸质报表预览与打印 |
| 运行监控 | `/monitor/server`、`/monitor/online`、`/monitor/cache`、`/monitor/cache-list`、`/monitor/weblog` | 应用运行状态、在线用户、缓存指标、缓存键值和在线请求日志 |
| 审计中心 | `/audit/behavior` | 登录、操作、数据变更、错误和接口访问审计 |
| 任务调度 | `/schedule/jobs` | 动态任务定义、启停和执行日志 |
| 流程中心 | `/workflow/start`、`/workflow/tasks`、`/workflow/instances`、`/workflow/console` | 统一发起请假、采购、报修流程，处理我的流程，按直属上级、部门负责人、职能角色等规则派单，管理员监控流程实例和维护流程配置 |
| 消息中心 | `/messages` | 个人消息、流程通知、审计提醒和任务消息 |
| 个人中心 | `/profile/security` | 个人资料、改密、登录历史和会话管理 |

功能说明、组件用法和实现原理见 [功能与组件](docs/features-and-components.md)。

## 工程结构

```text
easy-next-admin
├── easy-next-admin-server   # Spring Boot 3 服务端
│   └── Dockerfile           # 服务端镜像构建
├── easy-next-admin-web      # Vue 3 + Vite + Element Plus 前端
│   ├── Dockerfile           # 前端镜像构建
│   └── nginx.conf           # 前端容器 Nginx 配置
├── docs                # 开源项目文档
├── docker-compose.yml  # 本地 MySQL、Redis 依赖
└── pom.xml             # Maven 聚合工程
```

菜单、页面路由、角色授权资源和页面权限码以服务端 `sys_menu` 为唯一事实源。前端只通过 `/api/auth/me` 接收当前账号可见菜单，并在 `easy-next-admin-web/src/router/dynamicRoutes.ts` 中把 `component_path` 解析到本地 Vue 页面；按钮权限使用 `v-permission`，后端接口继续由 `@EasyPermission` 兜底。

## 编译打包

后端：

```bash
mvn -pl easy-next-admin-server -am -DskipTests package
```

产物：

```text
easy-next-admin-server/target/easyNextAdmin.jar
```

前端：

```bash
cd easy-next-admin-web
npm ci
npm run build
```

产物：

```text
easy-next-admin-web/dist
```

部署方式、Nginx 反向代理、Docker 构建和生产配置覆盖见 [编译与部署](docs/deployment.md)。

## 发布前验证

发布或提交 PR 前建议至少运行：

```bash
mvn -pl easy-next-admin-server -am test
cd easy-next-admin-web
npm ci
npm run test:unit
npm run build
```

本仓库已配置 GitHub Actions，在 `main` 分支 push 和 pull request 时会执行后端测试、前端单元测试和前端构建。

## 文档

- [文档目录](docs/README.md)
- [本地开发](docs/getting-started.md)
- [编译与部署](docs/deployment.md)
- [功能与组件](docs/features-and-components.md)
- [架构与实现原理](docs/architecture.md)
- [参考项目与借鉴边界](docs/reference-projects.md)

## 开源协作

- [贡献指南](CONTRIBUTING.md)
- [安全策略](SECURITY.md)
- [更新日志](CHANGELOG.md)

默认启动不需要额外 `.env` 文件。Docker Compose 依赖端口和前端开发代理都带默认值，确需覆盖时可用命令行环境变量或本机不提交的 `.env` / `easy-next-admin-web/.env.local`。`.editorconfig` 用于统一 IDE/编辑器格式；默认账号和默认密码只用于本地开发，生产环境必须覆盖。

## 参考项目

EasyNextAdmin 主要参考 RuoYi/RuoYi-Vue 的中文企业后台习惯，参考 Vben Admin 的前端权限与路由组织思路，参考 Flowable/Camunda 在流程领域的产品边界，同时直接使用 Element Plus、ECharts、LogicFlow 等开源组件。详细说明见 [参考项目与借鉴边界](docs/reference-projects.md)。

## 许可证

本项目使用 [Apache License 2.0](LICENSE)。
