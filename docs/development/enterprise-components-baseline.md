# 企业级组件能力矩阵

本文整理企业级开发常见组件、分布式组件，以及 EasyNextAdmin 当前已具备和仍缺少的能力。它不是当前功能宣传页，而是用于后续架构扫描、路线规划和二开取舍的基线。

## 判断原则

EasyNextAdmin 是企业后台脚手架，不是中间件平台。组件取舍遵循：

- 应用内强相关能力优先内置，例如权限、审计、数据范围、统一响应、幂等、限流、观测埋点。
- 可替换基础设施优先抽象接口和配置开关，例如 Redis、Kafka、OSS、指标后端。
- 大型分布式平台能力不直接内置，例如 Kubernetes、Service Mesh、APM 平台、统一日志平台、配置中心集群。
- 国内中小企业能落地优先，避免为了“看起来企业级”堆中间件。

状态说明：

| 状态 | 含义 |
| --- | --- |
| 已具备 | 代码中已有可运行实现，文档可描述为当前能力 |
| 部分具备 | 有基础实现，但生产完整性、治理或平台化不足 |
| 可选具备 | 依赖和开关存在，只有启用外部服务后生效 |
| 缺口 | 当前没有实现，后续可按优先级补齐 |
| 不建议内置 | 应由外部平台或部署环境提供，项目只保持接入边界 |

## 文档结构与边界

本文按四层梳理，避免把应用能力、分布式中间件和交付治理混在一起：

| 层级 | 放什么 | 不放什么 |
| --- | --- | --- |
| 应用组件 | 写在 EasyNextAdmin 代码或二开业务代码里的能力，例如权限、审计、幂等、限流、业务编号、批处理、业务事件 | MySQL 高可用、K8s、日志平台这类外部平台 |
| 分布式组件 | 支撑多实例、多服务、跨进程协作的中间件或运行时能力，例如 Redis、Kafka、对象存储、配置中心、注册发现、网关、分布式 ID | 业务编号、审批流程、消息模板这类应用语义 |
| 平台支撑组件 | 交付、观测、安全、备份和运维平台，例如制品库、镜像仓库、集中日志、APM、告警、Secret、DNS、证书 | 业务代码里的组件封装 |
| 交付与研发治理 | 发布包形态、部署方式、CI/CD、测试、安全扫描、SLO、复盘、容量治理 | 单个技术组件的 API 设计 |

业务编号服务的归类要特别明确：它属于应用组件，因为编号格式和业务规则强相关；底层可以使用 MySQL 行锁、号段、Redis 原子递增或分布式 ID 来保证并发安全，但不应把 Snowflake 或数据库主键直接暴露给用户作为业务编号。

## 企业级缺口全景

从“能开发业务”升级到“能长期生产运行”，缺口不只在代码组件，还包括交付、治理、安全、数据和运维流程。

| 大类 | 企业级通常需要 | EasyNextAdmin 当前状态 | 主要缺口 | 建议优先级 |
| --- | --- | --- | --- | --- |
| 应用基础能力 | 统一响应、异常、校验、权限、数据权限、审计、文件、消息、任务、工作流 | 大部分已具备 | 组件使用规范还不够体系化，例如限流、幂等、锁、任务、Outbox 的接入文档和反例 | P1 |
| 业务支撑能力 | 业务编号、流程单号、导入导出规范、通知模板、业务事件、批处理 | 部分具备 | 业务编号生成器和轻量批处理治理已具备基础版；仍缺消息模板、业务事件模型和批处理 worker 示例 | P1 |
| 分布式基础能力 | 分布式 ID、缓存、锁、限流、幂等、消息队列、远程调用、熔断、最终一致性 | 部分具备 | 主键分布式 ID 够用；Outbox 缺退避和死信；熔断/重试策略偏基础 | P0/P1 |
| 数据库治理 | 迁移、索引、慢 SQL、容量、备份恢复、归档、冷热数据 | 部分具备 | 缺审计/API/任务/Outbox 高增长治理、清理归档任务、大表变更 runbook | P0 |
| 缓存治理 | TTL、命名缓存、穿透/击穿/雪崩防护、热点 key、缓存一致性 | 部分具备 | 缺缓存模式文档、热点 key 治理、缓存失效一致性规范 | P1 |
| 可观测性 | metrics、logs、traces、events、profiles、前端 RUM、告警、面板 | 部分具备 | 缺结构化日志、OpenSearch/SLS/Loki 接入、前端事件上报、面板和告警规则 | P0/P1 |
| 稳定性治理 | SLO、错误预算、限流降级、熔断隔离、容量水位、故障复盘 | 部分具备 | 缺项目级 SLO 模板、burn rate 告警、复盘模板和演练制度 | P1 |
| 安全治理 | Cookie 会话、CSRF、密钥管理、漏洞扫描、审计合规、最小权限 | 部分具备 | 当前生产会话路线仍需 HttpOnly Cookie + CSRF；缺 Secret 管理、安全扫描和权限审批 | P0/P1 |
| 部署交付 | 传统部署包、单机容器包、K8s 清单、发布检查、回滚、灰度 | 部分具备 | 缺三套主交付包：裸机/VM、单机 Docker Compose、K8s；Docker Swarm 只做兼容说明 | P0 |
| CI/CD | 构建、测试、制品、环境审批、发布记录、自动回滚 | 部分具备 | 只有 CI，缺制品库、部署流水线、环境审批、发布事件和回滚自动化 | P1 |
| 配置治理 | 环境分层、配置中心、动态配置、灰度配置、配置审计 | 部分具备 | 当前靠配置文件和环境变量；缺配置中心接入规范、配置变更审计 | P2 |
| 日志和审计留存 | 本地日志、集中日志、索引、保留周期、归档、合规查询 | 部分具备 | 缺日志索引模板、审计归档策略、保留周期落地任务 | P0/P1 |
| 灾备恢复 | RTO/RPO、备份、恢复演练、多机房、对象存储版本 | 缺口较多 | 缺恢复 runbook、备份校验、演练计划和数据恢复验证 | P1 |
| 多租户和组织隔离 | 租户隔离、租户配置、租户数据边界、租户审计 | 不作为当前默认能力 | 当前是单企业/组织数据权限模型，不是 SaaS 多租户 | P2，只有做 SaaS 时才需要 |
| 国际化和本地化 | 多语言、时区、币种、区域格式 | 不作为当前默认能力 | 项目中文企业后台定位明确，不需要默认做 i18n | P3 |
| 成本治理 | 日志/指标成本、存储生命周期、资源配额、容量趋势 | 部分具备 | 缺观测成本预算、存储生命周期和资源用量面板 | P2 |

当前最核心的判断：

- 不是缺“权限、审计、缓存、任务、消息”这些应用基础组件，这些已经有基线。
- 真正缺的是生产化闭环：交付包、发布回滚、增长治理、结构化日志、告警面板、Outbox 死信、安全会话和灾备恢复。
- 分布式平台能力不要一次性全内置。没有 K8s 的企业先做好传统部署包和单机容器包；有 K8s 的企业再提供云原生部署包和平台接入模板。

## 企业级应用组件

这些组件通常属于业务应用自身，应尽量在脚手架里提供清晰基线。

| 组件域 | 企业级常见能力 | 当前状态 | EasyNextAdmin 现有落点 | 缺口和建议 |
| --- | --- | --- | --- | --- |
| 统一响应和错误码 | 统一响应结构、业务错误码、参数校验错误详情、全局异常处理 | 已具备 | `Response`、`PageResponse`、`ErrorCode`、`GlobalExceptionHandler` | 可继续补错误码分层文档和前端错误码处理规范 |
| API 契约文档 | OpenAPI、接口分组、调试入口、生产关闭策略 | 已具备 | `springdoc-openapi`、`OpenApiConfig`、`ApiDocsView` | 可补接口变更兼容策略和契约测试 |
| API 版本和兼容 | URL / Header 版本、废弃策略、兼容窗口、契约变更记录 | 缺口 | 当前接口仍按单版本演进 | P2：公开接口或多客户端接入后再补版本策略；内部后台优先保持兼容字段和契约测试 |
| 认证会话 | 登录、退出、会话恢复、在线会话、服务端撤销、会话超时 | 已具备 | `EasyAuthService`、`EasyAuthFilter`、`AuthSessionStore`、在线用户页 | 生产路线仍建议迁移 HttpOnly Cookie + CSRF，当前 Bearer token 仅适合开发调试 |
| 登录风控 | 验证码、登录失败次数、账号锁定、异常 IP/设备提醒 | 部分具备 | 登录验证码、登录限流、登录审计 | P1：账号锁定策略、异常登录提醒、可信设备和登录风险规则 |
| 账号生命周期 | 开户、离职停用、密码策略、重置密码、会话踢下线 | 部分具备 | 用户启停、重置密码、个人改密、在线用户会话撤销 | P1：补密码复杂度/有效期、离职自动停用、账号锁定和账号状态变更审计 |
| MFA / 二次确认 | 登录二次认证、高危操作再确认、OTP/短信/企业微信验证 | 缺口 | 文档中已建议高危动作可叠加二次确认，代码未实现 MFA | P2：公网后台、高权限后台或合规场景再接入，不作为默认登录复杂度 |
| 组织用户模型 | 用户、部门、角色、直属上级、部门负责人、岗位/职级 | 部分具备 | `module.system`、用户、角色、部门、直属上级、部门负责人 | 缺岗位、职级、职务、岗位授权和组织变更历史；非所有企业都需要默认内置 |
| 多租户隔离 | 租户、租户配置、租户数据边界、租户审计 | 不建议默认内置 | 当前是单企业组织权限和数据权限模型 | 只有做 SaaS 或集团多法人隔离时才作为 P2/P3 扩展 |
| 权限控制 | 页面权限、按钮权限、接口权限、超级管理员、权限版本 | 已具备 | `sys_menu`、`@EasyPermission`、`EasyPermissions`、`v-permission`、`PermissionVersionService` | 可补更细的权限变更审计和授权审批流程 |
| 数据权限 | 部门范围、本人范围、自定义部门、SQL 拦截、查询收口 | 已具备 | `@DataScope`、`EasyDataScopeInnerInterceptor`、`EasyDataScopeContext` | 可继续补跨模块数据权限接入检查清单 |
| 审计 | 登录、操作、异常、接口访问、敏感变更、审计可见性 | 已具备 | `@EasyAudit`、`AuditLogCollector`、`SensitiveAuditService`、`module.audit` | P2：保留周期、清理任务、冷归档、审计报表 |
| API 访问日志 | 入口请求、traceId、耗时、状态、请求/响应摘要 | 已具备 | `@EasyApiAccessLog`、`EasyApiAccessLogAspect`、`audit_api_log` | P2：高增长治理和默认查询时间范围 |
| 数据生命周期 | 热数据保留、冷归档、清理任务、恢复验证 | 缺口 | 当前主要在观测和稳定性文档中提出治理要求 | P0/P1：先覆盖审计、API 日志、任务日志、Outbox，再扩展到业务大表 |
| 数据分类分级 | 普通数据、敏感数据、核心数据、外发审批、访问审计 | 部分具备 | 脱敏、数据权限、敏感变更审计已有基础 | P1/P2：补字段分级清单、导出审批、敏感查询审计和外发规则 |
| 脱敏 | DTO 字段脱敏、日志/审计文本脱敏、敏感字段集中维护 | 已具备 | `@EasyMask`、`EasySensitiveDataMasker` | 可补字段分级、导出脱敏策略和安全测试样例 |
| 敏感数据加密 | 数据库存储加密、密钥轮换、按字段解密、最小可见范围 | 缺口 | 当前具备密码哈希和输出脱敏，不提供业务字段加密组件 | P2：涉及证件号、银行卡、合同密钥等高敏字段时再引入字段加密和 KMS |
| 参数校验 | Bean Validation、业务异常、分页参数白名单 | 已具备 | `spring-boot-starter-validation`、`PageRequestArgumentResolver`、`@PageQuery` | 可补文件上传、导入参数的统一错误详情规范 |
| JSON 编解码 | ObjectMapper 统一配置、异常收口 | 已具备 | `EasyJsonCodec`、`EasyJsonException` | 已满足当前脚手架需要 |
| 系统参数 | 应用内可维护参数、开关、阈值、业务配置 | 缺口 | 当前主要使用配置文件、环境变量和数据库业务表 | P1：只做应用内系统参数，不做配置中心；敏感参数仍走 Secret/环境变量 |
| 功能开关 | 能力开关、业务开关、灰度开关、开关审计 | 部分具备 | `easy.features.*` 支持基础设施能力开关 | P2：业务灰度和动态开关可在系统参数基础上扩展，不直接做复杂 feature flag 平台 |
| 数据字典 | 状态、枚举、业务选项、前端展示标签 | 缺口 | 当前多由枚举、常量或业务表承担 | P1：补轻量字典组件，注意不要把权限、菜单、流程配置塞进字典 |
| 业务编号 | 申请单号、工单号、采购单号、按日流水号、可读短码 | 基础具备 | `BusinessNumberService`、`biz_number_rule`、`biz_number_sequence`，请假/采购/报修已接入；后台有编号规则页 | 后续可补号段预分配、规则变更审批和编号占用/回收审计 |
| 文件中心 | 上传、下载、预览、鉴权、MIME/扩展名/文件头校验 | 已具备 | `SysFileController`、`EasyStorageFacade`、本地存储、Aliyun OSS 可选 | P1：病毒扫描、敏感内容检测、对象存储生命周期 |
| 导入导出 | CSV 模板、导入校验、导出防公式注入 | 部分具备 | 用户导入导出 | 可抽取轻量导入导出规范，但不建议做通用导入导出中心 |
| 批处理治理 | 长任务进度、取消、失败明细、失败项重试 | 基础具备 | `BatchTaskService`、`batch_task`、`batch_task_item`、批处理任务中心页面；业务 worker 仍需按场景接入 | P1：补结果文件、断点游标、worker 模板和失败项真实补跑示例 |
| 缓存 | 命名缓存、TTL、容量、事务感知、监控 | 已具备 | `EasyCacheConfig`、Caffeine、Redisson Cache、缓存监控页 | P1：缓存击穿/穿透策略文档、热点 key 治理 |
| 幂等 | 写接口幂等、重试防重 | 已具备 | `@Idempotent`、`IdempotentAspect` | P1：分布式幂等存储和业务幂等 key 规范 |
| 重复提交保护 | 短时间重复点击、表单重复提交 | 已具备 | `@EasyDuplicateRequestLimiter` | 与幂等边界已区分，继续保持轻量 |
| 限流 | IP/用户/全局限流、登录/验证码保护、Redis fallback | 已具备 | `@EasyRateLimit`、`EasyRateLimiterAspect`、`InMemoryRateLimiter`、`RedissonRateLimiter` | P1：429 响应标准、`Retry-After`、后台配置化策略 |
| 锁封装 | MySQL/Redis 锁、跨实例互斥 | 已具备 | `IEasyLocker`、`MysqlEasyLocker`、`RedisEasyLocker` | P1：锁超时、续期、可观测指标和使用规范 |
| 事务和最终一致性 | 本地事务、Outbox、本地消息重试 | 部分具备 | `EasyLocalMessageTemplate`、`LocalMessageRetryJob` | P0/P1：退避策略、人工处理页、积压告警、消息幂等消费 |
| 定时任务 | 任务声明、数据库启停、Cron、执行日志、慢任务 Trace Tree | 已具备 | `@EasyJob`、`ScheduleJobManager`、`ScheduleJobLogCallback` | P1：错过执行补偿、任务互斥、任务失败告警 |
| 轻量工作流 | 流程定义、审批任务、抄送、消息联动、实例监控 | 已具备 | `module.workflow`、LogicFlow 前端 | 保持轻量，不演进成完整 BPM 引擎 |
| 消息中心 | 站内消息、未读数、已读、业务跳转 | 已具备 | `module.message`、`MessageCenterView` | 可补消息模板、渠道扩展，但不建议内置短信/邮件平台 |
| 通知模板 | 站内信、邮件、短信、企微/钉钉、Webhook 的模板和变量 | 缺口 | 当前以站内消息为主 | P1/P2：先抽通知模板和发送端口，具体短信/企微/钉钉实现按企业接入 |
| Webhook / 开放集成 | 出站 Webhook、第三方应用凭证、签名、重放保护、调用审计 | 缺口 | 当前有 Feign/Kafka 基础设施，但没有开放集成模型 | P2：企业要对接 OA、ERP、工单、企微/钉钉时再沉淀统一集成端口 |
| 业务事件 | 领域事件、业务状态变更、事件发布、事件订阅、事件审计 | 部分具备 | 工作流事件、本地消息、Kafka 基础设施 | P1：沉淀统一业务事件模型，区分审计事件、消息事件和集成事件 |
| 报表 | 固定纸质报表、打印 | 已具备 | `module.report` | 不建议内置 BI 或拖拽报表平台 |
| 运行监控页 | JVM、CPU、内存、磁盘、缓存、在线用户、WebLog | 已具备 | `module.monitor`、Actuator、WebLog | 边界是应用内排障，不替代 Grafana/APM/日志平台 |
| 业务指标 | API、远程调用、限流、调度、Outbox 指标 | 已具备 | `EasyBusinessMetrics`、Micrometer、Influx registry | P1：指标字典、面板模板、SLO 绑定 |
| 前端观测 | Vue 错误、Promise rejection、路由错误、API 失败事件 | 部分具备 | `src/features/observability/events.ts` | P1：事件上报接口、RUM 性能指标、采样和隐私策略 |
| Trace / MDC | `X-Trace-Id`、日志 MDC、Kafka/Feign/线程池透传、本地 Trace Tree | 部分具备 | `EasyTraceIdFilter`、`TraceContext`、`@EasyTrace` | P1：W3C Trace Context / OpenTelemetry 可选接入；当前按项目决策先不动 |
| 结构化日志 | 本地日志、集中检索字段、OpenSearch/SLS/Loki 采集 | 部分具备 | `logback.xml` 文本日志、MDC、WebLog | P1：生产 JSON appender 或采集器解析配置 |
| 安全响应头和 CORS | CSP、HSTS、CORS 白名单、WAF 参数过滤 | 部分具备 | `WafFilter`、`EasyCorsFilter`、`easy.web.security-headers` | P1：CSRF、CSP 收紧、生产 Cookie 会话改造 |

## 企业级分布式组件

这些组件支撑多实例、多服务和跨进程协作。脚手架应提供接入边界、默认关闭和本地 fallback，而不是把平台本身做进项目。

| 分布式组件 | 企业级用途 | 当前状态 | EasyNextAdmin 现有落点 | 缺口和建议 |
| --- | --- | --- | --- | --- |
| MySQL | 关系数据、事务、审计、配置、任务、流程 | 已具备 | MyBatis-Plus、Flyway、MySQL 8.4 本地依赖 | 生产高可用、备份恢复、读写分离由部署环境提供 |
| Flyway | 数据库版本管理、可重复部署 | 已具备 | `db/migration`、H2 测试迁移 | 可补迁移回滚策略和大表变更规范 |
| Redis | 会话、缓存、验证码、限流、幂等、锁 | 可选具备 | `easy.features.redis`、Redisson、Spring Data Redis | 生产 Redis 高可用、持久化、监控和容量治理由外部提供 |
| 消息队列 | 异步事件、削峰、跨服务通知、最终一致性 | 可选具备 | `easy.features.kafka`、Kafka Producer/Consumer、Topic、健康检查、trace 透传 | 当前以 Kafka 为样板；RabbitMQ/RocketMQ 不默认内置，按企业现有技术栈扩展 |
| OpenFeign | 服务间 HTTP 调用 | 已具备 | `EasyFeignConfig`、`feign-hc5`、`feign-micrometer` | 缺真实业务 Feign 样例和调用降级策略模板 |
| Resilience4j | 熔断、隔离、超时、重试治理 | 部分具备 | `spring-cloud-starter-circuitbreaker-resilience4j`、`EasyCircuitBreakerConfig` | 当前配置偏基础，缺按依赖分级的超时/重试/熔断模板 |
| 对象存储 | 文件外部化、静态资源、归档 | 可选具备 | 本地存储、Aliyun OSS 可选 | 可增加 S3/MinIO 抽象实现和生命周期文档 |
| 配置中心 | 动态配置、灰度配置、统一密钥引用 | 缺口 | 当前使用 Spring 配置文件、环境变量、启动参数 | 中小企业可先用环境变量；多环境多服务后再接 Nacos/Apollo/Spring Cloud Config |
| 服务注册发现 | 服务定位、健康摘除 | 缺口 | 当前单体优先，无注册中心 | 单体后台不急需；微服务拆分后再接 Nacos/Consul/Eureka 或 K8s Service |
| API 网关 | 统一入口、认证前置、限流、路由、灰度 | 缺口 | 当前由 Nginx/前端代理承担基础反代 | 生产建议用 Nginx/Ingress/API Gateway；不建议项目内置网关 |
| 分布式任务调度平台 | 多实例任务协调、分片、补偿、任务治理 | 部分具备 | 自研轻量 `ScheduleJobManager` | 如任务规模变大，可外接 XXL-JOB、PowerJob 或云调度；脚手架保留轻量任务 |
| 分布式事务 | TCC/Saga/事务消息/补偿 | 部分具备 | 本地事务 + Outbox | 不建议引入 Seata 作为默认；按业务选择 Outbox/Saga/TCC |
| 搜索引擎 | 全文检索、复杂查询、日志检索 | 缺口 | 无业务搜索引擎 | 后台 CRUD 先用 MySQL；日志检索交给 OpenSearch/SLS |
| 分布式 ID | 表主键全局唯一、跨实例写入不冲突 | 部分具备 | MyBatis-Plus `ASSIGN_ID` 已用于实体主键；`EasyIdGenerator` 提供非业务主键的 UUID 工具 | 主键层够用；不要把 Snowflake 或主键直接暴露为业务编号，业务编号由应用组件负责 |
| CDC / 数据同步 | 数据库变更订阅、异构同步、搜索索引增量更新 | 缺口 | 当前无 CDC 组件 | 只有需要数据湖、搜索索引或跨系统同步时再接 Debezium、Canal 或云 CDC |
| Service Mesh | mTLS、流量治理、透明重试、链路 | 不建议内置 | 无 | 服务数量较少时不建议引入 |

## 企业级平台支撑组件

这些能力通常由企业平台、云服务或运维体系提供。EasyNextAdmin 不应内置平台本身，但要保持清晰接入要求。

| 平台组件 | 企业级用途 | 当前状态 | EasyNextAdmin 现有落点 | 缺口和建议 |
| --- | --- | --- | --- | --- |
| 负载均衡 / 反向代理 | HTTPS 入口、反向代理、健康摘除、基础限流 | 部分具备 | Nginx 示例、前端 Nginx 配置、Actuator 健康检查 | 裸机/VM 走 Nginx；K8s 走 Ingress；应用不内置网关 |
| DNS / 域名治理 | 内外网域名、环境域名、灰度域名、TTL 管理 | 缺口 | 当前只在部署文档中说明前端域名和 CORS | 由企业 DNS 或云解析提供，项目只要求配置真实 Origin 和回调地址 |
| TLS 证书 / PKI | HTTPS 证书、证书轮换、内网 CA、mTLS 基础 | 缺口 | 应用提供安全响应头和 HTTPS 场景配置提示 | 裸机用 Nginx 证书；K8s 用 Ingress/cert-manager；应用不管理证书生命周期 |
| 容器编排 | 扩缩容、滚动发布、健康探针 | 不建议内置 | Dockerfile、健康检查、部署文档 | Kubernetes、Helm、Argo CD 由部署层提供 |
| 制品库 / 镜像仓库 | JAR、前端包、容器镜像、版本保留、镜像扫描 | 缺口 | 当前只有本地构建产物和 Dockerfile | 企业交付需要 Nexus/Artifactory/Harbor/云镜像仓库，发布记录固定版本和 digest |
| Secret 管理 | 密钥托管、轮换、审计 | 缺口 | 当前通过环境变量/启动参数 | 生产使用 K8s Secret、Vault、云 KMS 或配置中心 Secret |
| 指标后端 | 趋势、面板、容量、告警 | 可选具备 | Micrometer + `micrometer-registry-influx` | 缺 Grafana 面板、告警规则、Prometheus/OTLP 可选出口 |
| 告警和事件平台 | 告警规则、通知路由、值班、升级、静默、告警审计 | 缺口 | 当前只有应用指标和基线文档，没有告警平台模板 | 中小企业可先 Grafana Alerting/云监控；成熟团队接 Alertmanager、PagerDuty 或企业 IM |
| 集中日志 | OpenSearch/Elasticsearch/SLS/Loki 检索和告警 | 缺口 | 当前只有本地 logback 文件和 WebLog | P1：JSON 日志或采集器解析字段、索引模板、保留策略 |
| APM / Trace 后端 | 跨服务 trace、服务拓扑、采样分析 | 缺口 | 当前是自研 `X-Trace-Id` + 本地 Trace Tree | P1/P2：OpenTelemetry SDK/Agent + Collector 可选，不强制内置 |
| 备份恢复平台 | 数据库备份、对象存储备份、日志归档、恢复演练 | 缺口 | 文档已有备份恢复要求，代码不负责平台能力 | 由 DBA、云数据库、对象存储和备份平台承载，项目提供表增长和恢复 runbook |
| 成本治理平台 | 资源用量、日志成本、指标基数、对象存储生命周期 | 缺口 | 稳定性和观测基线已有治理原则 | P2：先通过容量巡检和保留周期控制成本，成熟后接云成本或 FinOps 工具 |

## 企业内部交付分层

企业内部部署建议按运行平台分成三套主线，再给 Docker Swarm 一个兼容说明。不要把交付包、发布流程、监控平台和运维制度混在一个“部署文档”里。

| 形态 | 建议维护级别 | 适合场景 | 交付物 | 发布和回滚 |
| --- | --- | --- | --- | --- |
| 裸机/VM 传统包 | 主线一 | 没有 Docker/K8s 平台，只有 Linux、Nginx、MySQL、Redis | 后端 JAR、前端 `dist`、Nginx 配置、systemd 服务、配置样例、发布/回滚 runbook | 版本目录 + `current` 软链切换；systemd 重启；数据库迁移先备份 |
| 单机 Docker Compose 包 | 主线二 | 有 Docker，但没有集群编排；适合小企业、演示、私有化单机交付 | 后端镜像、前端镜像、`compose.yaml`、`.env.example`、volume 和日志说明 | 镜像 tag/digest 固定；`docker compose pull/up -d`；回滚到上一镜像 tag |
| Docker Swarm 包 | 兼容路线 | 客户已有 Swarm 集群和运维经验，但不准备上 K8s | `docker stack` 示例、secret/config、service update/rollback 说明 | 作为 Compose 包的派生说明维护，不和 K8s 平级投入 |
| K8s 云原生包 | 主线三 | 已有 Kubernetes、Ingress、Secret/ConfigMap、镜像仓库和运维平台 | 后端镜像、前端镜像、Helm 或 Kustomize、Deployment、Service、Ingress、探针、资源配额 | 使用镜像 tag/digest 回滚；readiness 摘流；数据库变更走 expand/migrate/contract |

四种形态的共同原则：

- 都使用 `prod` profile，不把 `local` profile 带到生产。
- 都不把 MySQL、Redis、对象存储密钥写进代码、镜像或前端产物。
- 都要有发布前检查、发布后检查、回滚步骤和数据备份策略。
- 都要把上传文件、审计日志、应用日志、数据库备份纳入恢复计划。
- 都应把内置监控页视为应用内排障入口，集中日志、指标和告警由外部平台承载。

交付、部署、监控和运维迭代应分成四条线维护：

| 维度 | 关注点 | 建议沉淀物 |
| --- | --- | --- |
| 交付发布 | 构建、制品、版本号、变更记录、数据库迁移、发布审批、回滚 | 构建流水线、制品清单、发布检查表、回滚 runbook、迁移策略 |
| 部署运行 | 进程托管、容器编排、配置注入、密钥注入、健康检查、资源限制 | 裸机/VM、Compose、K8s 三套部署模板，Swarm 兼容说明 |
| 监控观测 | 指标、日志、Trace、事件、告警、值班通知、SLO 面板 | 应用内监控页、Influx/Grafana 或 Prometheus 面板、日志索引模板、告警规则 |
| 运维迭代 | 备份恢复、容量、证书、补丁、漏洞、故障复盘、演练 | 备份恢复 runbook、容量巡检表、证书轮换计划、安全升级计划、复盘模板 |

### 传统内网部署包

建议沉淀这些文件和模板：

```text
release/
├── backend/easyNextAdmin.jar
├── frontend/dist/
├── nginx/easy-next-admin.conf
├── systemd/easy-next-admin.service
├── config/application-prod.example.yaml
├── runbook/deploy-traditional.md
└── runbook/rollback-traditional.md
```

关键要求：

- 后端 JAR 和前端 `dist` 都带版本目录，例如 `/opt/easy-next-admin/releases/2026-06-25-001`。
- `/opt/easy-next-admin/current` 软链指向当前版本，回滚就是切回上一版软链。
- systemd 只读取环境文件或外部配置，不在服务文件里写密码。
- Nginx 只负责 HTTPS、静态资源、`/api` 反向代理、访问日志和基础限流。
- 日志用 logback 文件 + logrotate，集中日志由采集器读取文件。

### 单机 Docker Compose 包

建议沉淀这些文件和模板：

```text
deploy/compose/
├── compose.yaml
├── compose.prod.yaml
├── .env.example
├── nginx/
├── runbook/deploy-compose.md
└── runbook/rollback-compose.md
```

关键要求：

- Compose 包只面向单机或小规模私有化交付，不承诺跨节点高可用。
- MySQL、Redis 可以在演示环境跟随 Compose 启动，生产建议仍使用外部数据库和 Redis。
- 后端、前端镜像使用明确 tag 或 digest，不能使用 `latest` 作为发布记录。
- volume、上传文件、日志目录必须有备份说明。
- 回滚优先切回上一镜像 tag；数据库迁移仍按兼容迁移处理。

### Docker Swarm 兼容说明

Swarm 不作为 EasyNextAdmin 的主交付路线，但可以给已有 Swarm 团队提供兼容说明：

- 复用 Compose 包的镜像、环境变量和 Secret 命名。
- 用 `docker stack deploy` 管理服务，用 `docker service update` 和 `docker service rollback` 做更新和回滚。
- 明确 manager/worker 节点、资源约束、持久化卷、滚动更新和健康检查要求。
- 不为 Swarm 单独维护完整平台能力文档；新增治理能力优先落到 Compose 和 K8s。

### K8s 云原生部署包

建议沉淀这些文件和模板：

```text
deploy/k8s/
├── base/
│   ├── backend-deployment.yaml
│   ├── frontend-deployment.yaml
│   ├── service.yaml
│   ├── ingress.yaml
│   ├── configmap.yaml
│   └── secret.example.yaml
├── overlays/prod/
└── runbook/
```

关键要求：

- 后端和前端镜像分开构建、分开发布，镜像使用明确 tag，生产发布记录 digest。
- 后端配置走 ConfigMap，密码、Token、对象存储密钥走 Secret 或外部 Secret 管理。
- `/actuator/health/liveness` 用于 liveness，`/actuator/health/readiness` 用于 readiness。
- 设置 CPU/内存 requests 和 limits，避免单个后台服务拖垮节点。
- 使用 rolling update，readiness 未通过前不接流量。
- 上传文件优先外置对象存储；如果使用本地存储，必须明确 PVC 和备份策略。
- 日志输出到 stdout 或文件采集二选一，平台侧统一进入 OpenSearch/SLS/Loki。

## 研发治理组件

这些不是运行时中间件，但企业级开发长期离不开。

| 能力 | 当前状态 | 现有落点 | 建议 |
| --- | --- | --- | --- |
| 自动化测试 | 部分具备 | 后端 JUnit、前端 Vitest、CI | 增加接口契约测试、关键业务集成测试和迁移测试 |
| 代码质量 | 部分具备 | 架构测试、TypeScript、Maven 编译 | 可补 Checkstyle/Spotless/ESLint 统一格式和静态扫描 |
| 安全扫描 | 缺口 | `SECURITY.md` | 可接 Dependabot、CodeQL、SCA、镜像扫描 |
| 依赖/SBOM/License 治理 | 缺口 | Maven、npm 依赖清单 | 可补 SBOM 生成、开源许可证检查、依赖准入和高危漏洞阻断 |
| 文档治理 | 已具备 | `docs/`、开发基线文档 | 后续新增组件必须同步“当前能力”和“路线图”边界 |
| 发布治理 | 部分具备 | `docs/deployment.md`、Dockerfile、CI | 补发布检查表、回滚步骤、变更窗口、发布事件 |
| SLO / 复盘 | 部分具备 | `stability-baseline.md` | 需要落到核心指标、告警规则和故障复盘模板 |
| 容量治理 | 部分具备 | 缓存监控、系统监控、稳定性基线 | 补数据库增长、日志增长、连接池和线程池容量面板 |

## 当前最重要缺口

按 EasyNextAdmin 的定位，优先补这些，不建议先堆大型平台。

| 优先级 | 缺口 | 原因 | 建议落点 |
| --- | --- | --- | --- |
| P0 | 审计/API/任务/Outbox 高增长治理 | 直接影响数据库容量、查询性能、备份恢复 | 默认时间范围、索引复核、清理任务、冷归档文档 |
| P0 | Outbox 退避、死信和人工处理 | 当前最终一致性闭环还不完整 | 本地消息状态页、最大重试后的人工处理、积压指标告警 |
| P0 | 三套交付包和 Swarm 兼容说明 | 内网企业交付差异主要来自运行平台，不应混成一个部署说明 | 裸机/VM、单机 Compose、K8s 三套模板；Swarm 只做兼容说明 |
| P0 | 指标字典和面板模板 | 已有 Micrometer 指标，但缺统一面板沉淀 | `docs/components/observability/metrics.md`、Influx/Grafana 样例 |
| P1 | 数据分类分级和导出治理 | 脱敏只解决展示问题，导出、查询和外发还需要数据级别规则 | 字段分级清单、导出审批、敏感查询审计、外发规则 |
| P1 | 生产结构化日志 | OpenSearch/SLS/Loki 接入需要结构化字段 | JSON appender 或采集器解析方案、索引字段规范 |
| P1 | 前端事件上报 | 当前前端事件仅本地缓冲，不能集中分析 | 前端事件上报 API、采样、隐私字段过滤 |
| P1 | 限流/幂等/锁治理文档 | 这些组件有了，但误用会造成业务问题 | 组件文档、接入示例、反例和测试模板 |
| P1 | 生产会话安全方案 | Bearer token + localStorage 不应作为公开生产方案 | HttpOnly Cookie、CSRF、防重放、会话轮换 |
| P1 | 发布和回滚模板 | 企业交付需要可重复流程 | 发布检查表、回滚 runbook、数据库变更策略 |
| P1 | 批处理治理增强 | 已有轻量任务/明细/取消/失败项重置，缺具体业务 worker 示例和结果文件 | 用户导入或报表生成接入批处理，补结果文件、断点游标和失败项补跑模板 |
| P2 | 敏感数据加密 | 脱敏解决展示和日志问题，不等于数据库高敏字段加密 | 字段加密组件、密钥版本、KMS 接入、解密审计 |
| P2 | Webhook / 开放集成 | 企业对接 OA、ERP、工单和企微/钉钉时需要统一凭证、签名和审计 | 出站 Webhook、API Key、签名验签、重放保护、调用审计 |
| P2 | CDC / 数据同步 | 搜索索引、数据湖或异构同步需要变更订阅，不应靠业务表轮询 | Debezium、Canal、云 CDC 接入说明，默认不内置 |
| P2 | OpenTelemetry 可选接入 | 服务增多后需要标准 trace 和 collector | 保持可选，不影响现有 Trace Tree |
| P2 | 配置中心接入 | 多环境多实例后才需要 | 先定义配置分层和 Secret 规则，再选 Nacos/Apollo/云配置 |
| P2 | 搜索/日志平台接入样例 | 业务搜索和日志检索是不同问题 | 业务搜索按需，日志检索走 OpenSearch/SLS/Loki |

## 不建议默认内置的组件

这些组件企业里常见，但不适合作为 EasyNextAdmin 默认能力：

- API 网关：由 Nginx、Ingress、Spring Cloud Gateway 或云网关提供。
- Kubernetes / Helm / Argo CD：由部署平台提供，项目保留 Dockerfile 和健康检查即可。
- Service Mesh：服务数量少时收益低、复杂度高。
- BI 平台：与本项目“企业后台脚手架”定位冲突。
- 完整 BPM 引擎：当前轻量流程足够二开，不把项目做成 Flowable/Camunda 替代品。
- 统一日志平台：项目提供结构化输出和 traceId，OpenSearch/SLS/Loki 由企业部署。
- APM SaaS：项目保持 OpenTelemetry/Micrometer 接入边界，不绑定厂商。
- 通用低代码扩展中心：容易稀释真实后台能力，不作为产品 UI 暴露。

## 推荐演进路线

### 第一阶段：应用内组件闭环

- 审计/API 日志增长治理。
- Outbox 死信和人工处理。
- 批处理治理增强。
- 指标字典和 Influx/Grafana 面板样例。
- 限流、幂等、锁、任务的组件文档。

### 第二阶段：生产观测和发布治理

- 结构化日志进入 OpenSearch/SLS/Loki。
- 前端事件上报。
- 发布检查表、回滚 runbook、数据库变更策略。
- 数据分类分级、导出审批和敏感查询审计。
- 核心 SLO 和告警规则。

### 第三阶段：按规模接入分布式平台

- 服务拆分后接配置中心、注册发现、网关。
- 多服务链路排障需要时接 OpenTelemetry Collector。
- 高异步规模后增强 Kafka 业务事件模型。
- 对接搜索、数据湖或异构系统时再引入 CDC。
- 大量文件和归档后完善对象存储生命周期。
