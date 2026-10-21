# ============================================================
# 快速回归命令 (PowerShell)
#
# 用途: 单行命令压单接口，快速看 P95 / 错误率，适合改完代码后回归对比。
# 工具可以混用 (默认用 wrk2，没装时用 hey)。
#
# 每个命令的输出都直接含 "改前 vs 改后" 的对比位，方便复制粘贴到记录里。
#
# 运行: PowerShell 下直接 .\scripts\loadtest\quick.ps1 执行，
#       或把里面某一行复制出来在终端跑
# ============================================================

$BASE = "http://127.0.0.1:8080"
$TOOL = "wrk2"    # 可改为 "hey" 或 "bombardier"

Write-Output "=== 快速回归命令集合 ==="
Write-Output ""
Write-Output "---- wrk2 安装: https://github.com/giltene/wrk2  (Windows 可从 Scoop 或源码编译) ----"
Write-Output "---- hey  安装: go install github.com/rakyll/hey@latest ----"
Write-Output "---- bombardier 安装: go install github.com/codesenberg/bombardier@latest ----"
Write-Output ""

Write-Output "--- 单接口快速冒烟 (10s 恒定速率 50 RPS) ---"
Write-Output "wrk2 -d10s -R50 --latency $BASE/api/articles/1001"
Write-Output "hey  -z10s -q50 -H 'Accept: application/json' $BASE/api/articles/1001"
Write-Output "bombardier -d10s --rate=50 --latencies -H 'Accept: application/json' $BASE/api/articles/1001"
Write-Output ""

Write-Output "--- 文章列表 (分页) ---"
Write-Output "wrk2 -d10s -R50 --latency '$BASE/api/articles?page=0&size=10'"
Write-Output ""

Write-Output "--- 按作者查文章 (身份感知缓存 key) ---"
Write-Output "wrk2 -d10s -R50 --latency '$BASE/api/articles/author/1?page=0&size=10'"
Write-Output ""

Write-Output "--- ES 搜索 ---"
Write-Output "wrk2 -d10s -R50 --latency '$BASE/api/articles/search?keyword=Redis&page=0&size=10'"
Write-Output ""

Write-Output "--- 收藏夹列表 ---"
Write-Output "wrk2 -d10s -R50 --latency $BASE/api/categories/top-level"
Write-Output ""

Write-Output "--- 收藏夹树 ---"
Write-Output "wrk2 -d10s -R30 --latency $BASE/api/categories/tree"
Write-Output ""

Write-Output "--- 对比命令: 改前 vs 改后 ---"
Write-Output "# 做法: 改完代码重启应用，对同一个接口分别跑两次 wrk2，记录 P95 差值"
Write-Output "# 例子: wrk2 -d10s -R50 --latency $BASE/api/articles/1001  # baseline"
Write-Output "#      wrk2 -d10s -R50 --latency $BASE/api/articles/1001  # after change"
Write-Output ""

Write-Output "--- MySQL Com_select 前后对比 (最硬的缓存命中证据) ---"
Write-Output "# 压前: mysql -h127.0.0.1 -P13306 -ublog_lt -ploadtest-db-pwd -N -e \"SHOW GLOBAL STATUS LIKE 'Com_select'\""
Write-Output "# 压后: 同上，取差值"
Write-Output "# 差值 / 请求数 ≈ 回源比例 (越低越好)"
Write-Output ""

Write-Output "--- Actuator 快速查看缓存指标 ---"
Write-Output "curl '$BASE/actuator/metrics/cache.gets?tag=cache:article:detail&tag=result:miss'"
Write-Output "curl '$BASE/actuator/metrics/cache.gets?tag=cache:article:detail&tag=result:hit'"
Write-Output "curl '$BASE/actuator/prometheus' | Select-String 'cache_'"
Write-Output ""

Write-Output "--- Redis 侧快速核验 ---"
Write-Output "docker exec blog-loadtest-redis-1 redis-cli -a loadtest-redis-pwd --no-auth-warning INFO stats"
Write-Output "# 关注 keyspace_hits / keyspace_misses 命中率"
Write-Output ""
