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

先启动后端，再在 `easy-next-admin-web` 目录启动前端：

```bash
cd easy-next-admin-web
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
src/assets        需要被 Vue/TS import 的本地资源
src/components    通用组件
src/directives    业务指令，例如按钮权限
src/features      业务 API、类型和领域逻辑
src/layout        后台整体布局
src/router        路由和路由守卫
src/stores        Pinia 状态
src/styles        全局样式
src/views         页面
public            按固定 URL 暴露的静态资源，例如 favicon
```

`src/assets` 和 `public` 的区别：

- `src/assets`：资源被代码 `import`，会进入 Vite 打包流程并生成哈希文件名。
- `public`：资源不经过打包，构建时原样复制到站点根路径，适合 favicon、robots.txt 等固定 URL 资源。

## 工程文件说明

| 文件 | 作用 | 维护建议 |
| --- | --- | --- |
| `src/App.vue` | Vue 根组件，只保留路由出口。 | 后台布局放到 `src/layout`，不要在根组件堆业务逻辑。 |
| `src/main.ts` | 前端启动入口，注册 Pinia、Element Plus、路由和全局指令。 | 全局能力集中在这里注册，页面能力放到 `features` 和 `views`。 |
| `.dockerignore` | 控制 Docker 构建上下文。 | 不把依赖、本地环境变量和临时日志发送进镜像构建。 |
| `.gitignore` | 控制 Git 忽略文件。 | 构建产物、依赖目录和本机配置不提交。 |
| `Dockerfile` | 用 Nginx 托管 `dist` 静态资源。 | 构建镜像前先运行 `npm run build`。 |
| `index.html` | Vite HTML 入口，声明 favicon 和 Vue 挂载点。 | 只放浏览器入口元信息，不写业务页面结构。 |
| `nginx.conf` | 前端容器的 Nginx 配置。 | 负责 history 路由回退、静态缓存、安全响应头和后端反向代理。 |
| `package.json` | npm 脚本、依赖和 Node/npm 版本约束。 | JSON 不支持注释，说明写在 README。 |
| `package-lock.json` | 锁定依赖版本，保证安装结果可复现。 | 由 npm 维护，不手写修改，不加注释。 |
| `tsconfig.json` | 浏览器端 TypeScript 配置。 | 保持 `strict`，路径别名和 Vite 配置一致。 |
| `tsconfig.node.json` | Node 侧 TypeScript 配置，目前用于 `vite.config.ts`。 | 只放构建工具配置需要的 TypeScript 规则。 |
| `vite.config.ts` | Vite 插件、别名、开发代理和构建分包配置。 | 新增代理或分包时优先写清业务原因。 |

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
