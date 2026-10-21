"""
雪崩专项测试 —— 验证 JitteredTtlFunction 是否打散 Spring Cache 写入 key 的过期时间。

关键思路:
  JitteredTtlFunction 只在 Spring Data Redis CacheManager 写 key 时生效（entryTtl(TtlFunction)）。
  直接用 redis-cli SET ... EX 写 key 不会走 Spring 的 TTL 逻辑。
  所以本脚本的做法是: 让应用自身通过 HTTP 请求触发 Spring Cache 写入。

流程:
  1. 确定 N 个不同的 article ID
  2. 用 redis-cli 先 DEL 掉它们的缓存 key（确保后续请求一定触发写入）
  3. 并发 HTTP 请求触发 Spring Cache 批量写入（这一步才会走 JitteredTtlFunction）
  4. 用 redis-cli PTTL 采样 Spring 刚写入的那些 key
  5. 统计标准差 + 直方图

前置条件:
  - Docker Desktop + loadtest compose 已启动
  - 应用 (loadtest profile) 已启动且已灌入种子数据 (1001-2000 文章)
"""

import argparse
import subprocess
import sys
import threading
import time
import urllib.request
import urllib.error


PROJECT = 'blog-loadtest'


# ========== 工具 ==========
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


def http_get(base, path):
    req = urllib.request.Request(f'{base}{path}', headers={'Accept': 'application/json'})
    try:
        t0 = time.time()
        with urllib.request.urlopen(req, timeout=5) as r:
            return r.status, time.time() - t0
    except urllib.error.HTTPError as e:
        return e.code, time.time() - t0
    except Exception:
        return None, 0


# ========== 主 ==========
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--base',         default='http://127.0.0.1:8080')
    ap.add_argument('--redis-pwd',    default='loadtest-redis-pwd')
    ap.add_argument('--count',        type=int, default=50)     # 要写入的缓存 key 数量
    ap.add_argument('--start-id',     type=int, default=1)  # 种子文章从 1 开始
    args = ap.parse_args()

    print(f'=== 雪崩专项测试 (TTL 抖动分布) ===')
    print(f'通过 Spring Cache 写入 {args.count} 个 key，然后采样 PTTL')

    # 预检
    print('\n[0] 预检...')
    if redis_cmd(args.redis_pwd, 'PING') != 'PONG':
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

    article_ids = list(range(args.start_id, args.start_id + args.count))
    cache_keys  = [f'article:detail::{aid}' for aid in article_ids]

    # 1. 先 DEL 所有目标 key，确保后续 HTTP 请求一定触发 Spring Cache 写入
    print(f'\n[1] DEL {args.count} 个缓存 key（让 Spring Cache 在下一步全新写入）')
    for key in cache_keys:
        redis_cmd(args.redis_pwd, 'DEL', key)
    print('  完成')

    # 2. 并发 HTTP 请求触发 Spring Cache 写入
    print(f'\n[2] 并发 HTTP 请求触发 Spring Cache 写入...')
    barrier  = threading.Semaphore(args.count)  # 一次放开 N 个
    written  = [False] * args.count
    errors   = [0]

    def fetch(i, aid):
        barrier.acquire()
        try:
            s, _ = http_get(args.base, f'/api/articles/{aid}')
            if s == 200:
                written[i] = True
            else:
                errors[0] += 1
        finally:
            barrier.release()

    threads = [threading.Thread(target=fetch, args=(i, aid))
               for i, aid in enumerate(article_ids)]
    t0 = time.time()
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    elapsed = time.time() - t0

    ok = sum(written)
    print(f'  HTTP 成功: {ok}/{args.count}  失败: {errors[0]}  耗时: {elapsed:.2f}s')

    # 3. 等 Spring Cache 写 Redis（极快，10ms 内）后采样
    print(f'\n[3] 采样 PTTL...')
    time.sleep(0.5)
    ttls = []
    for key in cache_keys:
        out = redis_cmd(args.redis_pwd, 'PTTL', key)
        try:
            ms = int(out.strip())
            if ms > 0:
                ttls.append(ms / 1000.0)
        except Exception:
            pass

    print(f'  有效采样: {len(ttls)} / {args.count}')
    if len(ttls) < 5:
        print('  [!] 有效 key 太少，可能这些文章不存在，调整 --start-id 重试')
        return

    # 4. 统计
    base_ttl = 1800  # article:detail 缓存基准 TTL = 30 min
    mean     = sum(ttls) / len(ttls)
    stdev    = (sum((t - mean) ** 2 for t in ttls) / len(ttls)) ** 0.5
    mi, ma   = min(ttls), max(ttls)
    jitter_pct = stdev / base_ttl * 100

    near_center = sum(1 for t in ttls if abs(t - base_ttl) <= base_ttl * 0.05)
    near_pct    = near_center / len(ttls) * 100

    print(f'\n====== 统计结果 ======')
    print(f'  缓存基准 TTL:    {base_ttl}s (30min)')
    print(f'  实际平均:        {mean:.1f}s')
    print(f'  标准差:          {stdev:.1f}s  (相对基准 {jitter_pct:.1f}%)')
    print(f'  范围:            [{mi:.0f}s, {ma:.0f}s]')
    print(f'  ±5% 基准内聚集:  {near_center}/{len(ttls)} = {near_pct:.1f}%')

    # 直方图 (每 100s 桶)
    buckets = {}
    for t in ttls:
        b = int(t / 100) * 100
        buckets[b] = buckets.get(b, 0) + 1
    print(f'\n  直方图 (每 100s 桶):')
    for b in sorted(buckets):
        bar = '#' * (buckets[b] * 2)
        print(f'    {b:4d}-{b+99:4d}s: {buckets[b]:3d}  {bar}')

    # 判定
    no_jitter  = (stdev < base_ttl * 0.02)   # 标准差 < 2% → 几乎没抖动
    has_jitter = (stdev > base_ttl * 0.04)  # 标准差 > 4% → 有抖动（±15% 的预期标准差约 8.5%）
    verdict = 'PASS' if has_jitter else ('FAIL' if no_jitter else 'PARTIAL')

    print(f'\n  ==== 判定: {verdict} ====')
    if verdict == 'FAIL':
        print('  说明: TTL 几乎全相同 → JitteredTtlFunction 未生效')
        print('  检查点:')
        print('    1. RedisConfig.cacheManager 是否用 entryTtl(new JitteredTtlFunction(...))')
        print('    2. RedisCacheConfiguration 是否还残留旧的 entryTtl(Duration)')
        print('    3. 是否用了 writeWithCacheManager / lockingRedisCacheWriter')
    elif verdict == 'PARTIAL':
        print('  说明: 有离散但不够强（预期 ±15% 抖动产生 ~8.5% 标准差）')
    else:
        print('  说明: Spring Cache 写入的 key 过期时间已有效打散，无雪崩时间相关性')


if __name__ == '__main__':
    main()
