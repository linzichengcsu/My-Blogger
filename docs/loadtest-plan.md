# 性能压测与缓存健壮性测试方案

> 本文件是压测工作的**总纲**：第 1 步定义测什么、看什么；第 2-4 步说明环境、降噪与观测如何就绪；
> 第 5-10 步（脚本编写、基线、三防专项、故障注入、CI 固化）待后续补充。

---

## 一、压测目标与指标（第 1 步）

### 1.1 方法论主线

性能压测看**吞吐 / 延迟 / 错误率**；缓存健壮性测试看**回源次数**。

> 延迟指标会骗人——缓存命中率下降时延迟可能只涨一点点，但数据库已经被打穿。
> **"DB 被打了几次"才是三防是否生效的唯一判据。**

### 1.2 指标体系

| 类别 | 指标 | 采集来源 |
|---|---|---|
| 吞吐 | RPS（请求/秒） | k6 / JMeter 汇总 |
| 延迟 | P50 / P95 / P99（ms） | k6 / JMeter 汇总 |
| 错误率 | 非 2xx 占比 | k6 / JMeter 汇总 |
| 缓存命中 | `cache.gets{result=hit/miss}` | `/actuator/prometheus` |
| 缓存写入 | `cache.puts`、`cache.evictions` | 同上 |
| **回源次数** | MySQL `Com_select` 增量 | `SHOW GLOBAL STATUS` |
| 连接池 | HikariCP active / pending | `/actuator/metrics/hikaricp.connections.*` |
| Redis 侧 | 延迟、慢命令 | `redis-cli --latency`、`SLOWLOG GET` |
| JVM | 火焰图、GC | async-profiler / JFR |

### 1.3 压测场景与目标值

目标值需在**基线压测（第 8 步）**后据实测校准，下表为初始验收线：

| 编号 | 场景 | 接口 | 目标（初始） | 必须核验 |
|---|---|---|---|---|
| S1 | 文章详情（缓存命中） | `GET /api/articles/{id}` | P95 < 150ms，错误率 0 | `cache.gets{hit}` 上升，`Com_select` 不增 |
| S2 | 公开文章列表 | `GET /api/articles?page=0&size=10` | P95 < 250ms | `Com_select` 增量与请求数不成比例 |
| S3 | ES 全文检索 | `GET /api/articles/search?keyword=Redis` | P95 < 400ms | ES 索引文档数 = 种子文章数 |
| S4 | 按作者查询（带权限 key） | `GET /api/articles/author/{id}` | P95 < 250ms | 命中率 > 90%（同一身份重复请求） |
| S5 | **击穿专项**：齐射已失效热点 key | `GET /api/articles/{hotId}` | 全部请求成功 | **`cache.gets{miss}` 增量 = 1**，`Com_select` 增量 = 1 |
| S6 | **穿透专项**：狂打不存在 ID | `GET /api/articles/99999999` | 全部优雅 404 | 首个 ID 仅 1 次 DB 查询；`cache:notfound:*` 存在且 TTL ≈ 300s |
| S7 | **雪崩专项**：TTL 抖动分布 | 批量写入后采样 `PTTL` | — | 同秒写入的 N 个 key，过期时刻明显散开 |
| S8 | **降级专项**：掐断 App→Redis | S1 接口 | 业务不报错，系统可用 | Redis 不可用时缓存静默失效，无异常抛出 |

### 1.4 环境规格基线（必须记录，否则跨次压测不可比）

| 项 | 值 |
|---|---|
| 施压机 CPU / 内存 | _待填_ |
| 应用 JVM 参数 / 堆 | _待填_ |
| MySQL / Redis / ES 容器资源上限 | 本方案：Redis 512MB、ES 512MB 堆 |
| 种子数据量 | 1000 篇文章 / 10 作者 / 12 收藏夹 |
| 应用实例数 | 1（单实例基线；多实例用于验证 Redis 锁的跨实例互斥） |

> ⚠️ 施压机与被测机**不能是同一台**，否则压测结果测的是资源争抢，不是应用性能。
> 若只有一台机器，至少确认 CPU 未跑满、且施压进程与应用进程分开限核。

---

## 二、压测环境（第 2 步）

### 2.1 隔离设计

| 维度 | 生产栈 `docker-compose.yml` | 压测栈 `docker-compose.loadtest.yml` |
|---|---|---|
| project name | `personal-blogger` | `blog-loadtest` |
| 数据库 | `csulzc_blogdb_prod` | `csulzc_blogdb_loadtest` |
| Redis 卷 / 数据 | `redisdata` | 独立容器，`appendonly no`（无持久化干扰） |
| ES 卷 | `esdata` | `lt-esdata` |
| 宿主机端口 | web:80 | mysql:13306 / redis:16379 / es:19200 / blog:8080（仅 127.0.0.1） |
| Spring profile | prod | **loadtest** |

> 端口刻意用 13306 / 16379 / 19200，避开宿主机上 dev 环境的 3306 / 6379，
> 两边可以同时运行互不干扰。

### 2.2 启动方式

**用法 A：仅中间件容器化，应用跑宿主机（推荐）**
便于用 async-profiler / JFR 直接剖析应用进程。

```powershell
# 1. 启动中间件（需先启动 Docker Desktop）
docker compose --env-file deploy/.env.loadtest -f deploy/docker-compose.loadtest.yml up -d

# 2. 宿主机运行应用（loadtest profile 零额外环境变量即可直连）
mvn spring-boot:run "-Dspring-boot.run.profiles=loadtest"
```

**用法 B：整个栈容器化**

```powershell
docker compose --env-file deploy/.env.loadtest -f deploy/docker-compose.loadtest.yml --profile app up -d
# 应用地址：http://127.0.0.1:8080
```

### 2.3 数据准备

无需手工导入 SQL。`LoadTestDataSeeder`（仅 `loadtest` profile 注册）在启动时自动灌入：

- 10 个作者账号：`lt_author1` ~ `lt_author10`，**统一密码 `LoadTest123!`**
- 8 个顶级收藏夹 + 4 个子收藏夹
- 1000 篇 `RELEASE` 状态文章（数量由 `app.loadtest.seed.article-count` 调整）
- 文章-收藏夹关联

执行顺序：Seeder（最高优先级）→ 迁移 → ES 全量重建（`reindex-on-startup=true`），
因此**一次启动即可让 MySQL 与 ES 都就绪**。库中已有文章时自动跳过，重启不会重复灌入。

### 2.4 重置压测环境

```powershell
# 清空数据卷，回到全新状态（会连带删除种子数据）
docker compose --env-file deploy/.env.loadtest -f deploy/docker-compose.loadtest.yml down -v
```

---

## 三、降噪清单（第 3 步）

压测时任何"非被测目标"的日志或统计都会污染结果。loadtest profile 已统一处理：

| 噪声源 | dev 默认 | loadtest 处理 | 原因 |
|---|---|---|---|
| Hibernate SQL 打印 | `show-sql: true` | `false` | 每次查询字符串拼接 + 输出 |
| SQL 参数绑定日志 | `org.hibernate.orm.jdbc.bind: TRACE` | `WARN` | 每参数一行日志，量级极大 |
| Hibernate 统计 | `generate_statistics: true` | `false` | 统计收集本身有运行时开销 |
| AOP 请求日志 | 开启 | `app.aspect.request-log.enabled=false` | 每请求做 JSON 序列化，开销显著 |
| Swagger 扫描 | 开启 | `springdoc.*.enabled=false` | 启动与运行时无谓开销 |
| 根日志级别 | `DEBUG`（csulzc） | `INFO`（仅业务包） | 保留降级/熔断告警可见 |

> `RequestLogAspect` 已改造为可通过 `app.aspect.request-log.enabled` 关闭，
> 生产与开发环境行为不变（`matchIfMissing=true`）。

> ❗ **`app.cache.enabled` 在 dev 下是 `false`** —— 直接用 dev 压测等于测一个没有缓存的系统，
> 结论完全无效。压测必须走 loadtest profile。

---

## 四、观测通道（第 4 步）

### 4.1 已就绪

| 项 | 配置 |
|---|---|
| Prometheus 注册表 | `pom.xml` 新增 `micrometer-registry-prometheus`（此前只有 actuator，无法被 Prometheus 抓取） |
| 暴露端点 | `health,info,metrics,prometheus` |
| Redis 健康 | `management.health.redis.enabled=true`（dev 默认关闭） |

### 4.2 常用观测命令

```powershell
# 缓存命中/未命中（回源次数最直接的证据）
curl "http://127.0.0.1:8080/actuator/metrics/cache.gets?tag=cache:article:detail&tag=result:miss"

# 全部缓存指标
curl "http://127.0.0.1:8080/actuator/metrics?name=cache"

# Prometheus 格式全量指标（接 Grafana 用）
curl "http://127.0.0.1:8080/actuator/prometheus"

# 数据库侧回源次数（最硬的旁证），比较压测前后差值
docker exec blog-loadtest-mysql-1 mysql -uroot -ploadtest-root-pwd -e "SHOW GLOBAL STATUS LIKE 'Com_select'"

# 击穿锁：压测中应能看到短暂存在的锁 key
docker exec blog-loadtest-redis-1 redis-cli -a loadtest-redis-pwd --no-auth-warning --scan --pattern "*~lock"

# 穿透哨兵：应存在且 TTL ≈ 300s
docker exec blog-loadtest-redis-1 redis-cli -a loadtest-redis-pwd --no-auth-warning --scan --pattern "cache:notfound:*"
docker exec blog-loadtest-redis-1 redis-cli -a loadtest-redis-pwd --no-auth-warning ttl "cache:notfound:article:99999999"

# Redis 侧延迟与慢命令
docker exec blog-loadtest-redis-1 redis-cli -a loadtest-redis-pwd --no-auth-warning --latency
docker exec blog-loadtest-redis-1 redis-cli -a loadtest-redis-pwd --no-auth-warning slowlog get 10
```

> 容器名以 `docker ps` 实际输出为准（Compose 会自动加 project 前缀）。

### 4.3 关键陷阱

- **齐射才能测出击穿**：用 `constant-vus` 缓慢加压，请求会分散到锁释放之后，看起来"只回源 1 次"但那是假象。
  必须用 JMeter 的 `Synchronizing Timer` 或 k6 的瞬时爬坡，让请求在同一毫秒发出。
- **比较 `Com_select` 要取增量**：先记录基线值，压测后再取差，不能直接看绝对值。
- **别用 dev profile 压测**：缓存关闭 + 全量日志，两个致命干扰叠加。

---

## 五、后续步骤（第 5-10 步，待实施）

| 步骤 | 内容 | 交付物 |
|---|---|---|
| 5 | 准备测试素材 | 固定文章 ID 集合、JWT token（用 `lt_author1 / LoadTest123!` 登录获取） |
| 6 | 安装 k6 / JMeter | 工具就位 |
| 7 | 编写压测脚本 | 按 S1-S8 场景分脚本 |
| 8 | 跑基线并记录 | 基线报告 |
| 9 | 三防专项测试 | S5/S6/S7 结论 |
| 10 | 故障注入 + CI 固化 | Toxiproxy 降级验证；Testcontainers 三防断言进 CI |
