// =====================================================================
// 击穿专项脚本 (k6)
//
// 原理: 先打一次让热点 key 进缓存，然后用 `CacheEvict` 逻辑把它逐出 ——
// 等等，k6 没法触发 CacheEvict，所以实际做法是：先 DEL 掉 Redis 中的
// 热点 key，然后齐射 N 个请求看有几个命中 miss（预期只有 1 个）。
//
// 但 k6 的 `constant-vus` 是慢慢加压的，真正的齐射要用 Sync. 见
// 02b-breakdown-sync.js 或 Python 版 scripts/loadtest/breakdown.py
//
// 本脚本用一种近似做法: `ramping-vus` 瞬间拉到大量 VUs。
//
// 运行:
//   k6 run -e BASE_URL=http://127.0.0.1:8080 -e HOT=1001 scripts/loadtest/02-breakdown.js
//
// 验收标准:
//   `cache.gets{result=miss}` 增量 = 1 (或很小)
//   MySQL Com_select 增量    ≈ 1
// =====================================================================

import http from 'k6/http';
import { check, group } from 'k6';

const BASE = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const HOT  = parseInt(__ENV.HOT) || 1001;
const VUS  = parseInt(__ENV.VUS) || 200;

export const options = {
    // 瞬间加压到 VUS，模拟过期瞬间的并发
    scenarios: {
        breakdown: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '1s',  target: VUS },    // 1s 内拉到 VUS 峰值
                { duration: '10s', target: VUS },    // 维持
                { duration: '1s',  target: 0 },
            ],
            gracefulRampDown: '1s',
        },
    },
};

export default function () {
    const res = http.get(`${BASE}/api/articles/${HOT}`, {
        headers: { Accept: 'application/json' },
    });
    check(res, {
        'status 200': r => r.status === 200,
    });
}

// ---- 测试执行前：用一个 VU 先清掉热点 key 的缓存，让全部 VU 同时面对 miss ----
// k6 没有 before() 可以跑 redis-cli，所以只能请用户手动先执行:
//   docker exec blog-loadtest-redis-1 redis-cli -a loadtest-redis-pwd --no-auth-warning \
//       DEL "article:detail::1001"
// 然后立刻启动本脚本。两个动作要快，否则缓存自然过期前可能被其他请求重建。
//
// 或者直接在 VU 里加一个固定 VU 做预热后，手动 DEL，再跑 ramping。
// 更可靠的方式：用 Python 脚本 (breakdown.py) —— 它能直接调用 redis-cli DEL。
