# Docker / Redis / Elasticsearch 部署指南

> 适用项目：My-Personal-Blogger（行吟博客）
>
> 本文所有版本号、环境变量、端口、路径均取自本项目源码（`pom.xml`、`application-dev.yml`、`application-prod.yml`、`RedisConfig.java`、`articles-mapping.json`），不是通用模板，照做即可。
>
| 组件 | 项目实际版本 / 取值 | 来源 |
|---|---|---|
| JDK | **21**（镜像必须用 JDK 21，不能用 17） | `pom.xml` `java.version=21` |
| Spring Boot | 4.1.1 | `pom.xml` |
| 构建产物 | `target/blog-0.0.1-SNAPSHOT.jar` | artifactId=`blog`，version=`0.0.1-SNAPSHOT` |
| MySQL | 9.x（库名 `csulzc_blogdb_prod`，账号 `blog_app`） | `application-prod.yml` |
| Redis | 7.x，Lettuce 连接池（commons-pool2 已引入） | `pom.xml` / README |
| Elasticsearch | **9.4.5** + IK 分词 **9.4.5**（版本必须逐位一致） | Spring Boot 4.1.1 BOM 管理 elasticsearch-java 9.4.5 |
| 前端 | Vite 6 + React 19，pnpm 构建，开发端口 5173 | `frontend/package.json` |
| 后端端口 | 8080（未显式配置，即 Spring Boot 默认） | — |

开始前必须知道的三个项目事实：

1. **配置是外置的**。根目录 `.env` 通过 `spring.config.import=optional:file:./.env[.properties]` 注入，且已在 `.gitignore` 中。容器里不要打 `.env` 进镜像，统一用环境变量注入。
2. **profile 决定行为**。默认激活 `dev`；生产必须用 `SPRING_PROFILES_ACTIVE=prod`，此时 Flyway 自动建表建索引、`ddl-auto=validate`、缓存与 ES 强制开启。
3. **prod 配置里有两个"localhost 坑"**：JDBC URL 写死 `jdbc:mysql://localhost:3306/...&requireSSL=true`，ES 写死 `http://localhost:9200`。容器化后必须用环境变量整体覆盖（见 1.6），不能依赖默认值。

---

# 一、Docker 部署流程（推荐：全栈 docker compose）

个人博客规模，最优做法是**一个 compose 文件编排 5 个容器**（MySQL + Redis + ES + 后端 + 前端 Nginx），数据走命名卷持久化，对外只暴露 80 端口。中间件之间走容器网络 DNS（用服务名 `mysql`/`redis`/`es` 互访，不要写 localhost 或 IP）。

## 1.1 目录结构

在项目根目录新建 `deploy/`（只放部署物料，不污染源码树）：

```
deploy/
├── docker-compose.yml          # 全栈编排
├── .env                        # 真实密码，禁止提交（compose 自动读取）
├── .env.example                # 占位模板，提交到仓库
├── backend/
│   └── Dockerfile              # 后端分层镜像
├── frontend/
│   ├── Dockerfile              # 前端多阶段构建（node 构建 → nginx 托管）
│   └── nginx.conf              # SPA 回退 + /api 反代 + 10MB 上传限制
└── es/
    └── Dockerfile              # ES 9.4.5 + IK 9.4.5 预装镜像
```

另外在**项目根目录**放一个 `.dockerignore`（后端构建上下文是项目根目录）：

```gitignore
target/
.git/
.github/
frontend/node_modules/
frontend/dist/
.pnpm-store/
uploads/
docs/
*.md
.env
deploy/.env
```

## 1.2 环境变量文件

`deploy/.env.example`（提交仓库）：

```dotenv
# ===== MySQL =====
MYSQL_ROOT_PASSWORD=change-me-root-strong-password
MYSQL_PASSWORD=change-me-app-strong-password
# 应用侧读取的是 application-prod.yml 里的占位符，变量名必须逐字一致
DB_USERNAME_PROD=blog_app
DB_PASSWORD_PROD=change-me-app-strong-password

# ===== Redis =====
REDIS_PASSWORD=change-me-redis-strong-password

# ===== JWT（无默认值，缺失应用直接启动失败；≥32 字符）=====
JWT_SECRET_PROD=change-me-to-a-random-string-at-least-32-chars

# ===== 首次部署 / 索引修复时临时设 true，跑完一次必须改回 false =====
REINDEX_ON_STARTUP=false

# ===== 可选：ES 开启 X-Pack 安全时才需要（本文默认内网关闭认证，留空）=====
ELASTICSEARCH_USERNAME=
ELASTICSEARCH_PASSWORD=
```

复制为 `deploy/.env` 填入真实强密码。`deploy/.env` 不要提交（根 `.gitignore` 已忽略根目录 `.env`，建议补一行 `deploy/.env`）。

> ⚠️ 不要把仓库根目录现有 `.env`（dev 用、含开发库口令）用于生产部署，两套密钥分离。

## 1.3 后端镜像：`deploy/backend/Dockerfile`

多阶段构建 + Spring Boot 分层 jar（依赖层与代码层分离，改代码后重新构建只需秒级）：

```dockerfile
# syntax=docker/dockerfile:1

# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 先单独拷 pom 预热依赖缓存
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
# 测试使用 H2 内存库、不依赖外部中间件（已在 CI 中执行），镜像构建阶段跳过加速
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q clean package -DskipTests

# 解出分层目录：dependencies / spring-boot-loader / snapshot-dependencies / application
WORKDIR /build/target
RUN java -Djarmode=layertools -jar blog-0.0.1-SNAPSHOT.jar extract

# ---------- 运行阶段 ----------
FROM eclipse-temurin:21-jre-jammy
# curl 供健康检查使用；建非 root 用户与 prod 配置中写死的两个目录
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -r -u 1001 appuser \
    && mkdir -p /app /var/www/uploads /var/log/blog \
    && chown -R appuser:appuser /app /var/www/uploads /var/log/blog

WORKDIR /app
# 层顺序：变化频率从低到高，最大化利用构建缓存
COPY --from=build --chown=appuser:appuser /build/target/dependencies/         ./
COPY --from=build --chown=appuser:appuser /build/target/spring-boot-loader/    ./
COPY --from=build --chown=appuser:appuser /build/target/snapshot-dependencies/ ./
COPY --from=build --chown=appuser:appuser /build/target/application/           ./

USER appuser
EXPOSE 8080
# JVM 按容器内存限额自适应；JAVA_OPTS 可在 compose 中追加
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
```

要点：

- 基础镜像必须 `*-temurin-21*`。本项目编译目标是 Java 21，用 17 运行会报 `UnsupportedClassVersionError`。
- `/var/www/uploads`（`file.upload-dir`）和 `/var/log/blog`（日志与 Tomcat access log）是 `application-prod.yml` 写死的路径，镜像里预先建好并授权，再用卷挂进去持久化。

## 1.4 前端镜像：`deploy/frontend/Dockerfile` + `nginx.conf`

`deploy/frontend/Dockerfile`：

```dockerfile
# ---------- 构建阶段 ----------
FROM node:22-alpine AS build
WORKDIR /app
RUN corepack enable          # 启用 pnpm（仓库用 pnpm-lock.yaml，且 pnpm 11 读 pnpm-workspace.yaml）

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
# request.ts 默认 baseURL 就是 '/api'，这里显式注入防止误读开发态 .env.development
ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN pnpm build               # 产物在 /app/dist

# ---------- 托管阶段 ----------
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

`deploy/frontend/nginx.conf`：

```nginx
server {
    listen 80;
    server_name _;
    root /usr/share/nginx/html;

    # 与后端 multipart.max-request-size=10MB 对齐，否则大图片在 Nginx 层被 413 拦掉
    client_max_body_size 10m;

    gzip on;
    gzip_types text/css application/javascript application/json image/svg+xml;
    gzip_min_length 1024;

    # React Router history 路由回退
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 与 Vite 开发代理等价：/api 全部转发到后端容器
    location /api/ {
        proxy_pass http://blog:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 30s;
    }
}
```

> 注意 `proxy_pass http://blog:8080;` 不带 URI 后缀，保留 `/api/...` 原始路径，与后端控制器前缀 `/api/**` 一致。

## 1.5 ES 镜像：`deploy/es/Dockerfile`

IK 插件必须烤进镜像（容器重建后插件不丢失），版本与 ES 逐位一致：

```dockerfile
FROM docker.elastic.co/elasticsearch/elasticsearch:9.4.5
# --batch 跳过安装确认；该镜像源与 docs/elasticsearch-setup.md 保持一致
RUN bin/elasticsearch-plugin install --batch \
    https://get.infini.cloud/elasticsearch/analysis-ik/9.4.5
```

内网构建拉不到外网时，手工下载 `elasticsearch-analysis-ik-9.4.5.zip` 放到 `deploy/es/`，改为：

```dockerfile
FROM docker.elastic.co/elasticsearch/elasticsearch:9.4.5
COPY elasticsearch-analysis-ik-9.4.5.zip /tmp/ik.zip
RUN bin/elasticsearch-plugin install --batch "file:///tmp/ik.zip" && rm /tmp/ik.zip
```

## 1.6 主编排文件：`deploy/docker-compose.yml`

```yaml
# Docker Compose v2（compose spec，无需 version 字段）
name: personal-blogger

services:
  mysql:
    image: mysql:9.7
    restart: unless-stopped
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_general_ci   # 与 Flyway 建表语句一致
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: csulzc_blogdb_prod
      MYSQL_USER: ${DB_USERNAME_PROD}
      MYSQL_PASSWORD: ${DB_PASSWORD_PROD}
    volumes:
      - mysqldata:/var/lib/mysql
    healthcheck:
      test: ["CMD-SHELL", "mysqladmin ping -h 127.0.0.1 -uroot -p\"$$MYSQL_ROOT_PASSWORD\" --silent"]
      interval: 10s
      timeout: 5s
      retries: 10
    networks: [blognet]
    # 仅本机排障时放开，不要对公网开放
    # ports: ["127.0.0.1:3306:3306"]

  redis:
    image: redis:7.4-alpine
    restart: unless-stopped
    command: >
      redis-server
      --requirepass ${REDIS_PASSWORD}
      --appendonly yes
      --appendfsync everysec
      --maxmemory 256mb
      --maxmemory-policy allkeys-lru
    volumes:
      - redisdata:/data
    healthcheck:
      test: ["CMD-SHELL", "redis-cli -a \"$$REDIS_PASSWORD\" --no-auth-warning ping | grep -q PONG"]
      interval: 10s
      timeout: 3s
      retries: 10
    networks: [blognet]
    # ports: ["127.0.0.1:6379:6379"]

  es:
    build:
      context: ./es
    image: blog-elasticsearch:9.4.5-ik
    restart: unless-stopped
    environment:
      discovery.type: single-node
      xpack.security.enabled: "false"        # 仅在内网 blognet 中可关闭；跨主机必须开认证
      ES_JAVA_OPTS: "-Xms512m -Xmx512m"      # 个人博客 512m 足够
    ulimits:
      memlock: { soft: -1, hard: -1 }
      nofile:  { soft: 65536, hard: 65536 }
    volumes:
      - esdata:/usr/share/elasticsearch/data
    healthcheck:
      test: ["CMD-SHELL", "curl -fsS 'http://localhost:9200/_cluster/health?wait_for_status=yellow&timeout=20s'"]
      interval: 15s
      timeout: 25s
      retries: 10
    networks: [blognet]
    # ports: ["127.0.0.1:9200:9200"]

  blog:
    build:
      context: ..                # 后端构建上下文是项目根（需要 pom.xml 与 src/）
      dockerfile: deploy/backend/Dockerfile
    image: blog-backend:latest
    restart: unless-stopped
    depends_on:
      mysql: { condition: service_healthy }
      redis: { condition: service_healthy }
      es:    { condition: service_healthy }
    environment:
      SPRING_PROFILES_ACTIVE: prod
      # —— 覆盖 application-prod.yml 中写死的 localhost + requireSSL 的整条 URL ——
      # 容器内网可信，useSSL=false；如强制 TLS 需自行配置 MySQL 证书，不能只改这个参数
      SPRING_DATASOURCE_URL: >-
        jdbc:mysql://mysql:3306/csulzc_blogdb_prod?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=utf8&connectTimeout=5000&socketTimeout=30000
      DB_USERNAME_PROD: ${DB_USERNAME_PROD}
      DB_PASSWORD_PROD: ${DB_PASSWORD_PROD}
      JWT_SECRET_PROD: ${JWT_SECRET_PROD}
      REDIS_HOST: redis
      REDIS_PORT: "6379"
      REDIS_PASSWORD: ${REDIS_PASSWORD}
      ELASTICSEARCH_URIS: http://es:9200
      ELASTICSEARCH_USERNAME: ${ELASTICSEARCH_USERNAME:-}
      ELASTICSEARCH_PASSWORD: ${ELASTICSEARCH_PASSWORD:-}
      # 首次部署/索引损坏时在 .env 置 true，日志确认回灌完成后改回 false 重启
      APP_SEARCH_ELASTICSEARCH_REINDEX_ON_STARTUP: ${REINDEX_ON_STARTUP:-false}
      JAVA_OPTS: "-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
    volumes:
      - uploads:/var/www/uploads    # 用户上传图片，删容器不丢
      - logs:/var/log/blog          # application.log + Tomcat access log
    healthcheck:
      test: ["CMD", "curl", "-fsS", "http://localhost:8080/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 10
      start_period: 60s             # 留给 Flyway 迁移 + ES 索引初始化
    networks: [blognet]

  web:
    build:
      context: ../frontend
      dockerfile: ../deploy/frontend/Dockerfile
    image: blog-frontend:latest
    restart: unless-stopped
    depends_on:
      blog: { condition: service_healthy }
    ports:
      - "80:80"
    networks: [blognet]

volumes:
  mysqldata:
  redisdata:
  esdata:
  uploads:
  logs:

networks:
  blognet:
    driver: bridge
```

关于这份编排的项目化说明：

- **MySQL 镜像 tag 与现有环境保持同一大版本**。若 `mysql:9.7` 在你的镜像源尚未提供，退用 `mysql:8.4`（LTS），`mysql-connector-j` 两者都兼容；不要在开发用 9.x、生产用 5.7 这种跨大版本组合。
- **Flyway 自动建表，不要手工导入表结构**。容器首次启动时 Flyway 执行 `V1`/`V2`/`V3` 迁移（`baseline-on-migrate=true`，对已存在的库也安全）。详见 [database-migration-guide.md](database-migration-guide.md)。
- **ES 索引也自动建**。`auto-create-index=true`，应用启动时发现 `articles` 索引缺失会自动按 classpath 里的 `articles-mapping.json`（含 IK 分词）创建。
- `/actuator/health` 已在 `SecurityConfig` 中 `permitAll`，健康检查无需鉴权；但注意 prod 下健康聚合包含 Redis 与 ES 指标，中间件宕机时容器会显示 `unhealthy`——即使应用本身已降级可用。
- 资源有限的机器建议给各服务加 `mem_limit`（如 MySQL 512m / ES 1g / blog 768m），ES 的堆内存不要超过容器限额的 50%。

## 1.7 启动与首次部署

在**装有 Docker Engine 24+ / Docker Desktop（含 Compose v2）**的机器上：

```bash
cd deploy
cp .env.example .env          # Windows: copy .env.example .env，然后编辑填真实密码
docker compose build          # 首次构建 5~15 分钟（Maven/pnpm 拉依赖）
docker compose up -d          # 按健康依赖顺序启动
docker compose ps             # 全部 healthy 才算就绪
docker compose logs -f blog   # 观察 Flyway 迁移与 ES 索引初始化日志
```

**数据库已有存量文章、首次接入 ES 时**：

1. 把 `.env` 里 `REINDEX_ON_STARTUP=true`；
2. `docker compose up -d blog`（重建/重启后端）；
3. 等到日志出现 `全量重建 Elasticsearch 索引完成，共 N 篇文章`；
4. 立刻改回 `false` 再 `docker compose up -d blog`，否则每次重启都全量删建索引。

全新部署（库是空的）不需要这一步，日常文章增删改由 `ArticleIndexEventListener` 在事务提交后自动同步。

## 1.8 验证清单

| # | 验证项 | 命令 / 地址 | 预期 |
|---|---|---|---|
| 1 | 前端页面 | `http://<服务器IP>/` | 首页正常，刷新子路由不 404 |
| 2 | 登录/文章接口 | 前端注册登录、浏览文章 | 正常（Nginx `/api` 反代通） |
| 3 | 健康检查 | `http://<服务器IP>/actuator/health` | `{"status":"UP"}` |
| 4 | Swagger | `http://<服务器IP>/swagger-ui.html` | 可打开（生产建议后续在 Nginx 层限制 IP） |
| 5 | ES 中文分词 | 见第三部分 3.3 | 正确切词 |
| 6 | 图片上传 | 前台写文章上传图片 | 图片写入 `uploads` 卷，重启容器后仍可访问 |
| 7 | 缓存 | `docker exec -it <redis容器> redis-cli -a $REDIS_PASSWORD keys 'category:*'` | 访问分类接口后出现缓存键 |

## 1.9 日常更新与回滚

```bash
# 更新后端：重新构建并滚动替换（卷与中间件不动）
cd deploy
docker compose build blog web
docker compose up -d blog web

# 看日志 / 进容器排查
docker compose logs --tail=200 blog
docker compose exec blog sh

# 备份数据库（与应用容器解耦，不依赖停机）
docker compose exec mysql mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" \
  --single-transaction csulzc_blogdb_prod > backup_$(date +%F).sql
```

回滚策略：镜像打版本号而不是永远用 `latest`（CI 发布时 `blog-backend:git-<短SHA>`），回滚即 `docker compose up -d blog` 指定旧 tag。数据库变更依赖 Flyway，回滚镜像前确认新版本没有执行过不可逆迁移。

## 1.10 Windows / Docker Desktop 注意事项

- 使用 **WSL2 后端**的 Docker Desktop；PowerShell 中 `docker compose` 命令与 Linux 完全一致。
- 本编排全部使用**命名卷**，不涉及 Windows 挂载路径换行符/权限问题；不要把 MySQL/ES 的数据目录改成 Windows 文件夹绑定挂载（IO 极慢且权限异常）。
- 仓库里的 shell 脚本若有 CRLF 问题，在 `.gitattributes`/`.editorconfig` 保证 `deploy/` 下脚本 LF，或容器里执行 `dos2unix`。
- WSL2 默认内存可能偏小（ES + MySQL + 后端同机时），在 `%UserProfile%\.wslconfig` 中调大后 `wsl --shutdown` 重启：

  ```ini
  [wsl2]
  memory=6GB
  swap=2GB
  ```

---

# 二、Redis 部署流程

## 2.1 先搞清本项目怎么用 Redis（决定了部署策略）

| 事实 | 来源 | 对部署的影响 |
|---|---|---|
| 客户端 Lettuce + commons-pool2 连接池 | `pom.xml` | 无需额外依赖；池参数已配置好 |
| 连接参数走 `REDIS_HOST/PORT/PASSWORD` 环境变量 | dev/prod yml | 容器里指向服务名 `redis` |
| 缓存总开关 `app.cache.enabled`：dev=`false`，prod=`true` | 两份 yml | **本地开发默认不启用缓存，不装 Redis 也能跑**（Lettuce 懒连接）；生产强制启用 |
| 默认 TTL 30 分钟；`category:list`/`category:tree` 1 小时；`category:stats` 10 分钟 | `RedisConfig.java` | Redis 是**纯缓存**，没有会话/分布式锁等不可丢数据 |
| 固定使用 `database: 0` | dev/prod yml | 单库够用，不需要多 database 隔离 |
| prod 连接池 max-active=20、超时 3s | `application-prod.yml` | Redis 要设合理 `maxclients`（默认 10000，远够） |
| dev 下 `management.health.redis.enabled=false` | `application-dev.yml` | 本地没起 Redis 时健康检查不会报 DOWN，属预期 |
| 值用 JSON 序列化并启用 default typing | `RedisConfig.java` | DTO 类结构变更后旧缓存可能反序列化失败——靠 TTL 自然过期即可，无需手工清库 |

结论：本项目的 Redis **允许丢数据**（丢了回源 MySQL 重建缓存），因此部署优先级是「加密码、限内存、LRU 淘汰」，而不是搞复杂的持久化/哨兵。单实例足够；哨兵/集群对个人博客属于过度设计。

## 2.2 推荐方式：Docker 独立容器

已包含在第一部分的 compose 中（服务名 `redis`）。如果只想单独跑一个 Redis 供本地/测试使用：

```bash
# Linux / macOS / Windows PowerShell（Docker Desktop）通用
docker run -d \
  --name blog-redis \
  --restart unless-stopped \
  -p 127.0.0.1:6379:6379 \
  -v blog-redis-data:/data \
  redis:7.4-alpine \
  redis-server \
    --requirepass "你的强密码" \
    --appendonly yes \
    --appendfsync everysec \
    --maxmemory 256mb \
    --maxmemory-policy allkeys-lru
```

要点解释（每条都对应本项目的用法，不是套话）：

- `-p 127.0.0.1:6379:6379`：只绑本机回环。生产 compose 内网通信用**不发布端口**；远程调试再临时放开，绝不能 `0.0.0.0` 无密码暴露公网（Redis 默认无认证，扫到即被写 SSH key 挖矿）。
- `--requirepass`：必须设置。应用侧通过 `REDIS_PASSWORD` 传入；密码为空时 prod 也能连上，但只要端口意外暴露就是灾难。
- `--maxmemory 256mb --maxmemory-policy allkeys-lru`：本项目缓存条目（分类树、文章列表 DTO）单条很小、总量可估，256mb 起步；满了按 LRU 淘汰。**不能用默认的 `noeviction`**——内存写满后写命令直接报错，会连累所有挂缓存的接口。
- `--appendonly yes / appendfsync everysec`：AOF 持久化，重启后缓存仍在，省去冷启动回源风暴；最坏丢 1 秒缓存，对纯缓存无实质影响。磁盘紧张也可以关掉（纯缓存本来就能重建）。
- 用 `redis:7.4-alpine` 与 README 标注的 Redis 7 对齐；不要上 Redis 8 预览版或用 Redis 5 老镜像。

想用配置文件方式管理，可挂载 `redis.conf` 并 `redis-server /usr/local/etc/redis/redis.conf`，关键配置等价于：

```conf
bind 0.0.0.0
protected-mode yes
port 6379
requirepass 你的强密码
appendonly yes
appendfsync everysec
maxmemory 256mb
maxmemory-policy allkeys-lru
slowlog-log-slower-than 10000
# 生产可选：禁用危险命令（本项目运维只用 keys/scan 排查，确认无脚本依赖后开启）
# rename-command FLUSHALL ""
# rename-command FLUSHDB ""
# rename-command CONFIG ""
```

> 非 Docker 部署（Linux 裸机，备选）：`apt install redis-server`（或官方仓库装 7.x）后改 `/etc/redis/redis.conf` 上表同样几项，`systemctl enable --now redis-server`。Windows 宿主机不建议原生安装 Redis（无官方 Windows 版），直接用 Docker。

## 2.3 应用侧接入确认

容器间通过 compose 网络，应用配置**不需要改 yml**，环境变量已在 compose 中注入：

```yaml
spring.data.redis.host=${REDIS_HOST}     # → redis（compose 服务名）
spring.data.redis.port=${REDIS_PORT}     # → 6379
spring.data.redis.password=${REDIS_PASSWORD}
spring.data.redis.database=0
```

本地开发（IDEA 直接跑后端）想用容器里的 Redis：

- dev 默认 `app.cache.enabled=false`，缓存注解不生效，**无需 Redis**；
- 想在本地验证缓存，设置环境变量 `REDIS_HOST=localhost`（容器发布了 127.0.0.1:6379）和 `REDIS_PASSWORD`，并加启动参数 `--app.cache.enabled=true` 临时打开即可，不要改 yml 文件。

## 2.4 验证

```bash
# 1) 连通性与认证（无密码会返回 NOAUTH）
docker exec -it <redis容器> redis-cli -a "你的密码" --no-auth-warning ping
# PONG

# 2) 内存策略是否生效
docker exec -it <redis容器> redis-cli -a "你的密码" --no-auth-warning CONFIG GET maxmemory-policy
# allkeys-lru

# 3) 业务验证（prod 下缓存是开的）：
#    调用几次 GET /api/categories/tree，再查键
docker exec -it <redis容器> redis-cli -a "你的密码" --no-auth-warning --scan --pattern 'category:*'
# 出现 category:tree 等键；TTL 应 ≤ 3600 秒
```

看到键即说明 `RedisConfig` 的 `RedisCacheManager`（String key + JSON value 序列化）工作正常。

## 2.5 运维与常见问题

| 现象 | 原因与处理 |
|---|---|
| `NOAUTH Authentication required` | 应用没配 `REDIS_PASSWORD` 或密码与 `requirepass` 不一致；compose 中两处都引用同一个变量即可 |
| 应用日志 `Unable to connect to localhost:6379` | 容器里写了 localhost（指向容器自身）；host 必须是 compose 服务名 `redis` |
| `WRONGTYPE` / 反序列化 `ClassCastException` | 序列化结构或 DTO 变更后的历史键，等 TTL 过期，或对该缓存名执行 `DEL category:tree`（整库可 `FLUSHDB`，纯缓存无副作用） |
| `OOM command not allowed` | 命中 maxmemory 且策略为 noeviction；改为 `allkeys-lru` 并调大 maxmemory |
| Redis 内存涨满 | 先 `INFO memory` 看用量，`--bigkeys`（redis-cli）排查异常大 key；本项目正常使用 256mb 足够 |
| 版本升级（7.x 小版本） | 标签改新版直接重建容器，AOF 在卷里；跨大版本前先用备份卷启动一个测试容器验证 AOF 兼容 |
| 缓存三防（穿透/击穿/雪崩） | README「已知问题」中已列规划（空值缓存/逻辑过期/TTL 随机化）；当前固定 TTL，大促式重启场景可错峰重启后端 |

---

# 三、Elasticsearch 部署流程

## 3.1 版本铁律（本项目最容易踩的坑）

```
Spring Boot 4.1.1 BOM
   └─ elasticsearch-java 客户端 9.4.5
        └─ ES 服务端必须用 9.4.5
             └─ IK 插件必须用 9.4.5（逐位一致）
```

- 客户端 9.4.5 连 ES 8.x 或其他 9.x 小版本都可能因 API 差异报错，不要图省事用 `elasticsearch:latest`。
- IK 插件版本不存在「向下兼容」，版本不符 ES 启动直接失败。
- 本项目只需要搜索能力，**不要部署 Kibana/Logstash**，个人博客那台机器扛不住也用不上。

## 3.2 容器部署（单节点）

镜像定义见 1.5（`blog-elasticsearch:9.4.5-ik`），编排见 1.6 的 `es` 服务。单机关键点复述：

```yaml
environment:
  discovery.type: single-node        # 单节点模式，不寻找集群
  xpack.security.enabled: "false"    # 内网 blognet 内关闭认证；跨网络主机必须开启（见 3.5）
  ES_JAVA_OPTS: "-Xms512m -Xmx512m"  # Xms=Xmx，避免运行时扩容抖动
ulimits:
  memlock: { soft: -1, hard: -1 }    # 允许锁内存（配合官方镜像的 bootstrap 检查）
volumes:
  - esdata:/usr/share/elasticsearch/data   # 索引数据持久化，不能放 Windows 绑定挂载
```

- **Linux 宿主机**必须先调虚拟内存上限，否则 ES 拒绝启动：

  ```bash
  # 临时生效
  sudo sysctl -w vm.max_map_count=262144
  # 永久生效
  echo 'vm.max_map_count=262144' | sudo tee /etc/sysctl.d/99-elasticsearch.conf
  sudo sysctl -p
  ```

  Docker Desktop（WSL2）默认已调好该参数；若仍报错，调大 `.wslconfig` 内存（见 1.10）后 `wsl --shutdown`。
- 堆内存从 512m 起步（个人博客索引通常几十 MB），上限给到物理内存的一半且不超过 31GB；同时调大容器内存限额，堆外 lucene 还要吃等量内存。
- 磁盘小的 VPS 建议调水位线（默认 95% 触达后索引变只读），在 es 服务 `environment` 加：

  ```yaml
  cluster.routing.allocation.disk.watermark.low: "85%"
  cluster.routing.allocation.disk.watermark.high: "90%"
  cluster.routing.allocation.disk.watermark.flood_stage: "95%"
  ```

单独运行（不走 compose）的等价命令：

```bash
docker build -t blog-elasticsearch:9.4.5-ik deploy/es

docker run -d --name blog-es \
  --restart unless-stopped \
  -p 127.0.0.1:9200:9200 \
  -e discovery.type=single-node \
  -e xpack.security.enabled=false \
  -e "ES_JAVA_OPTS=-Xms512m -Xmx512m" \
  -v blog-es-data:/usr/share/elasticsearch/data \
  blog-elasticsearch:9.4.5-ik
```

非 Docker 的裸机部署（下载发行包 + `bin/elasticsearch-plugin install IK`）见已有的 [elasticsearch-setup.md](elasticsearch-setup.md)，本文不重复。

## 3.3 启动验证

```bash
# 1) 版本确认：version.number 必须是 9.4.5
curl http://localhost:9200/
# "number" : "9.4.5"

# 2) IK 分词验证（没装 IK 或版本不符会报 analyzer [ik_smart] not found）
curl "http://localhost:9200/_analyze" -H "Content-Type: application/json" \
  -d '{"text":"我是中国人","analyzer":"ik_smart"}'
# tokens 应切分为 我 / 是 / 中国人（ik_smart 粗粒度）

# 3) 集群状态：单节点 yellow 是正常的（有副本但无第二节点可分配），不是故障
curl http://localhost:9200/_cluster/health?pretty
# "status" : "yellow", "number_of_nodes" : 1
```

应用首次启动后（`auto-create-index=true`），确认索引 mapping 中的中文分词器已生效：

```bash
curl http://localhost:9200/articles/_mapping?pretty
# title / summary / content 三个字段应显示 "analyzer": "ik_max_word", "search_analyzer": "ik_smart"
```

mapping 来源是打包进 jar 的 `src/main/resources/elasticsearch/articles-mapping.json`，无需手工 `PUT /articles`。

## 3.4 与应用联调（首次部署 / 数据修复）

应用侧配置全部走环境变量，prod yml 已留占位符：

| 环境变量 | 本项目取值 | 说明 |
|---|---|---|
| `ELASTICSEARCH_URIS` | `http://es:9200` | 容器内用服务名；裸机本地默认 `http://localhost:9200` |
| `ELASTICSEARCH_USERNAME/PASSWORD` | 空（内网关认证时） | 开 X-Pack 后必填 |
| `APP_SEARCH_ELASTICSEARCH_REINDEX_ON_STARTUP` | 首次 `true`，之后 `false` | 全量删建索引 + 从 MySQL 回灌文章 |

标准流程：

1. ES 先于应用启动（compose 的 `depends_on: service_healthy` 已保证顺序）；
2. 全新空库：直接启动，索引自动创建，之后文章增删改由事务事件（`AFTER_COMMIT`）自动同步，无需任何手工操作；
3. **存量库首次接入 ES 或索引数据不一致**：`.env` 设 `REINDEX_ON_STARTUP=true` 重启 blog 容器，日志出现 `全量重建 Elasticsearch 索引完成，共 N 篇文章` 后改回 `false` 再重启；
4. 搜索接口本身不变：`GET /api/articles/search?keyword=xxx`，命中时返回 `highlightTitle/highlightSummary`。

降级与熔断是本项目已实现的行为，运维时要能识别日志：

- ES 没起来 / 查询异常 → 搜索自动降级 MySQL `LIKE`（仅标题匹配），日志 `Elasticsearch 搜索失败，降级为数据库 LIKE 搜索`，**应用照常启动、接口不报错**；
- 写索引失败只记日志，不影响发文主流程；ES 恢复后增量缺口通过一次全量重建补齐；
- 因此 ES 容器可以在不重启应用的情况下重启，恢复后下一次搜索自动复位。

## 3.5 可选：开启 X-Pack 安全认证

ES 端口需要跨主机/跨网段访问时，不能裸奔。在 es 服务加：

```yaml
environment:
  xpack.security.enabled: "true"
  xpack.security.transport.ssl.enabled: "true"
  # 单节点 HTTP 可先不强制 TLS；公网环境建议同时启用 http.ssl
```

进入容器设置内置用户口令：

```bash
docker compose exec es bin/elasticsearch-setup-passwords auto
```

拿到 `elastic` 用户密码后填入 `deploy/.env`：

```dotenv
ELASTICSEARCH_USERNAME=elastic
ELASTICSEARCH_PASSWORD=上一步生成的密码
```

此时 URI 也要改成 `https://es:9200`（并处理自签证书信任），compose 中更新 `ELASTICSEARCH_URIS` 即可，应用侧 `application-prod.yml` 已支持这三个变量，无需改代码/重打包。

## 3.6 常见问题速查

| 现象 | 原因 / 处理 |
|---|---|
| ES 容器启动几秒钟后退出，日志含 `max virtual memory areas vm.max_map_count` | Linux 宿主机未调 `vm.max_map_count=262144`（见 3.2） |
| 日志含 `Plugin [analysis-ik] was built for Elasticsearch version 9.4.5` 之外的版本 | IK 与 ES 版本不一致；用 1.5 的镜像构建方式锁死同版本 |
| `analyzer [ik_max_word] has not been configured` | 索引在装 IK 之前就被创建了；设 `REINDEX_ON_STARTUP=true` 重建索引 |
| 集群状态一直 yellow | 单节点正常现象；追求 green 可把 articles 索引副本设为 0：`PUT /articles/_settings {"number_of_replicas":0}` |
| 写入报 `cluster_block_exception ... read-only-allow-delete` | 触发磁盘洪水线（95%）；清磁盘后执行 `PUT /_all/_settings {"index.blocks.read_only_allow_delete":null}` 解除 |
| 应用日志反复降级 LIKE、ES 没报错 | 先 `curl es:9200` 从应用容器内测网络；再确认 `ELASTICSEARCH_URIS` 用的是服务名而不是 localhost |
| Windows 上 ES 容器 OOM 被 kill | WSL2 内存不足，调 `.wslconfig` 的 `memory`，或把 `ES_JAVA_OPTS` 降到 `-Xms256m -Xmx256m` |
| 索引占用越来越大 | 正常（段合并滞后 + 删除标记）；低峰执行 `POST /articles/_forcemerge?max_num_segments=1` |

---

## 附：部署完成后的最小检查顺序

1. `docker compose ps`：五个容器全部 `running/healthy`；
2. `curl http://<IP>/actuator/health` → `UP`；
3. 前端注册、登录、发文、上传图片各走一遍；
4. `GET /api/articles/search?keyword=测试` 返回高亮结果（验证 ES + IK）；
5. `redis-cli ... --scan --pattern 'category:*'` 有键且带 TTL（验证缓存）；
6. 确认 `.env` 未进 git、80 以外的端口未对公网开放、`REINDEX_ON_STARTUP` 已回到 `false`。
