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