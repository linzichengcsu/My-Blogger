"""
穿透专项测试 —— 验证 CachePenetrationGuardAspect 哨兵缓存是否拦截不存在 ID 的请求。

用法:
  py scripts/loadtest/py/penetration.py [--rounds 5 --repeat-per-id 100]

零依赖: 仅需 Docker Desktop + loadtest compose 已启动 + 应用 (loadtest profile) 已运行。
"""

import argparse
import subprocess
import sys
import threading
import time
import urllib.request
import urllib.error


PROJECT = 'blog-loadtest'


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


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--base',            default='http://127.0.0.1:8080')
    ap.add_argument('--redis-pwd',       default='loadtest-redis-pwd')
    ap.add_argument('--rounds',          type=int, default=5)
    ap.add_argument('--repeat-per-id',   type=int, default=50)
    args = ap.parse_args()

    print(f'=== 穿透专项测试 ===')
    print(f'每轮 {args.rounds} 个新 ID，每个 ID 连打 {args.repeat_per_id} 次\n')

    # 预检
    print('[0] 预检...')
    ping = redis_cmd(args.redis_pwd, 'PING')
    print(f'  Redis PING: {ping}')
    if ping != 'PONG':
        print('  [!] Redis 不通')
        sys.exit(1)

    try:
        status, _ = http_get(args.base, '/actuator/health')
        print(f'  应用健康: {status}')
        if status != 200:
            print('  [!] 应用不可用，先启动 loadtest profile')
            sys.exit(1)
    except Exception as e:
        print(f'  [!] 应用连接失败: {e}')
        sys.exit(1)

    # 选肯定不存在的 ID
    nonexistent_ids = [99999000 + i * 13 for i in range(args.rounds)]

    # 清理旧哨兵
    print('\n[1] 清理旧哨兵...')
    for nid in nonexistent_ids:
        redis_cmd(args.redis_pwd, 'DEL', f'cache:notfound:article:{nid}')

    print(f'\n[2] 测试 {args.rounds} 轮新 ID...')
    all_results = []
    for nid in nonexistent_ids:
        print(f'\n  --- ID={nid} ---')
        before = redis_cmd(args.redis_pwd, 'EXISTS', f'cache:notfound:article:{nid}')
        print(f'    哨兵预存在? {before}')

        # 并发打同一个 ID
        def hit(_, nid=nid):
            return http_get(args.base, f'/api/articles/{nid}')

        results = []
        threads = []
        for i in range(args.repeat_per_id):
            t = threading.Thread(target=lambda: results.append(hit(i)))
            threads.append(t)
            t.start()
        for t in threads:
            t.join()

        statuses = [r[0] for r in results]
        ttl_out = redis_cmd(args.redis_pwd, 'TTL', f'cache:notfound:article:{nid}')
        s200 = statuses.count(200)
        s404 = statuses.count(404)
        serr = sum(1 for s in statuses if s is None or (isinstance(s, int) and s >= 500))
        print(f'    状态码: 200={s200}  404={s404}  异常={serr}')
        print(f'    哨兵 TTL: {ttl_out}s (期望 240-300)')
        all_results.extend(statuses)

    verdict = 'PASS' if all(s == 404 for s in all_results) else 'FAIL'
    print(f'\n====== 结果 ======')
    print(f'  全部返回 404: {verdict}')
    print(f'  404 占比: {sum(1 for s in all_results if s == 404)}/{len(all_results)}')
    print(f'  ==== 判定: {verdict} ====')
    if verdict == 'FAIL':
        print('  说明: 部分请求没返回 404，可能哨兵没拦截到')


if __name__ == '__main__':
    main()
