# 编译与部署

EasyNextAdmin 建议维护三套企业内部交付主线：裸机/VM 传统部署、单机 Docker Compose 部署、K8s 云原生部署。Docker Swarm 只作为已有 Swarm 团队的兼容说明，不作为主推交付路线。当前仓库的 `docker-compose.yml` 只用于本地 MySQL、Redis 依赖，不作为生产编排方案。

## 交付形态选择

| 形态 | 适合团队 | 推荐方式 |
| --- | --- | --- |
| 裸机/VM 传统包 | 没有 Docker/K8s 平台，只有 Linux 服务器、Nginx、MySQL、Redis | 后端 JAR + systemd，前端 `dist` + Nginx，配置用环境变量或外部配置文件 |
| 单机 Docker Compose 包 | 有 Docker，但没有集群编排；适合小企业、演示和私有化单机交付 | 后端镜像 + 前端镜像 + Compose 文件，生产依赖优先使用外部 MySQL/Redis |
| Docker Swarm 兼容说明 | 客户已有 Swarm 集群和运维经验，但暂不上 K8s | 复用 Compose 镜像和配置，补 `docker stack`、Secret、滚动更新和回滚说明 |
| K8s 云原生包 | 已有镜像仓库、Kubernetes、Ingress、Secret/ConfigMap 和运维平台 | 后端镜像 + 前端镜像，K8s Deployment/Service/Ingress，配置和密钥由平台注入 |

所有生产形态都使用 `prod` profile，都要求外部 MySQL 和可选 Redis；区别在于进程托管、发布回滚、配置密钥和流量摘除方式。裸机/VM、Compose、K8s 是三套主线，Swarm 只做兼容路线。

## 后端构建

在仓库根目录执行：

```bash
mvn -pl easy-next-admin-server -am -DskipTests package
```

产物：

```text
easy-next-admin-server/target/easyNextAdmin.jar
```

如需运行测试：

```bash
mvn -pl easy-next-admin-server -am verify
```

## 后端 JAR 部署

### 环境 Profile

配置文件按职责拆分：

- `application.yaml`：公共基线，放所有环境共享的默认行为。
- `application-local.yaml`：本地开发，保留 Swagger UI、演示账号、前端开发 CORS、较小线程池和便捷数据库/Redis 默认值。
- `application-prod.yaml`：生产部署，内网和公网都从这里起步，数据库、Redis、线程池、Tomcat、CORS 和安全响应头通过环境变量或启动参数覆盖。

`local` 默认 MySQL URL 带 `createDatabaseIfNotExist=true`，只用于本机 root 演示账号在空环境下自动创建数据库。`prod` 不提供该参数：生产数据库应由 DBA 或基础设施流程预建，应用账号只拥有目标 schema 内运行和迁移所需权限，不应拥有全局 `CREATE DATABASE` 权限。

生产部署使用 `prod` profile。`prod` 不提供数据库默认值，必须显式传入 `MYSQL_URL`、`MYSQL_USERNAME` 和 `MYSQL_PASSWORD`；Redis 默认指向 `redis://redis:6379`，真实部署建议显式传入 `EASY_REDIS_ADDRESS` 和 `EASY_REDIS_PASSWORD`。`prod` 默认开启 HSTS、收紧 Actuator 暴露范围和关闭 CORS 白名单。前后端同域网关反代时不需要 CORS；前后端分离域名部署时显式配置真实域名：

```bash
MYSQL_URL="jdbc:mysql://mysql:3306/easy-next-admin?serverTimezone=GMT%2B8&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci&useSSL=true" \
MYSQL_USERNAME=easy_admin \
MYSQL_PASSWORD="替换为生产数据库密码" \
java -jar easy-next-admin-server/target/easyNextAdmin.jar \
  --spring.profiles.active=prod \
  --easy.spring.redis.address=redis://redis:6379 \
  --easy.spring.redis.password="替换为生产 Redis 密码" \
  --easy.web.cors.allowed-origins[0]=https://admin.example.com
```

内网 HTTP 部署仍建议使用 `prod`，再按实际网络环境覆盖差异。例如纯内网未启用 HTTPS 时关闭 HSTS，并把 CORS 域名设为真实内网前端地址：

```bash
java -jar easy-next-admin-server/target/easyNextAdmin.jar \
  --spring.profiles.active=prod \
  --spring.datasource.url="jdbc:mysql://mysql:3306/easy-next-admin?serverTimezone=GMT%2B8&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci&useSSL=false&allowPublicKeyRetrieval=true" \
  --spring.datasource.username=easy_admin \
  --spring.datasource.password="替换为内网数据库密码" \
  --easy.spring.redis.address=redis://redis:6379 \
  --easy.spring.redis.password="替换为内网 Redis 密码" \
  --easy.web.security-headers.hsts-enabled=false \
  --easy.web.cors.allowed-origins[0]=http://admin.intranet.example
```

示例：

```bash
java \
  -jar easy-next-admin-server/target/easyNextAdmin.jar \
  --spring.profiles.active=prod \
  --spring.datasource.url="jdbc:mysql://127.0.0.1:3306/easy-next-admin?serverTimezone=GMT%2B8&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci&useSSL=false&allowPublicKeyRetrieval=true" \
  --spring.datasource.username=root \
  --spring.datasource.password=123456 \
  --easy.features.redis=true \
  --easy.spring.redis.address=redis://127.0.0.1:6379 \
  --easy.spring.redis.password=111222
```

生产环境建议：

- 使用非 `local` profile 启动，避免加载本地开发配置；Swagger UI 和 OpenAPI JSON 在通用配置中默认关闭。
- 内网和公网部署都使用 `prod` profile，并通过环境变量或启动参数提供数据库、Redis、对象存储、线程池容量和真实前端域名。
- 不启用 `local` profile；`/api/auth/demo-accounts` 在生产环境会返回空列表，仍需替换初始化账号密码。
- 使用外部 MySQL 和 Redis，避免把生产状态放在应用容器内。
- Redis 使用能力级开关 `easy.features.redis=true`。开启后缓存、会话、验证码、重复请求、幂等、限流和分布式锁会自动切到 Redis/Redisson 实现。
- Kafka 使用能力级开关 `easy.features.kafka=true`。未开启时不会创建 Kafka Producer、Consumer、Topic Admin 和健康检查，避免单机开发误连 `localhost:9092`。
- Feign、调度、监控、WebLog、OSS 和本地消息也都通过 `easy.features.*` 控制。生产只开启实际需要的能力；Influx 指标导出默认关闭，需要接入外部指标库时再显式配置。InfluxDB 作为当前指标后端没有问题，应用侧通过 Micrometer 输出 `easy.api.requests`、`easy.remote.calls` 等低基数指标，后续要换 Prometheus 或 OTLP 时应新增 exporter，而不是改业务埋点。
- 通过环境变量、启动参数或配置中心覆盖数据库密码、Redis 密码、对象存储配置。
- 按企业安全策略配置会话超时：`EASY_AUTH_SESSION_IDLE_TIMEOUT` 默认 `30m`，`EASY_AUTH_SESSION_ABSOLUTE_TIMEOUT` 默认 `8h`。普通企业后台绝对超时通常为 8-12 小时，公网或高权限后台建议 4-8 小时。
- 按真实前端域名覆盖 `easy.web.cors.allowed-origins` 或 `easy.web.cors.allowed-origin-patterns`，不要在生产环境继续使用本地开发 Origin，也不要开放 `*` 且携带凭证。
- 按部署方式收紧 `easy.web.security-headers`：HTTPS 环境可开启 `hsts-enabled`，稳定资源路径后再配置 `content-security-policy`，避免本地 HTTP 和历史内联资源被误伤。
- 把 `/actuator/health` 暴露给负载均衡健康检查，其他 Actuator 端点按内网权限控制。
- 使用反向代理统一接入 HTTPS、访问日志和限流策略。
- 对公开生产环境，生产安全路线固定为服务端会话 + `HttpOnly; Secure; SameSite` Cookie，并为写接口配置 CSRF 防护；当前 Bearer token 方案只适合作为本地开发和前后端分离调试路线，不作为生产验收方案。
- 用户导入保持 CSV、小文件和行数限制；当前默认单文件不超过 2MB、单次不超过 1000 行，避免后台导入拖垮服务。
- 实时日志的日志级别调整是独立权限 `monitor:weblog:level`，只授予可信运维角色。
- 本地 logback 文件日志用于 WebLog 和现场排障；采集到 OpenSearch / Elasticsearch / SLS / Loki 的日志应通过采集器解析或 JSON appender 形成结构化字段。不要把 WebLog 当集中日志平台使用，也不要在本地文件里长期打开 DEBUG。
- 审计和 API 访问日志要配置保留策略：`audit_api_log` 这类高增长表建议 30-90 天热数据，登录、权限、角色、菜单和敏感变更按企业合规保留更久；清理或归档任务必须记录范围、行数和执行结果。

## 依赖版本与兼容性

本仓库的 `docker-compose.yml` 只作为本地开发依赖编排，当前固定的兼容性基线如下：

| 依赖 | 本地镜像 | 说明 |
| --- | --- | --- |
| MySQL | `mysql:8.4` | MySQL 8.4 LTS。新数据卷使用默认 `caching_sha2_password` 认证，不启用已废弃的 `mysql_native_password`。 |
| Redis | `redis:7.4-alpine` | Redis 7.4 Alpine。开启 AOF，适合本地保留运行态数据；兼容 Redis 7.4 生成的 RDB/AOF 基础文件格式。 |

企业部署时建议保持同一大版本和小版本通道，不要直接使用 `latest`。如果升级 MySQL 或 Redis 的大版本、小版本，需要先在预发环境完成数据库迁移、登录、权限、文件、流程、调度、缓存和审计链路验证。生产镜像建议进一步固定到补丁版本或 digest，并通过镜像扫描和变更窗口统一升级。

MySQL 8.4 默认禁用旧的 `mysql_native_password` 服务端插件，MySQL 9 会移除该插件。EasyNextAdmin 作为全新脚手架不为旧插件保留默认兼容配置；如果企业环境里存在历史账号，应在升级窗口内先迁移到 `caching_sha2_password`，再切换到 MySQL 8.4 或更高版本。

## 前端构建

```bash
cd easy-next-admin-web
npm ci
npm run build
```

产物：

```text
easy-next-admin-web/dist
```

默认构建后的前端仍通过相对路径 `/api` 访问后端。生产部署时，应由 Nginx 或网关把 `/api/` 反向代理到后端服务。

## Nginx 静态部署

示例配置：

```nginx
server {
    listen 80;
    server_name example.com;

    root /usr/share/nginx/html/easy-next-admin;
    index index.html;

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_read_timeout 75s;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

注意 `proxy_pass http://127.0.0.1:8080;` 不带末尾 `/`，这样会保留 `/api` 前缀，匹配当前后端控制器路径。`proxy_read_timeout 75s` 用于给普通业务查询、导入导出和工作流详情留出反向代理读取时间，避免代理先于应用正常响应断开。

## Docker 构建

企业环境通常不要直接把本地 `docker-compose.yml` 当生产编排。推荐做法是：

- 应用镜像和依赖镜像分开发布，MySQL、Redis 优先使用托管服务或独立高可用集群。
- 镜像使用明确 tag，生产发布清单固定 digest；升级通过预发验证和回滚方案管理。
- 密码、Token、对象存储密钥使用 Secret/配置中心，不写入镜像、不提交 `.env`。
- 运行诊断的 Metrics 默认写入服务端 `./data/diagnosis/metrics` 下的 RRD4J 环形时序文件，按 1 分钟、5 分钟和 30 分钟精度归档。默认保留目标为 30 天，配置值不会低于 30 天并可调得更长；文件行数创建后固定，不会因时间推移无限增长。5 分钟和 30 分钟档保存桶末 `LAST` 快照，长时间窗不会承诺保留桶内的所有瞬时峰值；默认本地 MeterRegistry 的累计值会在进程重启后归零。容量、保留期或 series 上限可能让可追溯跨度短于目标或存在缺口。序列容量满时优先保留 JVM、进程、系统和 `easy.operations.*` 核心指标，并按优先级与最近使用时间淘汰低优先级序列。Slow、Errors 和对应 Trace 是同一 canonical incident 的不同投影，稀疏写入滚动 JSONL；URI、异常、堆栈、调用树和操作详情会先复用统一脱敏器并截断到有界长度，再进入异步落盘队列。该队列除 16384 条硬上限外，默认还有 16 MiB 的待写内存预算；队列或预算已经耗尽时会在序列化前快速拒绝，新记录会被丢弃并在存储状态中展示待写 bytes、预算拒绝数和总丢弃数，可通过 `EASY_DIAGNOSIS_PENDING_MAX_BYTES` 调整。Trace 详情可按 traceId 补充 MySQL 中经过数据范围过滤的异常审计证据。正常成功的 HTTP、Job、Kafka、事务、SQL 和远程调用不写 Trace 文件，只进入 Micrometer 聚合。`observability_remote_call_log` 仅是旧版兼容明细，默认不写业务 MySQL，也不参与总量、成功率或平均耗时计算；确有旧接口依赖时可设置 `EASY_DIAGNOSIS_REMOTE_LOG_PERSISTENCE_ENABLED=true`，这时慢或失败远程调用通过容量默认 512 的独立单线程队列异步写入，队列满或应用关闭时丢弃兼容明细，可用 `EASY_DIAGNOSIS_PERSISTENCE_QUEUE_CAPACITY` 调整。开启兼容表后，服务每天按同一个保留期分批物理删除过期行，可通过 `EASY_DIAGNOSIS_REMOTE_LOG_CLEANUP_CRON` 调整清理时刻。RRD 与 JSONL 默认各有 64 MiB 总预算，单个 RRD series 和单个 JSONL 分段默认各以 8 MiB 为上限；JSONL 按保留期和容量共同清理。容器替换时需挂载持久卷，例如 `-v /srv/easy-next-admin/diagnosis:/app/data/diagnosis`；通过 `EASY_DIAGNOSIS_STORAGE_DIR`、`EASY_DIAGNOSIS_RETENTION_DAYS`、`EASY_DIAGNOSIS_HISTORY_MAX_BYTES` 和 Metrics 对应配置调整目录与预算。
- JFR 使用 Java 17 自带的 `jdk.jfr` 模块，以及该模块导出的 `jdk.jfr`、`jdk.jfr.consumer` 包，不需要 Maven 依赖或额外 jar。项目的 Temurin 17 运行镜像会在构建时检查 `jdk.jfr` 和 `jdk.management` 模块；若自行使用 `jlink` 裁剪运行时，也需要显式包含这两个模块。
- JVM 类直方图通过当前 JVM 的 `DiagnosticCommand.gcClassHistogram` 按需执行，不需要 JavaMelody 或额外依赖。它会检查整个堆并可能产生安全点停顿，因此页面要求人工确认、服务端拒绝并发抓取，也不会由定时任务或页面轮询自动触发；高峰期不要执行。
- 诊断开关按层级生效：`EASY_FEATURE_MONITOR=false` 关闭整个监控能力与诊断接口；`EASY_DIAGNOSIS_ENABLED=false` 停止本地诊断历史、JFR 和操作资源采样，并让 JSONL 清理及 JFR 轮转/清理任务直接返回而不扫描存储目录；`EASY_DIAGNOSIS_DETAIL_SAMPLING_ENABLED=false` 只停止单次操作的 CPU、分配内存、锁和堆栈采样。SQL 高频路径默认不读取每条语句的线程 CPU、分配内存和阻塞/等待计数，慢 SQL、错误 SQL 会在深采样预算允许时尝试保留线程栈和锁现场；确需逐条 SQL 资源差值时设置 `EASY_DIAGNOSIS_SQL_RESOURCE_SAMPLING_ENABLED=true`。其他 HTTP、Job、Kafka、事务和远程调用仍按绑定线程区间采样。活跃列表刷新仅批量读取轻量线程状态、当前等待锁和有限堆栈，不主动扫描已持有的 Monitor / Synchronizer；完整持有锁只在慢周期采样或慢/错结束采样的预算内抓取。默认不开启每次操作首尾的 `ThreadInfo` 争用计数读取；确需 blocked/waited 次数及毫秒差值时，再设置 `EASY_DIAGNOSIS_CONTENTION_TIME_ENABLED=true`，该开关同时开启 JVM 全局争用耗时统计。JFR 默认关闭，可通过高级诊断页开启临时会话，也可用 `EASY_DIAGNOSIS_JFR_ENABLED=true` 改为启动时开启；有效会话上限是 `EASY_DIAGNOSIS_JFR_MAX_SESSION_MINUTES` 与 1440 分钟中的较小值，应用重启后恢复配置默认状态。录制以 JDK `default.jfc` continuous 配置为低开销基线，并显式启用页面使用的执行、分配、锁/Park、线程 CPU、GC、慢文件/网络 I/O、Error 和 `RuntimeOperation` 事件；执行采样周期为 20ms，分配采样限制为每秒 150 个，锁/Park 与 I/O 只记录 20ms 以上的事件。JDK 官方将 `default.jfc` 推荐给连续录制，实际开销仍需在目标 JDK 和业务流量下做开启/关闭 A/B。JFR 默认保留 1 天，总容量目标 64 MiB、单段目标 16 MiB，并每 30 分钟轮转；JFR chunk 和无缝轮转的短暂重叠可能让磁盘占用瞬时略高于目标。
- Trace 详情可按 traceId 和操作时间选择对应 JFR 分段并在应用内分析；多个分段由服务端在一次有界请求中扫描，聚合前会去除 JFR chunk 边界重复事件。JFR 状态和文件列表只读取目录元数据，并用文件名中的分段开启时间做保守边界，不会在页面轮询时同步扫描整个 `.jfr`；若 `maxSize` 已淘汰早期 chunk，实际可读起点可能晚于这个边界，分析阶段仍按真实事件时间过滤。联合分析最多处理 32 个分段、250000 个物理事件和 5 秒扫描时间，同时最多运行 2 个任务。只有带 traceId 的项目自定义 `RuntimeOperation` 事件可与一次操作精确关联；快速成功 SQL 的事件只保留 mapper、command 等廉价字段，慢 SQL 和错误 SQL 才延迟生成脱敏归一化详情。JDK JFR 的 CPU、分配、锁/Park、GC 与 I/O 事件仅作为同 JVM、同时间窗口内的堆栈旁证，不能视为对应 traceId 的精确资源归因。原始 `.jfr` 文件也可下载后交给 JDK Mission Control 深入分析。
- 运行诊断、高级诊断、JVM 类直方图、JFR 启停和 JFR 原始文件下载分别要求 `monitor:diagnosis:view`、`monitor:diagnosis:advanced-view`、`monitor:diagnosis:capture`、`monitor:diagnosis:jfr-control`、`monitor:diagnosis:jfr-download`。内置 admin 与 ops 角色默认拥有这些权限；自定义角色应按职责授权。
- 本地存储按实例隔离，不做跨节点聚合。多实例部署必须为每个实例提供独立持久卷，并通过实例定向访问查看对应数据；需要统一趋势、全局告警或跨实例检索时，将 Micrometer 指标和结构化日志接入外部观测平台。
- 容器以非 root 用户运行，限制运行权限，保留健康检查、资源限制和日志轮转。
- 数据目录、上传文件和日志进入持久化卷或外部服务，备份恢复流程要定期演练。
- 业务应用实例保持无状态，横向扩容依赖外部 Redis、数据库和对象存储。

构建后端镜像：

```bash
mvn -pl easy-next-admin-server -am -DskipTests package
docker build -f easy-next-admin-server/Dockerfile -t easy-next-admin-server:local .
```

后端 Dockerfile 放在 `easy-next-admin-server/`，但构建上下文仍然要使用仓库根目录 `.`。镜像构建会复制本地 Maven 产物 `easy-next-admin-server/target/easyNextAdmin.jar`，因此必须先在本地或 CI 中完成 Maven 打包。这样 Docker 镜像阶段只做运行环境封装，不在镜像构建里重复下载 Maven 依赖。后端镜像默认使用 `SPRING_PROFILES_ACTIVE=prod` 启动；本地临时调试容器时，可通过 `-e SPRING_PROFILES_ACTIVE=local` 显式覆盖。

运行后端容器示例。下面命令只用于本机快速验证后端镜像：数据库连宿主机 MySQL，并显式关闭 Redis，避免单容器示例误连 `redis://redis:6379`。接近生产的容器部署应去掉 `EASY_FEATURE_REDIS=false`，并传入真实 Redis 地址和密码。

```bash
docker run --rm -p 8080:8080 \
  -e JAVA_OPTS="-Xms512m -Xmx1024m" \
  -e EASY_FEATURE_REDIS=false \
  easy-next-admin-server:local \
  --spring.datasource.url="jdbc:mysql://host.docker.internal:3306/easy-next-admin?serverTimezone=GMT%2B8&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci&useSSL=false&allowPublicKeyRetrieval=true" \
  --spring.datasource.username=root \
  --spring.datasource.password=123456
```

构建前端镜像：

```bash
cd easy-next-admin-web
npm ci
npm run build
cd ..
docker build -t easy-next-admin-web:local ./easy-next-admin-web
```

前端 Dockerfile 只复制本地构建产物 `easy-next-admin-web/dist` 到 Nginx 镜像，因此必须先完成前端构建。

运行前端容器示例：

```bash
docker run --rm -p 8081:80 easy-next-admin-web:local
```

前端镜像默认把 `/api/` 和 `/storage/` 代理到 `http://host.docker.internal:8080`，适合本地 Docker Desktop 访问宿主机后端。企业部署或前后端同在 Docker 网络时，通过运行时环境变量覆盖代理目标，不需要重新构建镜像：

```bash
docker run --rm -p 8081:80 \
  -e API_PROXY_PASS=http://server:8080 \
  easy-next-admin-web:local
```

前端镜像内置的 `nginx.conf` 默认写入 `X-Frame-Options`、`X-Content-Type-Options`、`Referrer-Policy` 和 `Permissions-Policy`，并对 `/assets/` 指纹文件启用长期缓存，对入口页面使用 `no-store`，避免发版后浏览器长期持有旧入口。

## 发布检查

发布前建议至少执行：

```bash
mvn -pl easy-next-admin-server -am -DskipTests package
cd easy-next-admin-web && npm run build
```

仓库内置 GitHub Actions 工作流会在 push 和 PR 时运行后端 `verify`、前端单元测试和前端构建；本地仍可按需只运行对应命令。

发布后检查：

- `GET /actuator/health` 返回 `UP`。
- 前端刷新任意二级路由不会 404。
- 登录、工作台、用户管理、角色授权、流程待办、实时日志至少各验证一次。
- 用户导入模板、1000 行限制、导出 CSV 脱公式注入至少验证一次。
- 公开生产环境必须替换初始化账号密码；如需公开体验环境，应单独准备脱敏账号和隔离数据，不复用生产配置。
