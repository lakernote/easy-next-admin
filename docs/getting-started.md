# 本地开发

本文说明如何在本地启动 EasyNextAdmin，并确认前后端、数据库和接口文档可用。

## 环境要求

| 工具 | 建议版本 | 用途 |
| --- | --- | --- |
| JDK | 17+ | 运行 Spring Boot 3 服务端 |
| Maven | 3.9+ | 后端依赖管理和构建 |
| Node.js | 22 LTS 或 24 LTS | 前端开发和构建；不再建议使用已 EOL 的 Node.js 20 |
| npm | 随 Node.js 安装 | 前端依赖管理 |
| Docker | 24+ | 本地启动 MySQL、Redis |
| Docker Compose | v2 | 编排本地依赖 |

## 根目录配置文件

`.editorconfig` 用于统一不同 IDE 和编辑器的基础格式，不需要手动执行。IntelliJ IDEA、WebStorm 通常会自动识别；VS Code 需要安装 EditorConfig 插件。当前规则要求 UTF-8、LF 换行、文件末尾保留换行、去除行尾空格，默认 2 空格缩进，Java 文件使用 4 空格。

默认启动不需要额外 `.env` 文件。`docker-compose.yml` 已经在 `${变量名:-默认值}` 中写了本地默认值；如果没有 `.env`，Docker Compose 会直接使用这些默认值。比如 `${MYSQL_PORT:-3306}:3306` 表示宿主机默认使用 `3306` 端口访问容器内 MySQL。

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `MYSQL_IMAGE` | `mysql:8.4` | 本地 MySQL 镜像，固定在 8.4 LTS 通道 |
| `REDIS_IMAGE` | `redis:7.4-alpine` | 本地 Redis 镜像，固定在 7.4 Alpine 通道 |
| `MYSQL_PORT` | `3306` | MySQL 映射到宿主机的端口 |
| `MYSQL_ROOT_PASSWORD` | `123456` | 本地 root 密码，仅用于开发 |
| `REDIS_PORT` | `6379` | Redis 映射到宿主机的端口 |
| `REDIS_PASSWORD` | `111222` | 本地 Redis 密码，仅用于开发 |

临时覆盖端口时，可以直接在命令前加环境变量：

```bash
MYSQL_PORT=13306 REDIS_PORT=16379 docker compose up -d mysql redis
```

如果经常需要覆盖，也可以自己创建根目录 `.env`，该文件不会提交到仓库：

```dotenv
MYSQL_PORT=13306
REDIS_PORT=16379
MYSQL_ROOT_PASSWORD=123456
REDIS_PASSWORD=111222
```

生产环境应使用部署平台的密钥管理、环境变量或配置中心，不复用本地演示密码。

## 启动依赖

`docker-compose.yml` 只负责本地开发依赖，不启动业务应用：

- MySQL 8.4 LTS，Docker 镜像 `mysql:8.4`，默认端口 `3306`，库名 `easy-next-admin`，root 密码 `123456`，新数据卷默认使用 `caching_sha2_password`
- Redis 7.4，Docker 镜像 `redis:7.4-alpine`，默认端口 `6379`，密码 `111222`

```bash
docker compose up -d mysql redis
```

停止依赖：

```bash
docker compose down
```

如果本地库因为迁移历史或测试数据导致启动异常，可以备份并重建：

```bash
mkdir -p work/db-backups
docker exec easy-next-admin-mysql mysqldump --default-character-set=utf8mb4 -uroot -p123456 --single-transaction --set-gtid-purged=OFF easy-next-admin > "work/db-backups/easy-next-admin-$(date +%Y%m%d%H%M%S).sql"
docker exec easy-next-admin-mysql mysql -uroot -p123456 -e "DROP DATABASE IF EXISTS \`easy-next-admin\`; CREATE DATABASE \`easy-next-admin\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
docker exec -i easy-next-admin-mysql mysql --default-character-set=utf8mb4 -uroot -p123456 easy-next-admin < easy-next-admin-server/src/main/resources/db/migration/V1__init.sql
```

上面的命令会备份当前 MySQL 数据库到 `work/db-backups`，然后用 `easy-next-admin-server/src/main/resources/db/migration/V1__init.sql` 重新初始化。

## 启动服务端

```bash
cd easy-next-admin-server
mvn spring-boot:run
```

默认配置：

- 服务端口：`8080`
- 数据库：`jdbc:mysql://localhost:3306/easy-next-admin`
- 默认 profile：`local`
- Flyway：启动时自动执行 `db/migration/V1__init.sql`
- OpenAPI：`http://127.0.0.1:8080/swagger-ui.html`

Redis 默认按企业部署形态启用，需要先启动本地 Redis。Redis 连接和能力开关统一放在 `easy` 命名空间下，便于集中理解：

```bash
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--easy.features.redis=true --easy.spring.redis.password=111222"
```

启用 Redis 后，缓存、会话、验证码、重复请求、幂等、限流和分布式锁会自动切到 Redis/Redisson 实现。临时不依赖 Redis 时可启动参数覆盖 `--easy.features.redis=false`，这些运行态能力会回退到本地内存或 MySQL。

Kafka 默认不启用。如果本地需要调试 Kafka 基础设施，启动时增加 `--easy.features.kafka=true --easy.spring.kafka.bootstrap-servers=127.0.0.1:9092`。

## 启动前端

```bash
cd easy-next-admin-web
npm ci
npm run dev
```

默认前端启动也不需要额外 `.env` 文件。前端代码默认请求 `/api`，Vite 开发服务器默认把 `/api/**` 和 `/storage/**` 代理到 `http://localhost:8080`。

如果需要长期覆盖前端配置，可以自己创建 `easy-next-admin-web/.env.local`。这套变量只给 Vite 前端使用，和根目录 `.env` 不是一回事：

- 根目录 `.env`：给 Docker Compose 用，控制 MySQL、Redis 镜像、端口和密码。
- `easy-next-admin-web/.env.local`：给前端开发和构建用，控制页面标题、接口基础路径、本地代理目标。

本地个性化配置建议写入 `easy-next-admin-web/.env.local`，该文件不会提交到仓库：

```bash
cd easy-next-admin-web
touch .env.local
```

如果后端不是 `8080` 端口，改 `.env.local`：

```dotenv
VITE_API_BASE_URL=/api
VITE_API_PROXY_TARGET=http://127.0.0.1:8081
```

Vite 默认地址：

```text
http://127.0.0.1:5174
```

前端开发服务器会把 `/api/**` 代理到 `VITE_API_PROXY_TARGET`。也可以临时通过命令行覆盖后端地址：

```bash
VITE_API_PROXY_TARGET=http://127.0.0.1:8081 npm run dev
```

## 演示账号

登录页会从 `GET /api/auth/demo-accounts` 读取演示账号。该接口只在 `local` / `demo` profile 返回账号清单，非演示环境返回空列表，避免生产登录页暴露初始化密码。

| 角色 | 账号 | 密码 |
| --- | --- | --- |
| 超级管理员 | `admin` | `admin` |
| 部门负责人 | `manager` | `easynext` |
| 普通员工 | `staff` | `easynext` |
| 审计人员 | `auditor` | `easynext` |

这些账号用于本地开发和公开体验环境，方便第一次启动后直接验证权限、流程、审计和监控页面。正式生产环境必须替换初始化密码，且不要启用 `local` / `demo` profile。

首次登录只校验账号密码。同一用户名和客户端 IP 登录失败后，后续登录会要求验证码。

## 本地检查路径

启动完成后按下面顺序检查：

| 检查项 | 地址 |
| --- | --- |
| 前端登录页 | `http://127.0.0.1:5174/login` |
| 工作台 | `http://127.0.0.1:5174/dashboard` |
| 后端健康检查 | `http://127.0.0.1:8080/actuator/health` |
| OpenAPI UI | `http://127.0.0.1:8080/swagger-ui.html` |
| OpenAPI JSON | `http://127.0.0.1:8080/v3/api-docs` |

## 常见问题

### MySQL 端口被占用

调整环境变量后再启动依赖：

```bash
MYSQL_PORT=13306 docker compose up -d mysql redis
```

然后启动服务端时覆盖数据源地址：

```bash
cd easy-next-admin-server
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:mysql://localhost:13306/easy-next-admin?serverTimezone=GMT%2B8&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci&useSSL=false&allowPublicKeyRetrieval=true"
```

### MySQL 提示 mysql_native_password is not loaded

这通常说明当前机器复用了旧 MySQL 数据卷，里面的 root 账号仍绑定 `mysql_native_password`。MySQL 8.4 默认禁用这个旧插件；全新数据卷会使用默认 `caching_sha2_password`，正常不会出现这个错误。

如果这是全新脚手架，本地数据不需要保留，直接删除旧数据卷重新初始化。这个操作会清空本地数据库：

```bash
docker compose down
docker volume rm easy-next-admin_mysqlData
docker compose up -d mysql
```

如果本地数据需要保留，先临时启用旧插件完成账号迁移，迁移后再移除临时配置。可以在本机临时给 MySQL 启动参数追加 `--mysql-native-password=ON`，不要把它作为脚手架默认配置提交。容器启动后先检查账号认证插件：

```bash
docker compose exec mysql mysql -uroot -p123456 -e "SELECT user, host, plugin FROM mysql.user;"
```

确认能登录后，把 root 账号迁移到 MySQL 8.4 默认认证方式：

```bash
docker compose exec mysql mysql -uroot -p123456 -e "ALTER USER 'root'@'%' IDENTIFIED WITH caching_sha2_password BY '123456'; ALTER USER 'root'@'localhost' IDENTIFIED WITH caching_sha2_password BY '123456'; FLUSH PRIVILEGES;"
```

### Flyway 校验失败

本地开发库可以直接备份并重建，保证数据库结构与当前初始化脚本一致：

```bash
mkdir -p work/db-backups
docker exec easy-next-admin-mysql mysqldump --default-character-set=utf8mb4 -uroot -p123456 --single-transaction --set-gtid-purged=OFF easy-next-admin > "work/db-backups/easy-next-admin-$(date +%Y%m%d%H%M%S).sql"
docker exec easy-next-admin-mysql mysql -uroot -p123456 -e "DROP DATABASE IF EXISTS \`easy-next-admin\`; CREATE DATABASE \`easy-next-admin\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
docker exec -i easy-next-admin-mysql mysql --default-character-set=utf8mb4 -uroot -p123456 easy-next-admin < easy-next-admin-server/src/main/resources/db/migration/V1__init.sql
```

### 前端接口 404 或连接失败

确认后端在 `8080` 端口运行，或通过 `VITE_API_PROXY_TARGET` 指向正确地址。前端 Axios 的业务前缀是 `/api`，不要在页面里直接请求完整后端域名。
