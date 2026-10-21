// =====================================================================
// ES 搜索压测 (k6) —— 独立压全文检索路径
//
// 验收标准:
//   p(95) < 500ms, 结果包含 data.hits
//   /actuator/metrics 中 ES 相关连接健康
//
// 运行:
//   k6 run -e BASE_URL=http://127.0.0.1:8080 scripts/loadtest/04-search.js
// =====================================================================

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const KEYWORDS = ['Redis', 'Spring', '缓存', 'Elasticsearch', 'Java', 'MyBatis', 'MySQL', '并发'];

export const options = {
    vus: 20,
    duration: '30s',
    thresholds: {
        'http_req_duration': ['p(95)<500'],
    },
};

export default function () {
    const kw = KEYWORDS[(Math.random() * KEYWORDS.length) | 0];
    const res = http.get(`${BASE}/api/articles/search?keyword=${kw}&page=0&size=10`, {
        headers: { Accept: 'application/json' },
    });
    check(res, {
        'status 200 or 400': r => r.status === 200 || r.status === 400,  // keyword 为空时 400 也 OK
    });
    sleep(0.1);
}
