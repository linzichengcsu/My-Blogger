// =====================================================================
// 穿透专项脚本 (k6) —— 狂打不存在的文章 ID
//
// 验收标准:
//   1. 全部返回 404，不出现 5xx 或连接错误
//   2. 首个 ID 触发 1 次 DB 查询；后续同 ID 被哨兵拦截，Com_select 不再增加
//   3. Redis 中能看到 cache:notfound:article:{id} 且 TTL ≈ 300s
//
// 运行:
//   k6 run -e BASE_URL=http://127.0.0.1:8080 scripts/loadtest/03-penetration.js
//
// 前提: 应用必须是 loadtest profile (CachePenetrationGuardAspect 才会生效)
// =====================================================================

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://127.0.0.1:8080';

// 选择一个肯定不存在且永远不会被种子数据用到的 ID
const NONEXISTENT_ARTICLE  = 99999999;
const NONEXISTENT_CATEGORY = 99999998;

export const options = {
    vus: 20,
    iterations: 500,   // 共 500 次请求，全部打同一个不存在 ID
};

export default function () {
    const id = (Math.random() < 0.5) ? NONEXISTENT_ARTICLE : NONEXISTENT_CATEGORY;
    const path = (id === NONEXISTENT_ARTICLE) ? '/api/articles' : '/api/categories';

    const res = http.get(`${BASE}${path}/${id}`);
    check(res, {
        'returns 404': r => r.status === 404,
        'no 5xx / network error': r => r.status < 500,
    });
    sleep(0.05);
}
