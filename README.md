# My-Personal-Blogger 📝

[![JDK](https://img.shields.io/badge/JDK-23-orange)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-9.7-%23007396)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-7.0-%23ea527f)](https://redis.io/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

个人博客系统后端服务 —— 基于 **Spring Boot 3** 构建的 RESTful API 项目，提供用户认证、文章、分类、评论、文件上传、管理后台等完整博客能力。

> **当前进度**：后端开发完成，开发环境所有用户业务接口测试通过。

---

## 📑 目录

- [功能特性](#功能特性)
- [技术栈](#技术栈)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [API 文档](#api-文档)
- [测试](#测试)
- [项目结构](#项目结构)
- [生产部署要点](#生产部署要点)
- [开发日志](#开发日志)
- [已知问题及解决规划](#已知问题及解决规划)
- [License](#license)
- [鸣谢](#鸣谢)

---

## ✨ 功能特性

**用户与权限**

- [x] 注册 / 登录 / 刷新 Token（JWT 无状态认证）
- [x] 三级角色体系：`USER`、`ADMIN`、`SUPER_ADMIN`，注册接口禁止创建超管
- [x] 基于 Origin 的请求来源检测：前端请求强制普通用户权限
- [x] 仅凭 Token 的 `/me` 个人中心：查询 / 修改信息 / 修改密码
- [x] 用户启停、锁定 / 解锁、软删除等管理能力
- [x] 密码 BCrypt 加密 + 复杂度校验

**内容管理**

- [x] 文章 CRUD、发布 / 归档、批量操作、点赞、浏览计数
- [x] 分类无限层级树（顶级分类 + 子分类查询）
- [x] 评论发表、回复、审核、批量删除 / 批量审核
- [x] Markdown 渲染（CommonMark + GFM 表格扩展）

**系统能力**

- [x] Spring Cache + Redis 缓存，可开关（`app.cache.enabled`）
- [x] 文件上传（本地磁盘存储）+ Base64 互转接口，类型白名单校验
- [x] AOP 统一请求日志 + SQL 参数绑定打印 + 敏感字段脱敏
- [x] Flyway 数据库迁移（生产环境）、HikariCP 连接池调优
- [x] OpenAPI / Swagger UI 文档、Actuator + Prometheus 监控
- [x] 开发 / 生产双环境配置隔离，测试环境使用 H2

---

## 🛠 技术栈

| 类别 | 技术                                           |
|---|----------------------------------------------|
| 语言 / 框架 | Java 23、Spring Boot 3.2.5                    |
| 持久层 | Spring Data JPA (Hibernate 6)、Flyway         |
| 数据库 | MySQL 9.7（dev/prod）、H2（测试）、Redis（缓存）         |
| 安全 | Spring Security、JJWT 0.12.3、BCrypt           |
| 接口文档 | springdoc-openapi 2.5.0（Swagger UI）          |
| 前端模板 | Thymeleaf + thymeleaf-extras-springsecurity6 |
| 其他 | Lombok、AOP、Actuator、CommonMark、commons-lang3 |

---

## 🚀 快速开始

### 前置要求

| 依赖 | 版本 |
|---|---|
| JDK | 23 |
| Maven | 3.8+ |
| MySQL | 8.0+ |
| Redis | 6.0+（Docker 亦可） |

### 1. 克隆项目
```bash
git clone https://github.com/linzichengcsu/My-Personal-Blogger.git
cd My-Personal-Blogger
```
### 2. 创建数据库
```sql
CREATE DATABASE csulzc_blogdb_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

### 3.配置环境变量（推荐）
开发环境默认配置在 `src/main/resources/application-dev.yml` 中，可优先使用环境变量覆盖：
```bash
export SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/csulzc_blogdb_dev?useSSL=false&serverTimezone=UTC
export SPRING_DATASOURCE_USERNAME=root
export SPRING_DATASOURCE_PASSWORD=123456
export SPRING_REDIS_HOST=localhost
export SPRING_REDIS_PORT=6379
```

### 4. 运行项目
```bash
mvn spring-boot:run
```

### 4. 验证

| 地址 | 说明                     |
|---|------------------------|
| `http://localhost:8080/swagger-ui.html` | Swagger UI             |
| `http://localhost:8080/v3/api-docs` | OpenAPI JSON           |
| `http://localhost:8080/actuator/health` | 健康检查                   |
| `openapi.yaml` | 项目根目录离线 API 文档（将会定期同步） |

---

## ⚙️ 配置说明

### 双环境对比

| 项目 | dev | prod |
|---|---|---|
| 数据库 | `csulzc_blogdb_dev`，`ddl-auto: update` | `csulzc_blogdb_prod`，`ddl-auto: validate` |
| Flyway | 关闭 | 开启（`baseline-on-migrate: true`） |
| SQL 日志 | 打印 + 参数绑定 | 关闭 |
| 连接池 | 默认 | HikariCP 20 连接 + 泄漏检测 |
| JWT 有效期 | 24h / 刷新 7d | 1h / 刷新 1d |
| 缓存 | Spring Cache + Redis | 同左 + Hibernate 二级缓存 |
| 监控 | — | Actuator + Prometheus |
| 文件目录 | `./uploads` | `/var/www/uploads` |

> 注意：
> - 生产环境请务必修改 `application-prod.yml` 中的数据库账号密码，并开启 Flyway 迁移。
> - 测试环境默认使用 H2 数据库。
> - 请勿使用真实姓名、电话、地址等作为测试数据，可使用随机数据生成工具生成，或“宋江”“卢俊义”等熟知的虚构人物。
> - 开发环境默认使用 MySQL，可修改 `application-dev.yml` 中的数据库配置。
> - 请务必确认`./uploads`目录存在且具有写权限。
> - 敏感字段请勿明文存储，可在开发环境下创建.env文件并将其加入.gitignore中。
> - 生产和开发环境中禁止使用默认账号密码。

### 核心环境变量

| 变量 | 说明 | 默认值 |
|---|---|---|
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis 连接 | localhost / 6379 / 空 |
| `JWT_SECRET` | JWT 签名密钥（≥32 字符） | dev 内置占位值 |
| `DB_USERNAME` / `DB_PASSWORD` | 生产库账号 | root / change-me-in-production |

---

## 📖 API 文档

完整接口定义见项目根目录 [openapi.yaml](openapi.yaml)，主要模块：

| 模块 | 前缀 | 说明 |
|---|---|---|
| 用户 | `/api/users` | 注册、登录、刷新、`/me` 个人中心、用户管理（部分 ADMIN） |
| 文章 | `/api/articles` | CRUD、发布 / 归档、搜索、批量操作、点赞 / 浏览 |
| 分类 | `/api/categories` | 分类 CRUD、树查询 |
| 评论 | `/api/comments` | 评论 / 回复、审核、批量操作 |
| 文件 | `/api/files` | 上传、Base64 转换 |
| 管理端 | `/api/admin` | 看板统计（ADMIN） |

统一响应结构为 `Result<T>`（`api.response.Result`），错误由全局异常处理器统一转换为标准格式。

---

## 🧪 测试

项目已覆盖Entity、Repository、Service、Controller、Security五层测试，并新增**接口自动化集成测试**：
- 测试环境使用 H2（`MODE=MySQL`）+ 独立配置 `application-test.properties`
- 测试代码位于 `src/test/java/csulzc/My_Personal_Blogger/`

### 接口自动化测试（端到端 HTTP 层）

`ApiIntegrationTest`（`src/test/java/csulzc/My_Personal_Blogger/ApiIntegrationTest.java`）基于
`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`，走真实的安全过滤器链（JWT 鉴权），
覆盖用户注册/登录/刷新令牌、管理员权限、分类/文章/评论 CRUD 等核心业务流程，且已针对鉴权与业务规则做状态断言。

运行方式（`test` Profile，使用 H2，无需本地 MySQL / Redis）：

```bash
mvn -Dtest=ApiIntegrationTest test
```

接入 CI：任意 Maven 项目/流水线执行上述命令即可；也可按需拆分到 `integration-test` 阶段，或扩展为按模块的多个测试类。
运行前提与项目一致：JDK 23 + Maven。

---

## 📂 项目结构
src/main/java/csulzc/My_Personal_Blogger\
├── api/ # DTO（article/category/comment/user/dashboard）+ 统一响应与全局异常\
├── aspect/ # AOP 请求日志切面（含敏感脱敏） \
├── config/ # 安全、CORS、Redis、JWT、OpenAPI、文件存储、管理员初始化\
├── controller/ # REST 控制器（users/articles/categories/comments/files/admin） \
├── domain/entity/ # JPA 实体（User/Article/Category/Comment/Admin + BaseEntity）\
├── repository/ # Spring Data JPA 仓储层 \
├── security/ # JWT 过滤器与令牌、密码校验、来源解析、安全上下文工具 \
└── service/ # 业务逻辑层

## 🚢 生产部署要点

1. **数据库**：先由 Flyway 执行 `V1__create_production_schema.sql` 和 `V2__create_production_indexes.sql` 迁移（生产索引）。
2. **密钥**：务必通过 `JWT_SECRET` 注入强密钥，长度 ≥ 32 字符。
3. **TLS**：prod 数据源已启用 SSL 校验，请确保 MySQL 开启 SSL。
4. **日志**：自动滚动写入 `/var/log/blog/application.log`，Tomcat 访问日志同目录。
5. **监控**：`/actuator/prometheus` 可直接对接 Prometheus + Grafana。
6. **资源目录**：确认 `/var/www/uploads` 存在且应用有写权限。

## 📅 开发日志

- 3月2日：项目创建
- 3月8日：Entity 层完成并测试通过
- 3月16日：Repository 层完成并测试通过
- 3月21日：DTO 模块完成
- 3月23日：引入 Qoder AI 辅助编程
- 3月28日：Service 层初步编码完成
- 4月5日：Service 层全部测试通过
- 4月19日：RESTful API 与计算机网络基础学习、Controller 层开发完成并测试通过
- 4月29日：部署 MySQL、JWT、OpenAPI 组件，引入文件系统，后端功能初步完善
- 5月10日：各业务 API 测试通过
- 5月21日：引入管理员功能
- 5月29日：支持 Base64 文件编码，提供文件与 Base64 互转 API
- 6月17日：增强认证逻辑，引入 JWT 与 BCrypt 密码学组件
- 6月30日：鲁棒性补强
- 7月4日：生产环境配置完成，部署数据库索引
- 7月22日：显式编写 openapi.yaml，进入 Apifox 联调准备阶段
- 8月15日：开始用户与鉴权部分联调，按实际业务增补 API
- 9月2日：用户业务接口测试完成（管理员接口由于不对外暴露，将在日后逐步测试）

## 🐞 已知问题及解决规划
### **功能收尾**
| 问题 | 现状与影响 | 解决规划 | 优先级 | 状态  |
|---|---|---|---|-----|
| 管理员接口测试未覆盖 | Admin 模块仅部分测试，管理端未对外暴露 | 补齐 AdminController/AdminService 测试 | P1 | 计划中 |
| 自动化接口测试脚本 | 目前依赖手工联调（Apifox），回归成本高 | 已新增 `ApiIntegrationTest` 接口自动化脚本，待接入 CI | P2 | 脚本已编写，待接入 CI |

### **架构与技术演进**
| 问题 | 现状与影响 | 解决规划 | 优先级 |
|---|---|---|---|
| 文章搜索为 LIKE 模糊匹配 | %keyword% 无法命中索引 → 全表扫描，数据量增长后延迟升高 | 引入 MySQL FULLTEXT + ngram 中文分词；数据量进一步增长时评估 Elasticsearch | P1 |
| Redis 缓存缺少三防 | 当前为裸缓存，存在穿透/击穿/雪崩风险 | 空值缓存与布隆过滤器（穿透）、逻辑过期/互斥锁（击穿）、TTL 随机化（雪崩） | P1 |
| 浏览/点赞计数同步写库 | 每次请求 UPDATE +1，热点文章行锁竞争与写压力大 | 改为 Redis INCR 原子计数 + @Scheduled 定时批量落库（容忍短暂误差） | P1 |
| JWT 登出后仍可续用 | 无状态 token 无主动失效语义 | 引入 Redis 黑名单（记录 jti 至过期），支持登出/封禁即时生效 | P2 |
| 登录接口无防爆破 | 存在暴力破解/撞库风险 | Redis 滑动窗口限流 + 账号失败次数锁定 | P2 |
| 文件存储绑定本地磁盘 | 单机存储，不利于水平扩展与备份 | 抽象存储接口（当前本地实现），预留对象存储（OSS/S3）切换 | P3 |


---

## 📄 License

[MIT](LICENSE)

## 鸣谢
- 部分Agent Skill脚本来自知名开源库[InterviewGuide](https://github.com/Snailclimb/interview-guide)