# 编译与部署

EasyNextAdmin 支持传统 JAR + Nginx 部署，也支持构建后端 Docker 镜像。当前仓库的 `docker-compose.yml` 只用于本地 MySQL、Redis 依赖。

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

注意 `proxy_pass http://127.0.0.1:8080;` 不带末尾 `/`，这样会保留 `/api` 前缀，匹配当前后端控制器路径。前端镜像内置的 `easy-next-admin-web/nginx.conf` 也是这个规则。

## Docker 构建

企业环境通常不要直接把本地 `docker-compose.yml` 当生产编排。推荐做法是：

- 应用镜像和依赖镜像分开发布，MySQL、Redis 优先使用托管服务或独立高可用集群。
- 镜像使用明确 tag，生产发布清单固定 digest；升级通过预发验证和回滚方案管理。
- 密码、Token、对象存储密钥使用 Secret/配置中心，不写入镜像、不提交 `.env`。
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
