# 行吟博客 — 变更日志

本项目所有重要变更都会记录在此文件中，格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，并遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

---

## [1.0.0] - 2026-11-02

首次正式发布。经过约 8 个月的开发与迭代，前后端完整功能齐备，支持生产环境部署。

### ✨ 新增功能

**后端核心**
- 用户注册 / 登录 / 刷新 Token（JWT 无状态认证，JJWT 0.12.3）
- 三级角色体系：`USER`、`ADMIN`、`SUPER_ADMIN`，注册接口禁止创建超管
- 基于 Origin 的请求来源检测，前端请求强制普通用户权限
- 仅凭 Token 的 `/me` 个人中心：查询 / 修改信息 / 修改密码
- 用户启停、锁定 / 解锁、软删除等管理能力
- 密码 BCrypt 加密 + 复杂度校验（PasswordValidator）

**内容管理**
- 文章 CRUD、发布 / 归档、批量操作、点赞、浏览计数
- 分类无限层级树（顶级分类 + 子分类查询），分类归属创建者
- 评论发表、回复、审核、批量删除 / 批量审核
- Markdown 渲染（CommonMark + GFM 表格扩展）

**系统能力**
- Spring Cache + Redis 缓存（可开关 `app.cache.enabled`）
- 业务逻辑与中间件解耦合，Redis/ES 故障时自动降级
- Redis 缓存雪崩防护（TTL 抖动）、缓存击穿防护（互斥写入）、缓存穿透防护（空值缓存 + Guard AOP）
- ElasticSearch 9.4.5 全文检索（IK 中文分词）
- 文件上传（本地磁盘）+ Base64 互转接口，类型白名单校验（jpeg/png/gif/webp，≤5MB）
- AOP 统一请求日志 + SQL 参数绑定打印 + 敏感字段脱敏
- Flyway 数据库迁移（5 个版本），HikariCP 连接池调优（生产 20 连接 + 泄漏检测）
- JWT 黑名单（登出后旧 Token 立即失效）
- 登录失败次数限制（防止暴力破解）
- 邮箱验证码重置密码（SMTP 可配置）
- OpenAPI / Swagger UI 文档、Actuator + Prometheus 监控
- 开发 / 生产双环境配置隔离（`application-dev.yml` / `application-prod.yml`），测试环境使用 H2

**前端**
- React 19 + TypeScript 5.7 + Vite 6 单页应用
- React Router 7 路由，Ant Design 5 UI 组件库
- 用户认证、文章列表 / 详情 / 编辑、分类浏览、评论交互
- 个人中心、管理后台 Dashboard + 用户 / 文章 / 分类 / 评论管理
- Markdown 预览（react-markdown + remark-gfm）
- Tailwind CSS 4 + Ant Design v5-patch-for-react-19

### 🔧 工程化

- Spring Boot 4.1.1（Spring Boot Starter Parent 4.1.1）
- Spring Security 6、Spring Data JPA、Spring Data Redis、Spring Data Elasticsearch
- Docker Compose 完整容器化：MySQL 9.7 + Redis 7.4 + ElasticSearch 9.4.5 + Spring Boot + Nginx
- Spring Boot 瘦 Jar 多阶段 Dockerfile（利用 `jarmode=tools`）
- GitHub Actions CI：后端增量测试（基于 git diff 智能映射）+ 前端 typecheck & build
- Maven Surefire 配置 JDK 21 attach self（解决 Mockito inline mock maker 问题）
- OpenAPI Maven Plugin 自动从运行中的应用生成 `openapi.yaml`

### 🧪 测试覆盖

- 48+ 测试类，分布于 Entity / Repository / Service / Controller / Security 五层
- 端到端 API 集成测试 `ApiIntegrationTest`（32 用例，`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`，走真实安全过滤器链）
- 测试环境使用 H2（`MODE=MySQL`）+ 独立配置，不依赖外部 MySQL / Redis
- 前端单元测试：Vitest + Testing Library（jsdom），覆盖工具函数、请求层（Token 与 401 刷新）、API 客户端、登录态上下文与核心组件
- 本地环境压测脚本（k6 + Python），含主场景 / 雪崩 / 击穿 / 穿透 / 搜索五种场景

### 🐛 已修复的重要问题

- 慢 SQL 全量排查与优化（Dashboard 统计、弃用 schema）
- N+1 查询、全量加载、内存过滤、批量删除性能问题
- 分类归属澄清：分类为用户个人资产，文章创建不再强制要求分类
- Docker 容器内邮件发送失败（SMTP 连接问题）
- 文件存储从硬编码本地磁盘抽象为 StorageService 接口
- Spring Boot 4.1.1 大版本迁移兼容（TestRestTemplate 位置变化、layertools 移除等）
- 前端文章发布字数统计错误、图片上传失败

### ⚠️ 已知限制

- 文件存储当前仅支持本地磁盘，接口已抽象为 `StorageService`，预留对象存储（OSS/S3）切换扩展
- 前端单元测试已覆盖工具函数、请求层、API 客户端、登录态与核心组件，但页面级集成测试尚未接入
- 邮件验证码功能需配置 SMTP 环境变量才可使用，未配置时静默跳过
- Admin 管理接口暂未在 CI 中覆盖端到端测试

### 🛠 技术栈一览

| 类别 | 技术 |
|------|------|
| 语言 / 框架 | Java 21、Spring Boot 4.1.1、React 19、TypeScript 5.7 |
| 持久层 | Spring Data JPA (Hibernate 6)、Flyway |
| 数据库 | MySQL 9.7（dev/prod）、H2（测试）、Redis 7.4（缓存）、ElasticSearch 9.4.5（搜索） |
| 安全 | Spring Security、JJWT 0.12.3、BCrypt |
| 中间件 | HikariCP、Actuator + Prometheus |
| 接口文档 | springdoc-openapi 3.1.1 |
| 前端 | Ant Design 5、React Router 7、Vite 6、Tailwind CSS 4 |
| 构建 | Maven 3.9、npm（随 Node 22 内置）、Docker Compose |
| 测试 | JUnit 5、Mockito、AssertJ、Spring Boot Test、Vitest、Testing Library、k6 |

---

[1.0.0]: https://github.com/linzichengcsu/My-Personal-Blogger/releases/tag/v1.0.0

## [1.0.1] - 2026-11-03

### 🐛 已修复的重要问题
修复了项目`docker compose build`因 pnpm 到 npm 的迁移未能及时同步到`docker-compose.yml`导致的构建失败的问题

## [1.0.2] - 2026-11-05

### 🐛 已修复的重要问题
- 修复了前端文章内容输入框点单词回车会吞掉换行符的问题
- 修复了缓存驱逐策略错误导致的前端已被删除或归档的文章错误地显示在主页的问题

### 📖 新增文档
- 增加了适合非技术背景用户快速一键启动的说明文档`QUICK_START_FOR_BEGINNERS.md`，位于根目录下

## [1.0.3] - 2026-11-08

### ✨ 新增功能

- Docker Compose 新增 MailHog 本地 SMTP 服务（SMTP 端口 1025、Web 收件箱 8025，仅绑定 127.0.0.1），开发与演示环境无需真实邮箱即可联调验证码邮件
- 邮件发件人支持配置友好显示名：新增环境变量 `SMTP_FROM_NAME` / 配置项 `app.smtp.from-name`（默认"行吟博客"），收件箱展示更清晰

### 🔧 优化变更

- `.env.example` 与 `deploy/.env.example` 补充完整的 SMTP 配置段，默认示例切换为 QQ 邮箱真实投递（`smtp.qq.com:465` SSL），并附授权码获取方式与「SMTP_FROM 必须与 SMTP_USERNAME 一致」等关键配置注释
- Docker Compose 中 blog 服务增加 2GB 内存上限，避免容器无限制占用宿主机内存
- `QUICK_START_FOR_BEGINNERS.md` 终端启动说明补充 macOS Terminal（原仅提及 Windows PowerShell）

### 🐛 已修复的重要问题

- 统一时间管理逻辑：实体层时间字段改用 `java.time.Instant` 并按 UTC 存储，DTO 层通过新增的 `DateTimeUtils` 工具类统一格式化，配合 Hibernate `time_zone: UTC`，彻底消除前后端时区歧义
- 启用 `application-dev.yml` 与 `application-prod.yml` 中此前被注释的 `app.smtp` 配置段，使 `SMTP_*` 环境变量能正确绑定到邮件服务属性，修复配置注入不生效的问题
- 邮件 From 头对发件人显示名按 RFC 2047 进行 Base64 编码，避免非 ASCII 字符或空格引发 SMTP 地址解析异常（如 "Domain contains illegal character"），降低邮件被拒收或进入垃圾箱的概率

## [1.0.4] - 2026-11-15

### ✨ 新增功能

- 新增一键并发/性能测试脚本 `scripts/concurrent-test/run.cmd`（PowerShell 5+），支持 `-All` / `-MysqlOnly` / `-Perf` / `-TestPattern` 选择测试范围，并可通过 `-Scale` / `-Threads` / `-Profile`（smoke / default / high / extreme）在不改源码的前提下调整压测压力
- 新增 Docker 环境一体化压测脚本 `scripts/loadtest-docker/run.cmd`：自动拉起隔离的压测栈（`deploy/docker-compose.loadtest.yml`）→ 等待健康检查 → 运行容器化 k6 → 输出汇总 → 可选拆栈，宿主机无需安装 k6
- 新增基于 Testcontainers 的并发正确性测试 `ConcurrentCorrectnessTest` / `MysqlConcurrentCorrectnessTest`，用真实 MySQL 复现生产语义（`INSERT IGNORE` 静默去重、InnoDB 行级锁），Docker 不可用时自动跳过，默认 `mvn test` 不依赖 Docker
- 新增 `StressParams` 统一压测参数类，压测线程数、循环次数、批量大小等可通过 `-Dstress.*` 系统属性覆盖

### 🔧 优化变更

- **收藏夹（Category）扁平化（不兼容变更）**：移除父子层级，所有收藏夹平级；删除 `GET /api/categories/top-level`、`/tree`、`/my/tree`、`/{categoryId}/subcategories`、`/{categoryId}/path`、`/{categoryId}/article-count`、`/stats/top-level` 接口及 `CategoryTreeDTO`；`CategoryDTO` / `CategoryRequest` 移除 `parentCategoryId`、`parentCategoryName`、`subCategories` 字段；新增 Flyway 迁移 `V6__flatten_categories.sql` 删除 `categories.parent_category_id` 列及其外键
- 同步精简收藏夹服务与仓储层：删除树构建、路径查询、子分类统计、顶级分类查询等冗余逻辑，收藏夹缓存移除 `category:tree`，只保留 `category:list`（1 小时）/ `category:detail`（30 分钟）/ `category:stats`（10 分钟）
- JWT 认证改为按角色继承构建权限（`SUPER_ADMIN` ⊃ `ADMIN` ⊃ `USER`），使 `@PreAuthorize("hasRole('ADMIN')")` 对超级管理员同样生效，与 `SecurityContextUtil.isAdmin()` 语义保持一致
- `loadtest` profile 下放开 `GET /api/categories/**` 匿名访问，便于压测收藏夹只读接口的缓存命中率（生产环境仍需鉴权）
- 前端收藏夹管理页与写文章页改为平级结构：移除树形选择器（`TreeSelect`）与树拍平逻辑，直接使用收藏夹列表作为下拉/多选选项
- 测试环境 H2 会话锁超时提升至 60s（`SET LOCK_TIMEOUT 60000`），避免高并发压测时队尾线程抛 `LockTimeoutException` 造成结果失真

### 🐛 已修复的重要问题

- 修复公开的作者文章列表接口 `GET /api/articles/author/{authorId}` 对匿名访客返回 403 的问题：改用不校验归属权的 `getUserProfile`，避免触发 `validateOwnershipOrAdmin`
- 修复分类树逻辑与收藏夹实际需求不一致导致的服务层臃肿、缓存键冗余与查询开销问题（随收藏夹扁平化一并移除）

### 📖 新增文档

- 新增 `PERF_TEST_LOG.md`（由 `CACHE_TEST_LOG.md` 重命名并扩充），补充雪崩 / 穿透 / 击穿专项测试与并发正确性测试的指令与结果记录
- 更新 `docs/docker-redis-elasticsearch-deployment.md` 中的缓存键说明与故障排查示例（`category:tree` → `category:list`）

## [1.1.0] - 2026-11-26

### ✨ 新增功能

**标签系统**
- 新增文章标签（Tag）功能：标签 CRUD、文章多标签关联，后端 `TagController` + `TagService` + `TagRepository` + `ArticleTagRepository`，DTO 含 `TagDTO` / `TagRequest`
- 前端 Tags API、类型定义、写文章页标签选择器、文章详情页标签展示、管理后台文章标签管理
- Flyway 迁移 `V8__create_tags.sql` 新建 `tags` 与 `article_tags` 关联表
- `GET /api/tags/**` 放行匿名访问，其余操作需鉴权

**通知系统**
- 新增站内通知（Notification）功能：评论发表/回复时自动通知作者，后端基于 Spring Event 机制（`NotificationTriggeredEvent` + `NotificationEventListener`）
- `NotificationController` 提供未读数查询、通知列表、标记已读等接口，DTO 含 `NotificationDTO` / `NotificationCountDTO`
- 前端 `NotificationBell` 铃铛组件、实时未读计数、通知下拉面板
- Flyway 迁移 `V7__create_notifications.sql` 新建 `notifications` 表

**对象存储（MinIO）**
- 文件存储从硬编码本地磁盘扩展为可切换的对象存储：新增 `MinioStorageService`（实现 `StorageService` 接口），默认仍用 `local`
- 新增 MinIO Java SDK 8.5.17 依赖、`MinioConfig` 配置类
- Docker Compose 内置 MinIO 服务（`minio:latest`，API 9000 + Console 9001），blog 服务 `depends_on` 自动等待 MinIO 就绪
- 通过 `FILE_TYPE=minio` 环境变量或 `app.file.type=minio` 配置即可切换，无需改代码

**前端状态管理**
- 引入 [Zustand](https://github.com/pmndrs/zustand) 作为全局状态管理，替代散落在各组件中的 `useState` / Context 模式
- 按业务拆分为 `authStore`（登录态与用户信息）、`articleStore`（文章列表与筛选）、`notificationStore`（通知未读与列表），入口统一从 `stores/index.ts` 导出
- 重构 `App.tsx`、`SiteLayout`、`NotificationBell`、`HomePage`、`ProfilePage`、`ArticleDetailPage`、`AuthPage` 等组件改用 Store，消除 Props 层层透传

### 🔧 优化变更

- docker-compose.yml 新增 MinIO 服务（`miniodata` volume、127.0.0.1:9001 Console 端口），blog 服务容器内环境变量自动读取 MinIO 连接参数
- `.env.example` 与 `deploy/.env.example` 补充完整 MinIO 配置段（含默认值与安全注释），生产务必覆盖 `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD`
- `application-dev.yml` 与 `application-prod.yml` 新增 `app.file.type=local` 默认值，与切换开关语义对齐
- `SecurityConfig` 为 `/api/tags/**` 增加与 categories 同等的放行规则（`GET` 公开，其余需鉴权）

### 🐛 已修复的重要问题

- 修复 Dependabot 扫描到的 7 个前端依赖漏洞：升级若干 npm 包版本（涉及 `package-lock.json` 刷新，净减 656 行冗余依赖树）

### 📖 新增文档

- `.env.example` / `deploy/.env.example` 补充 MinIO 对象存储配置说明与默认值注释
