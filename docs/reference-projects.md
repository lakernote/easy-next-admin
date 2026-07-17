# 参考项目与借鉴边界

EasyNextAdmin 参考成熟开源项目的后台习惯、组件能力和领域模型，但不复制完整模板、不保留第三方品牌、不把不属于企业脚手架的能力塞进默认 UI。

## 参考清单

| 项目或组件 | 参考内容 | EasyNextAdmin 的做法 | 不做什么 |
| --- | --- | --- | --- |
| [RuoYi / RuoYi-Vue](https://doc.ruoyi.vip/ruoyi-vue/) | 中文企业后台的信息架构、用户/角色/菜单/按钮权限、数据权限、日志、监控、定时任务、代码组织习惯 | 系统管理、权限授权、监控、日志、任务调度等基础后台能力优先贴近国内企业用户习惯 | 不复制 RuoYi 模板页、品牌、代码生成主线、字典参数等低频默认模块 |
| [Vben Admin](https://doc.vben.pro/en/guide/in-depth/access.html) | Vue 管理后台的路由权限、菜单权限、按钮权限和工程化组织 | 后端 `sys_menu` 统一维护菜单、页面和按钮资源，前端按授权菜单动态装配路由，按钮权限走指令 | 不引入 Vben 的完整框架、主题系统和应用结构，也不保留前端硬编码菜单清单 |
| [Element Plus](https://element-plus.org/en-US/component/overview.html) | Vue 3 企业后台基础组件，包括表格、表单、弹窗、抽屉、菜单、分页和图标 | 作为默认 UI 组件库，保持本地依赖和中文后台交互习惯 | 不依赖 CDN、在线字体或在线图标 |
| [Apache ECharts](https://echarts.apache.org/en/index.html) | 图表类型、交互、响应式渲染和仪表盘展示 | 封装 `EasyChart`，用于工作台和监控页面 | 不做完整 BI 数据建模平台 |
| [LogicFlow](https://07.logic-flow.cn/guide/basic/logic-flow.html) | 流程图画布、节点、连线、交互和扩展机制 | 用于流程配置和流程实例图展示，流程运行仍由 EasyNextAdmin 后端轻量引擎处理 | 不把 LogicFlow 当作完整流程引擎 |
| [Flowable](https://www.flowable.com/open-source/docs/bpmn/ch07b-BPMN-Constructs) / [Camunda](https://docs.camunda.io/docs/components/concepts/processes/) | BPMN、流程定义、流程实例、任务、历史和可视化运维概念 | 借鉴流程领域术语，内置轻量审批引擎支持发起、待办、审批、转办、委派、加签、退回、撤回和抄送 | 不引入完整 BPMN 运行时、DMN、CMMN、复杂编排和独立流程平台 |
| [Spring Boot Actuator](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html) | 健康检查和应用信息端点 | 保留健康检查与服务信息入口，服务监控页面不展示 Actuator 明细清单 | 不默认引入 Spring Boot Admin 服务端 |
| [springdoc-openapi](https://springdoc.org/) | OpenAPI JSON 和 Swagger UI | `local` 开发环境暴露 `/v3/api-docs` 和 `/swagger-ui.html`，方便调试接口 | 通用配置默认关闭文档入口，生产不加载 `local` profile |
| [Redisson](https://redisson.pro/docs/integration-with-spring/) | Redis 客户端、锁、Spring 集成 | `easy.features.redis=true` 后统一承载缓存、会话、验证码、重复请求、幂等、限流和分布式锁 | 本地可用 `easy.features.redis=false` 回退到内存或 MySQL |
| [Spring Kafka](https://spring.io/projects/spring-kafka) | Kafka Producer、Consumer、Topic Admin 和监听容器 | `easy.features.kafka=true` 后才启用 Kafka 基础设施，默认不误连本机 Kafka | 不把 Kafka 当作所有业务消息的隐式默认通道 |

## 产品边界

EasyNextAdmin 的默认 UI 只展示真实企业后台能力：

- 权限管理
- 用户、角色、菜单、组织和文件
- 应用监控、在线用户、缓存、实时日志
- 行为审计
- 动态定时任务
- 轻量工作流
- 消息中心
- 用户导入导出
- 个人中心

以下内容不作为默认主线：

- 低代码页面搭建
- 完整 BI 平台
- 完整 BPMN 流程平台
- 与当前代码无关的中间件展示页
- 模板自带品牌页和演示页

## 为什么这样取舍

中文企业后台的核心诉求是稳定、可维护和二开效率。RuoYi 证明了用户、角色、部门、菜单、日志、监控和任务调度这类基础后台能力的长期价值；Vben Admin 证明了前端工程化和权限路由的组织价值；ECharts、LogicFlow、Element Plus 解决了图表、流程图和后台组件的成熟度问题。

EasyNextAdmin 把这些经验收敛到“Spring Boot 3 + Vue 3 企业脚手架”里：基础后台要完整，增强能力要真实可用，默认产品不能膨胀成低代码、BI 或 BPM 平台。
