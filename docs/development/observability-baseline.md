# 可观测性体系蓝图

本文不是当前实现盘点，而是把可观测性领域的 RFC、开放标准、SRE 方法论和主流厂商实践整理成一套适合中小型企业后台落地的体系。EasyNextAdmin 后续按本文做能力沉淀和缺口扫描。

## 一句话目标

可观测性不是“多打日志”或“接一个监控平台”，而是让团队在故障、变慢、越权、数据异常和用户投诉发生时，能用统一证据回答：

- 发生了什么。
- 影响了哪些用户和业务。
- 是入口、应用、依赖、数据、网络还是发布变更导致。
- 当前是否还在恶化。
- 谁需要处理，处理优先级是什么。

## 设计约束

面向中小型企业时，体系必须克制：

- 不默认建设大而全 APM 平台。
- 不让团队维护过多中间件。
- 不把所有日志、trace 和指标无限期保存。
- 不采集高基数、敏感和低价值数据。
- 不让告警直接淹没研发和运维。
- 优先用开放标准和可替换组件，避免被单一厂商锁死。

## 标准和方法论

| 领域 | 标准或资料 | 核心启发 |
| --- | --- | --- |
| 可观测性定义 | [OpenTelemetry: What is observability](https://opentelemetry.io/docs/what-is-opentelemetry/) | 系统要主动产生 traces、metrics、logs 等遥测数据，再送到观测后端分析。 |
| 语义标准 | [OpenTelemetry Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/) | HTTP、DB、Messaging、异常、资源、日志、指标字段应使用统一语义，避免各服务各叫各的。 |
| Trace 传播 | [W3C Trace Context](https://www.w3.org/TR/trace-context/) | 跨服务链路传播使用 `traceparent` / `tracestate`，避免私有 header 导致断链。 |
| 指标暴露 | [Prometheus Metric and Label Naming](https://prometheus.io/docs/practices/naming/)、[OpenMetrics](https://www.cncf.io/projects/openmetrics/) | 指标名称、单位、标签和低基数设计决定后续能不能聚合、告警和长期存储。 |
| 事件格式 | [CloudEvents](https://cloudevents.io/) | 事件数据应有统一元数据，便于跨服务、跨平台传递和路由。 |
| 日志传输 | [RFC 5424 Syslog](https://datatracker.ietf.org/doc/html/rfc5424) | 日志进入外部系统时应考虑标准格式、结构化字段和可解析性。 |
| 日志安全 | [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html) | 明确哪些事件必须记录、哪些数据必须排除，避免安全风险和告警噪声。 |
| 日志管理 | [NIST SP 800-92](https://csrc.nist.gov/pubs/sp/800/92/final) | 企业日志管理要覆盖采集、存储、分析、保护、归档、处置和流程。 |
| 服务目标 | [Google SRE: Implementing SLOs](https://sre.google/workbook/implementing-slos/) | 观测数据最终应服务于 SLI/SLO、错误预算和稳定性决策。 |

## 厂商打法

主流厂商的方向高度一致，但产品形态不同。

| 厂商或生态 | 典型打法 | 对中小企业的借鉴 |
| --- | --- | --- |
| AWS Well-Architected | 用 Well-Architected Reliability / Operational Excellence 指导监控、自动化、恢复和持续改进。 | 先定义工作负载关键路径和恢复目标，再补指标、告警和演练，不从工具开始。 |
| Azure Well-Architected | Reliability 强调可用性、恢复能力、冗余、容量和设计检查清单。 | 用 checklist 固化设计评审，避免依赖个人经验。 |
| Google Cloud Architecture Framework | 把可靠性、运营卓越、自动化、监控、容量和变更治理合在架构框架里。 | 把观测性做成工程流程的一部分，而不是上线后补。 |
| OpenTelemetry 生态 | 统一 instrumentation、collector、exporter 和语义约定，后端可接多厂商。 | 应用侧尽量标准化，后端可先开源自建，后续迁移托管平台。 |
| Grafana LGTM | Loki 日志、Grafana 可视化、Tempo trace、Mimir/Prometheus 指标组合成开放栈。 | 适合希望自建、成本可控、团队有运维能力的企业。 |
| Datadog / New Relic / Honeycomb | SaaS 平台统一 APM、日志、指标、trace、SLO、错误预算和事件关联。 | 借鉴其“服务目录 + SLO + 关联排障”模型，不一定直接购买全套。 |
| Elastic Observability | 搜索和日志分析强，逐步扩展到 APM、指标、trace 和安全分析。 | 日志检索和安全审计强需求场景可参考。 |
| 阿里云 SLS / ARMS | SLS 统一 logs、metrics、traces、events；ARMS 强调应用监控、链路和业务指标。 | 国内企业常见选择：云上低运维成本，适合先托管后自研沉淀。 |
| 腾讯云 TCOP | 指标、链路、日志、事件和告警统一入口，覆盖云资源和自定义监控。 | 适合已在腾讯云上部署的企业，重点借鉴云资源 + 应用统一视图。 |
| 华为云 AOM | 一站式指标、trace、日志、事件观测和告警。 | 适合政企或华为云环境，重点借鉴应用、容器、基础设施联动。 |

厂商共性可以抽象为五层：

```text
采集标准化 -> 传输管道化 -> 存储分层化 -> 分析关联化 -> 告警行动化
```

中小企业不需要一次做满，但不能跳过“标准化”和“行动化”。

## 信号体系

### Metrics

回答“是否异常、趋势如何、是否要告警”。

优先采集：

- RED：Rate、Errors、Duration，用于 HTTP/API/远程调用。
- USE：Utilization、Saturation、Errors，用于 CPU、内存、磁盘、线程池、连接池。
- 业务计数：登录失败、限流命中、任务失败、Outbox 堆积、文件上传失败、审批超时。
- SLO 指标：好事件、坏事件、总事件、错误预算消耗。

设计原则：

- 只用低基数标签。
- 路径用 route template，不用真实 URL。
- 时间单位用 seconds，大小用 bytes，计数器用 total 语义。
- 不把用户 ID、traceId、手机号、IP 原文、订单号、文件名作为标签。

EasyNextAdmin 当前指标出口建议：

- 应用侧统一使用 Micrometer 建模，业务代码只依赖 `MeterRegistry` 和内部指标门面。
- InfluxDB 作为当前可选指标后端没有问题，适合中小型团队快速做趋势、容量和面板。
- 指标命名、单位和标签必须先标准化；不要因为使用 InfluxDB 就把高基数字段写成 tag。
- 后续如果需要 Prometheus 或 OTLP，只新增 registry/exporter，不改业务指标代码。

### Logs

回答“具体发生了什么、上下文是什么”。

日志要分层：

- 应用运行日志：用于排障。
- 安全日志：认证、授权、输入异常、敏感操作。
- 审计日志：谁在什么时间改了什么。
- 访问日志：请求入口、状态、耗时、客户端信息。

生产日志建议结构化，最小字段：

| 字段 | 说明 |
| --- | --- |
| `timestamp` | 统一时间格式和时区 |
| `level` | INFO / WARN / ERROR |
| `service` | 服务名 |
| `env` | 环境 |
| `trace_id` | 链路 ID |
| `span_id` | 可选 span ID |
| `user_id` | 登录后用户 ID，必要时脱敏或哈希 |
| `event_type` | 标准事件类型 |
| `outcome` | success / failure / denied / timeout |
| `message` | 脱敏后的描述 |
| `error.type` | 异常类型 |
| `error.message` | 脱敏异常摘要 |

本地日志和集中日志建议分开：

- 本地文件保留人可读文本，服务 WebLog、现场排障和快速 tail。
- 进入 OpenSearch / Elasticsearch / SLS / Loki 的日志使用 JSON appender，或由采集器解析成结构化字段。
- 两条链路共享 traceId、userId、eventType、outcome 等字段，但不要强制本地文件也变成难读 JSON。
- DEBUG 日志只允许临时开启，必须有权限、审计和自动恢复时间。

### Traces

回答“一次请求慢在哪里、跨服务哪里断了”。

最小要求：

- HTTP 入口创建或继承 trace。
- 出站 HTTP / Feign / RestClient 透传 trace。
- 消息生产和消费透传 trace。
- 慢请求和错误请求能定位数据库、远程调用、缓存、业务分支。
- 支持 W3C `traceparent`，保留业务友好的 `X-Trace-Id`。

中小企业不必全量采样所有 trace：

- 错误 100% 保留。
- 慢请求 100% 保留。
- 普通请求按比例采样。
- 高价值业务链路可提高采样率。

### Events

回答“发生了哪个业务或系统状态变化”。

适合用事件表达：

- 发布开始、发布成功、发布回滚。
- 配置变更。
- 权限变更。
- 任务失败进入人工处理。
- Outbox 达到最大重试。
- SLO 状态变化。

内部事件字段可参考 CloudEvents 思路：

| 字段 | 说明 |
| --- | --- |
| `id` | 事件 ID |
| `source` | 事件来源 |
| `type` | 事件类型 |
| `subject` | 业务对象 |
| `time` | 发生时间 |
| `trace_id` | 关联链路 |
| `data` | 脱敏业务载荷 |

Events 的存储不要一刀切：

- 前端错误、路由失败、资源加载失败、发布事件、SLO 状态变化，优先进入日志/事件平台，例如 OpenSearch、SLS、Loki 或云厂商事件中心。
- 强审计事件，例如权限变更、用户状态变更、敏感配置变更，进入审计表，满足查询和合规留存。
- 跨服务业务事件，例如订单状态变化、工作流推进、Outbox 最大重试，进入 Outbox / Kafka / 消息系统，保证传递和补偿。
- 事件平台里的 event 适合关联和告警；审计表里的 event 适合追责；消息系统里的 event 适合驱动业务。

### Profiles

回答“CPU、内存或锁竞争花在哪里”。

中小企业可以后置：

- 先做好 metrics、logs、traces。
- 只有遇到 CPU 高、内存泄漏、锁竞争、GC 抖动时再引入 profiling。
- 如果用 Grafana Pyroscope、Async Profiler 或云厂商 profiler，应默认低开销、按需开启。

### RUM 和前端观测

企业后台也需要前端观测，因为很多问题只发生在用户浏览器。

最小采集：

- 白屏和 Vue 全局错误。
- 未处理 Promise rejection。
- Axios 请求失败和最后一个 traceId。
- 路由加载失败。
- 页面加载耗时、首屏耗时、资源加载失败。

注意：

- 不采集表单明文、token、Cookie、完整 URL 查询参数。
- traceId 要能和后端审计、日志、API 访问日志关联。

## 中小企业落地架构

### L0：单体内网排障型

适合：小团队、单体应用、内网部署、预算有限。

能力：

- 标准文本日志 + traceId。
- Actuator health / metrics。
- 审计表。
- 慢请求日志。
- WebLog 只给可信运维角色。

边界：

- 只能本地排障。
- 不能做长期趋势、统一告警和跨实例分析。

### L1：低成本开源统一观测型

适合：1-5 个服务，开始有生产告警和容量问题。

推荐组合：

- Prometheus：指标采集。
- Grafana：面板。
- Loki 或 Elasticsearch：日志。
- Jaeger 或 Tempo：trace。
- Alertmanager：告警通知。

关键要求：

- 指标标签低基数。
- 日志结构化。
- trace 采样。
- 告警只围绕 SLO、错误率、容量和关键依赖。

### L2：OpenTelemetry 标准化管道型

适合：服务增多，可能混合云、自建和托管平台。

推荐：

- 应用侧使用 OpenTelemetry SDK / Agent。
- 中间层使用 OpenTelemetry Collector。
- 后端可以是 Grafana、Elastic、Datadog、New Relic、阿里云 SLS / ARMS 等。

价值：

- 应用埋点和后端平台解耦。
- 统一 resource attributes。
- 支持多后端迁移和并行验证。

### L3：SLO 驱动运营型

适合：业务已经要求稳定性承诺。

能力：

- 服务目录。
- SLO / 错误预算。
- 多窗口 burn rate 告警。
- 发布事件和故障事件关联。
- 自动生成复盘数据。
- 观测成本治理。

## 推荐数据模型

统一资源标签：

| 字段 | 示例 |
| --- | --- |
| `service.name` | `easy-next-admin-server` |
| `service.version` | `1.0.0` |
| `deployment.environment` | `prod` |
| `host.name` | `admin-01` |
| `cloud.provider` | `aliyun`、`aws`、`tencent` |
| `region` | `cn-hangzhou` |
| `team` | `platform` |

HTTP 标签：

| 字段 | 示例 |
| --- | --- |
| `http.request.method` | `GET` |
| `http.route` | `/api/system/users/{id}` |
| `http.response.status_code` | `200` |
| `error.type` | `TimeoutException` |

业务标签只保留低基数：

| 字段 | 示例 |
| --- | --- |
| `module` | `system`、`workflow`、`audit` |
| `operation` | `create_user`、`approve_task` |
| `outcome` | `success`、`failure`、`denied` |

## 告警原则

告警只分三类：

- Page：现在必须处理，否则用户继续受影响。
- Ticket：几天内处理，防止演变成故障。
- Log：只记录，不打扰人。

优先级顺序：

1. SLO 错误预算快速燃烧。
2. 登录不可用、核心 API 错误率升高。
3. 数据库、Redis、文件存储等关键依赖不可用。
4. Outbox、任务、队列出现不可自动恢复堆积。
5. 容量长期接近阈值。

不建议告警：

- 单次 500。
- 单次慢请求。
- 短暂 CPU 尖刺。
- 已自动恢复且没有用户影响的重试。

## 可观测性成本治理

中小企业尤其要控制观测成本：

- 指标控制标签基数。
- trace 控制采样率。
- 日志按级别和环境过滤。
- 审计日志与排障日志保留周期分开。
- 热存储只留近期，冷归档留合规周期。
- 调试级日志默认关闭，临时开启必须有权限和自动恢复时间。

审计和 API 日志增长治理建议：

- `audit_api_log` 属于高增长访问日志，默认只保留 30-90 天热数据；超过周期后按月归档到对象存储或低成本库。
- 登录、权限、角色、菜单、敏感数据变更属于安全审计，保留周期通常高于 API 访问日志，可按企业合规要求保留 180-365 天或更久。
- 错误日志和异常明细优先保留 30-90 天热数据，长期趋势交给指标，长期原文交给集中日志归档。
- 查询页面默认带时间范围，禁止无条件扫全表；常用查询字段要有索引，历史归档不参与默认在线查询。
- 清理任务必须记录执行审计，包括清理范围、清理行数、执行人或任务名、开始结束时间。
- 大表治理优先级：时间字段索引 -> 默认时间范围 -> 定时清理 -> 冷归档 -> 必要时按月分区或拆冷热表。

## EasyNextAdmin 应沉淀的能力

这不是现状描述，而是后续扫描清单：

| 优先级 | 能力 | 落地方向 |
| --- | --- | --- |
| P0 | 指标命名和标签规范 | 新增 `docs/components/observability/metrics.md` |
| P0 | Micrometer + InfluxDB 指标出口 | `EasyBusinessMetrics` 覆盖 API、远程调用、限流、调度任务、Outbox，`micrometer-registry-influx` 负责导出 |
| P0 | API 访问日志与指标职责分离 | `@EasyApiAccessLog` 只写访问日志，指标统一走 `EasyBusinessMetrics` |
| P1 | W3C Trace Context 兼容 | `EasyTraceIdFilter`、Feign、Kafka、前端 trace |
| P1 | 生产集中日志结构化 | 本地文本日志 + OpenSearch/SLS/Loki 结构化采集链路 |
| P1 | 前端错误和性能观测 | Vue 全局错误、Promise rejection、Axios 失败事件、本地事件模型 |
| P1 | 审计归档和保留策略 | 审计组件文档、清理任务、后续 Flyway 方案 |
| P2 | 观测性接入清单 | 新增模块时检查 metrics、logs、traces、audit |
| P2 | Grafana / 云厂商面板样例 | 只作为部署参考，不内置平台 |

## 90 天落地路线

### 0-30 天：标准先行

- 固定指标、日志、trace、事件字段规范。
- 明确哪些数据不能采集。
- 明确 WebLog、审计、集中日志的边界。
- 先补核心 API、登录、任务、Outbox、远程调用指标；当前指标出口优先 InfluxDB。

### 31-60 天：最小闭环

- 建立 InfluxDB 指标库和核心面板；如服务数量增加，再补 Prometheus 或 OTLP。
- 建立核心 Grafana 面板。
- 结构化日志进入统一检索。
- traceId 能在前端、审计、日志、API 访问日志之间串起来。

### 61-90 天：SLO 联动

- 为稳定性文档中的 SLO 提供指标。
- 建立错误预算和 burn rate 面板。
- 告警接入 Page / Ticket / Log 分级。
- 形成新增模块观测性接入检查。
