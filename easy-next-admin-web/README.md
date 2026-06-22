# EasyNextAdmin Web

EasyNextAdmin Web 是 EasyNextAdmin 的 Vue 3 前端基座，面向中文企业后台系统。页面只展示真实业务能力，不暴露抽象扩展中心或模板演示概念。

## 技术栈

- Vue 3
- TypeScript
- Vite
- Pinia
- Vue Router
- Element Plus
- Axios
- ECharts
- LogicFlow

## 本地开发

先启动后端，再启动前端：

```bash
npm ci
npm run dev
```

默认地址：

```text
http://127.0.0.1:5174
```

Vite 会把 `/api/**` 代理到 `http://localhost:8080`。如需修改：

```bash
VITE_API_PROXY_TARGET=http://127.0.0.1:8081 npm run dev
```

也可以创建本地环境变量文件：

```bash
touch .env.local
```

常用变量：

| 变量 | 说明 |
| --- | --- |
| `VITE_API_BASE_URL` | 浏览器请求后端接口时使用的基础路径，默认 `/api` |
| `VITE_API_PROXY_TARGET` | `npm run dev` 时 `/api` 和 `/storage` 代理到的后端地址 |

`easy-next-admin-web/.env.local` 只给前端 Vite 使用；根目录 `.env` 只给 Docker Compose 使用。

## 构建

```bash
npm run build
```

产物目录：

```text
dist
```

## 目录约定

```text
src/api           基础接口封装
src/components    通用组件
src/directives    业务指令，例如按钮权限
src/features      业务 API、类型和领域逻辑
src/layout        后台整体布局
src/router        路由和路由守卫
src/stores        Pinia 状态
src/styles        全局样式
src/views         页面
```

## 菜单与权限

菜单、页面路由和页面权限以服务端 `sys_menu` 为唯一来源。前端登录后读取后端返回的菜单树，根据 `component_path` 动态挂载 `src/views` 下的页面组件。

新增页面时需要同步维护后端 `sys_menu` 数据、页面组件、功能 API 和按钮权限码。按钮权限使用 `v-permission`，后端接口仍必须用 `@EasyPermission` 兜底。

## 设计原则

- 默认中文后台体验。
- 不依赖 CDN、在线字体或在线图标。
- 页面优先服务企业 CRUD、权限管理、监控审计、流程审批和二次开发。
- 页面只调用 `src/features` 中的 API 函数，不直接写 Axios 请求。
- 新功能需要同步补充后端菜单资源、组件路径、权限码和文档。

完整文档见仓库根目录 [docs](../docs/README.md)。
