# 压测脚本目录说明

> 全部脚本在应用已以 `loadtest` profile 启动、种子数据灌入完成之后使用。
> 见 `docs/loadtest-plan.md` 了解完整准备步骤。

## 目录结构

```
scripts/loadtest/
├── 01-main.js          # k6 主力压测：5 场景混合 (详情 / 列表 / 作者列表 / 搜索 / 收藏夹)
├── 02-breakdown.js     # k6 击穿专项：ramping-vus 瞬间齐射（近似）
├── 03-penetration.js   # k6 穿透专项：狂打不存在 ID
├── 04-search.js        # k6 ES 搜索独立压测
├── py/
│   ├── breakdown.py    # Python 击穿专项：Barrier 同步点齐射 + Com_select 核对（**最可靠**）
│   ├── penetration.py  # Python 穿透专项：哨兵拦截验证
│   └── avalanche.py    # Python 雪崩分布：PTTL 采样统计（零依赖）
└── quick.ps1           # wrk2 / hey / bombardier 快速回归命令清单
```

## 推荐执行顺序

### 1. 工具安装

```powershell
# k6 (主力)
winget install k6.k6                    # Windows winget
# 或: choco install k6
# 或: https://grafana.com/docs/k6/latest/get-started/installation/

# wrk2 (快速回归)
# 从 Scoop / Go install / 源码编译

# Python 3.8+    (已内置，零依赖)
```

### 2. 环境就绪

```powershell
# 启动中间件
docker compose --env-file deploy/.env.loadtest -f deploy/docker-compose.loadtest.yml up -d

# 启动应用 (宿主机)
mvn spring-boot:run "-Dspring-boot.run.profiles=loadtest"
# 等控制台打印 "Started BlogApplication in ..." 且 ES 全量重建完成

# 验证种子数据已灌入
curl http://127.0.0.1:8080/api/articles/1001
curl http://127.0.0.1:8080/api/users/lt_author1

# 登录拿 token (可选，多数公开接口不需要)
curl -X POST http://127.0.0.1:8080/api/users/login ^
  -H "Content-Type: application/json" ^
  -d "{\"username\":\"lt_author1\",\"password\":\"LoadTest123!\"}"
```

### 3. 跑脚本

```powershell
# === 主力压测 ===
k6 run -e BASE_URL=http://127.0.0.1:8080 scripts/loadtest/01-main.js

# === 击穿专项 (Python 版最可靠) ===
# 先手动 DEL 缓存
docker exec blog-loadtest-redis-1 redis-cli -a loadtest-redis-pwd --no-auth-warning DEL "article:detail::1001"
# 立刻启动齐射 (1s 内)
python scripts/loadtest/py/breakdown.py --vus 200 --article-id 1001

# === 穿透专项 ===
python scripts/loadtest/py/penetration.py

# === 雪崩专项 ===
python scripts/loadtest/py/avalanche.py --count 100 --base-ttl 60

# === 快速回归 ===
.\scripts\loadtest\quick.ps1          # 打印所有命令
wrk2 -d10s -R50 --latency http://127.0.0.1:8080/api/articles/1001
```

## 关键：缓存三防的验收信号

| 防护 | 手段 | 通过标准 | 从哪看 |
|---|---|---|---|
| **雪崩 (TTL 抖动)** | `avalanche.py` | 标准差 > 基准 5%，±5% 内聚集 < 30% | Python 直接输出 |
| **击穿** | `breakdown.py` | `cache.gets{miss}` 增量 ≈ 1；MySQL Com_select 增量 ≈ 1 | Python 直接输出 |
| **穿透** | `penetration.py` | 每个新不存在 ID 仅触发 1 次 DB；哨兵 TTL ≈ 300s | Python 直接输出 + redis-cli |

## 常见坑

1. **击穿要齐射，不是缓慢加压**
   `constant-vus` 加压慢，请求分散到锁释放之后，看起来都命中了——假阴性。
   用 Python 版的 Barrier 同步点才能真正复现"同一毫秒"的过期瞬间。

2. **击穿前先 DEL 缓存 key**
   k6 没有 redis-cli，所以击穿脚本跑之前要手动清 key，再立刻启动。

3. **Dev profile 压测 = 白测**
   `dev` 下 `app.cache.enabled=false`，缓存根本不开。必须用 `loadtest`。

4. **Actuator 在非 loadtest 下被 SecurityConfig 挡住**
   除了 `loadtest` 以外的 profile 下 `/actuator/metrics` 和 `/actuator/prometheus` 需要 JWT。
   loadtest profile 已在 SecurityConfig 放行为公开。

5. **Com_select 要取增量，不要看绝对值**
   先记录基线，压后再取差。绝对值包含 Flyway/迁移/启动阶段的查询。
