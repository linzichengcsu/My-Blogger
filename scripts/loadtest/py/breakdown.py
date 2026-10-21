"""
击穿专项测试 —— 验证 sync=true + lockingRedisCacheWriter 是否让热点 key 过期瞬间
的回源串行化（只有一个线程查库）。

用法:
  # 1. 先手动 DEL 缓存 key (在另一个终端):
  #      docker exec blog-loadtest-redis-1 redis-cli -a loadtest-redis-pwd --no-auth-warning DEL "article:detail::1001"
  # 2. 立刻启动本脚本 (1s 内):
  py scripts/loadtest/py/breakdown.py --vus 200 --article-id 1001

零依赖: Docker Desktop + loadtest compose + 应用。
        不再依赖本机 redis-cli / mysql。
"""

import argparse
import json
import subprocess
import sys
import threading
import time
import urllib.request
import urllib.error


PROJECT = 'blog-loadtest'


# ---------- 容器发现 ----------
def find_container(service_keyword):
    r = subprocess.run(
        ['docker', 'ps', '--filter', f'label=com.docker.compose.project={PROJECT}',
         '--filter', f'name={service_keyword}', '--format', '{{.Names}}'],
        capture_output=True, text=True, timeout=10
    )
    names = [n.strip() for n in r.stdout.splitlines() if n.strip()]
    if not names:
        print(f'[错误] 未找到含 "{service_keyword}" 的容器，先启动 loadtest compose')
        sys.exit(1)
    return names[0]


def docker_exec(container, *args):
    r = subprocess.run(
        ['docker', 'exec', '-i', container] + list(args),
        capture_output=True, text=True, timeout=30
    )
    return r.stdout.strip()


def redis_cmd(password, *args):
    return docker_exec(find_container('redis'),
                       'redis-cli', '-a', password, '--no-auth-warning', *args)


def mysql_cmd(user, pwd, *args):
    return docker_exec(find_container('mysql'),
                       'mysql', f'-u{user}', f'-p{pwd}', '-N', *args)


# ---------- MySQL Com_select ----------
def mysql_com_select(user, pwd):
    out = mysql_cmd(user, pwd, '-e', "SHOW GLOBAL STATUS LIKE 'Com_select'")
    try:
        return int(out.strip().split()[-1])
    except Exception:
        return -1


# ---------- Actuator / Prometheus 指标 ----------
def prometheus_metric(base, name, **labels):
    """从 Prometheus /actuator/prometheus 端点精确读取带指定 labels 的 metric 值。

    Boot 4 Actuator /actuator/metrics 的 tag 过滤 (?cache=...&result=...) 行为异常，
    返回的是该 metric 在所有 cache_name 上的总和，无法单独过滤。
    Prometheus 端点按 Micrometer 的 expected labels 正确输出，直接文本解析即可。

    示例: prometheus_metric(base, 'cache_gets_total', cache='article:detail', result='miss')
    """
    url = f'{base}/actuator/prometheus'
    try:
        with urllib.request.urlopen(url, timeout=5) as r:
            text = r.read().decode()
        target_keys = set(f'{k}="{v}"' for k, v in labels.items())
        for line in text.splitlines():
            line = line.strip()
            if not line or line.startswith('#') or not line.startswith(name + '{'):
                continue
            lbrace = line.find('{')
            rbrace = line.find('}', lbrace)
            if rbrace < 0:
                continue
            labels_str = line[lbrace + 1:rbrace]
            val_str = line[rbrace + 1:].strip()
            label_pairs = set()
            for pair in labels_str.split(','):
                k, _, v = pair.partition('=')
                label_pairs.add(f'{k.strip()}={v.strip()}')
            # 所有目标 key=value 都要在 label_pairs 里
            if target_keys <= label_pairs:
                try:
                    return float(val_str)
                except ValueError:
                    return 0.0
        return 0.0
    except Exception as e:
        print(f'  [!] Prometheus 读取失败: {e}')
    return -1


# ---------- HTTP ----------
def http_get(base, path):
    req = urllib.request.Request(f'{base}{path}', headers={'Accept': 'application/json'})
    try:
        t0 = time.time()
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status, time.time() - t0
    except urllib.error.HTTPError as e:
        return e.code, time.time() - t0
    except Exception:
        return None, 0


# ---------- 同步点 ----------
class Barrier:
    def __init__(self, n):
        self.n = n
        self.lock = threading.Lock()
        self.phase = 0
        self.count = 0
        self.event = threading.Event()

    def wait(self):
        with self.lock:
            self.phase += 1
            self.count += 1
            if self.count >= self.n:
                self.event.set()
                return
        # 所有线程到位后再一起放行
        self.event.wait()


# ---------- 主 ----------
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--base',         default='http://127.0.0.1:8080')
    ap.add_argument('--redis-pwd',    default='loadtest-redis-pwd')
    ap.add_argument('--mysql-user',   default='blog_lt')
    ap.add_argument('--mysql-pwd',    default='loadtest-db-pwd')
    ap.add_argument('--vus',          type=int, default=200)
    ap.add_argument('--article-id',   type=int, default=1001)
    args = ap.parse_args()

    print(f'=== 击穿专项测试 VUs={args.vus} article={args.article_id} ===\n')
    cache_key = f'article:detail::{args.article_id}'
    print(f'缓存 key: {cache_key}')

    # 预检
    print('\n[0] 预检...')
    ping = redis_cmd(args.redis_pwd, 'PING')
    print(f'  Redis PING: {ping}')
    if ping != 'PONG':
        print('  [!] Redis 不通')
        sys.exit(1)

    try:
        s, _ = http_get(args.base, '/actuator/health')
        print(f'  应用健康: {s}')
        if s != 200:
            print('  [!] 应用不可用')
            sys.exit(1)
    except Exception:
        print('  [!] 应用连接失败')
        sys.exit(1)

    # 1. 预热
    print(f'\n[1/5] 预热：打一次让热点 key 进缓存')
    status, _ = http_get(args.base, f'/api/articles/{args.article_id}')
    print(f'  预热响应: {status}')

    # 2. 确认 key 存在
    exists = redis_cmd(args.redis_pwd, 'EXISTS', cache_key)
    print(f'  Redis EXISTS {cache_key}: {exists}  (应为 1)')

    # 3. 基线
    print(f'\n[2/5] 记录基线')
    # Prometheus endpoint 精确按 label 过滤（Boot 4 Actuator 的 ?cache=...&result=... tag query
    #  行为异常：返回所有 cache_name 上的总和，无法单独过滤，改由 Prometheus 端点文本解析）
    miss_before   = prometheus_metric(args.base, 'cache_gets_total', cache='article:detail', result='miss')
    hit_before    = prometheus_metric(args.base, 'cache_gets_total', cache='article:detail', result='hit')
    com_select_0  = mysql_com_select(args.mysql_user, args.mysql_pwd)
    print(f'  cache.gets miss 基线: {miss_before}')
    print(f'  cache.gets hit 基线:  {hit_before}')
    print(f'  MySQL Com_select 基线: {com_select_0}')

    # 4. 手动驱逐缓存 —— 模拟过期瞬间
    print(f'\n[3/5] DEL 缓存 key（模拟过期）')
    del_out = redis_cmd(args.redis_pwd, 'DEL', cache_key)
    print(f'  Redis DEL 结果: {del_out}')

    # 5. 齐射
    print(f'\n[4/5] Barrier 同步点齐射 {args.vus} 个线程...')
    barrier  = Barrier(args.vus)
    results  = []

    def shooter(i):
        barrier.wait()
        status, lat = http_get(args.base, f'/api/articles/{args.article_id}')
        results.append((i, status, lat))

    threads = [threading.Thread(target=shooter, args=(i,)) for i in range(args.vus)]
    for t in threads:
        t.start()
    t0 = time.time()
    for t in threads:
        t.join()
    wall_ms = (time.time() - t0) * 1000
    print(f'  齐射总耗时: {wall_ms:.0f}ms')

    # 6. 读取结果
    print('\n[5/5] 读取回源次数...')
    time.sleep(0.5)
    miss_after   = prometheus_metric(args.base, 'cache_gets_total', cache='article:detail', result='miss')
    hit_after    = prometheus_metric(args.base, 'cache_gets_total', cache='article:detail', result='hit')
    com_select_1 = mysql_com_select(args.mysql_user, args.mysql_pwd)

    miss_delta   = miss_after  - miss_before
    hit_delta    = hit_after   - hit_before
    select_delta = com_select_1 - com_select_0

    ok_count  = sum(1 for _, s, _ in results if s == 200)
    err_count = sum(1 for _, s, _ in results if s != 200)

    print(f'\n====== 结果 ======')
    print(f'  HTTP 成功:   {ok_count} / {args.vus}')
    print(f'  HTTP 失败:   {err_count} / {args.vus}')

    if results:
        lats = sorted(r[2] * 1000 for r in results)
        p50 = lats[len(lats)//2]
        p95 = lats[int(len(lats)*0.95)]
        p99 = lats[int(len(lats)*0.99)] if len(lats) > 100 else lats[-1]
        print(f'  P50/P95/P99: {p50:.0f}/{p95:.0f}/{p99:.0f} ms')

    # 指标解读：
    #   MySQL Com_select 是击穿是否成功的**金标准**：只有 1 个线程回源 → 业务层 N 次 SQL
    #   业务 getArticleById → convertToDetailDTO 里还有 commentCount + author lazy + categories lazy
    #   附加查询，单次业务回源 = 4 次 Com_select，预热时已在基线中包含
    #
    #   Prometheus cache.gets: pre-check + 锁内双检各触发 1 次 get，单次 miss = 2×miss, 单次 hit = 1×hit
    #   但 Lettuce max-active=20，200 并发时大量线程在连接池排队，pre-check 无法全部执行，
    #   实际 miss/hit 会低于理论值。因此 MySQL Com_select 才是靠谱的锁串行化判据。
    print(f'\n  cache.gets miss 增量:   {miss_delta}    (单次 miss 回源理论值=2, 受 Lettuce 连接池排队影响会偏低)')
    print(f'  cache.gets hit 增量:    {hit_delta}     (≈ VUs-1 等待线程的锁内双检 hit, 理论值≈{args.vus - 1})')
    print(f'  MySQL Com_select 增量:  {select_delta}  (1 次业务回源 × 业务内 SELECT 次数 ≈ 4)')

    # 判据：MySQL Com_select ≤ 10（≤ 约 2-3 次业务回源）+ 大部分请求命中
    verdict = 'PASS' if (select_delta <= 10 and hit_delta >= args.vus * 0.5) else 'FAIL'
    print(f'\n  ==== 判定: {verdict} ====')
    if verdict == 'FAIL':
        print('  说明: Com_select 增量过大 → 多线程同时回源，锁串行化失效')
        print('       hit 增量太小 → 多数请求仍在回源，命中率不足')


if __name__ == '__main__':
    main()
