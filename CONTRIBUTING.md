# 贡献指南

感谢你关注 EasyNextAdmin。这个项目面向中文企业后台二次开发，贡献应优先围绕真实后台能力、清晰权限边界和稳定本地部署展开。

## 开发准备

```bash
docker compose up -d
cd easy-next-admin-server
mvn spring-boot:run
```

```bash
cd easy-next-admin-web
npm ci
npm run dev
```

## 提交要求

- 后端使用 Java 17、Spring Boot 3、MyBatis-Plus 和 Flyway。
- 前端使用 Vue 3、TypeScript、Vite、Pinia、Vue Router、Axios 和 Element Plus。
- 新页面要同时补齐后端菜单资源、权限码、前端 API wrapper 和基础错误处理。
- 不把规划项写成已完成功能，不引入低代码、BI 或完整 BPM 平台概念。
- 文档只描述当前仓库真实存在、可运行、可验证的能力。

## 验证命令

修改后端、SQL 或接口契约时，优先运行：

```bash
mvn -pl easy-next-admin-server -am verify
```

修改前端时，至少运行：

```bash
cd easy-next-admin-web
npm run test:unit
npm run build
```

## Pull Request

提交 PR 时请说明：

- 变更目的和影响范围。
- 涉及的后端接口、前端页面、菜单权限或数据库迁移。
- 已运行的验证命令和结果。

安全问题不要直接提交公开 Issue 或 PR，请先阅读 [安全策略](SECURITY.md)。
