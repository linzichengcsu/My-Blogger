// =====================================================================
// Blog 主力压测脚本 (k6)
//
// 覆盖场景 (按 docs/loadtest-plan.md 中的 S1-S4):
//   1. ArticleDetail     - 文章详情 (GET /api/articles/{id})       测缓存命中
//   2. ArticleList       - 文章列表 (GET /api/articles?page&size)  测分页缓存
//   3. AuthorList        - 作者文章列表 (GET /api/articles/author/{id})  测身份感知 key
//   4. Search            - ES 全文检索 (GET /api/articles/search?keyword=Redis)
//   5. Categories        - 收藏夹列表 + 统计 (测 category:list 缓存)
//
// 运行前提：
//   - 应用已以 loadtest profile 启动，且种子数据灌入完成
//   - loadtest profile 启动时灌入 1000 篇种子文章，ID 范围 1-1000
//
// 运行示例:
//   k6 run -e BASE_URL=http://127.0.0.1:8080 scripts/loadtest/main.js
//   k6 run -e BASE_URL=http://127.0.0.1:8080 -e DURATION=2m -e RPS=50 scripts/loadtest/main.js
//   k6 run --iterations 1000 --vus 50 scripts/loadtest/main.js   # 固定次数回归
//
// 关键环境变量:
//   BASE_URL     默认 http://127.0.0.1:8080
//   DURATION     默认 60s
//   VUS          默认 20
//   KEYWORD      默认 "Redis"
//   HOT_ARTICLE  默认 1 (种子文章 ID 起点; loadtest 灌入 ID 1-1000)
// =====================================================================

import http from 'k6/http';
import { check, sleep, group } from 'k6';

const BASE     = __ENV.BASE_URL      || 'http://127.0.0.1:8080';
const DURATION = __ENV.DURATION      || '60s';
const VUS      = parseInt(__ENV.VUS) || 20;
const KEYWORD  = __ENV.KEYWORD       || 'Redis';
const HOT      = parseInt(__ENV.HOT_ARTICLE) || 1;

// 管理员账号：loadtest profile 下 SecurityConfig 已对 GET /api/categories/** 放行，
// 但脚本统一登录拿 Bearer token 复用管理员身份（LoadTestDataSeeder 启动时把 admin
// 密码重置为已知值），供所有 VU 复用，也覆盖其它需鉴权的端点。
const ADMIN_USER = __ENV.LT_ADMIN_USER || 'admin';
const ADMIN_PASS = __ENV.LT_ADMIN_PASS || 'LoadTest123!';

export const options = {
    // 让 k6 额外输出 p(99)，供摘要展示（默认只算到 p(95)）
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
    thresholds: {
        // 核心阈值 —— 首次跑先用宽松值，拿到基线后再收紧
        'http_req_duration': ['p(95)<500', 'p(99)<1500'],
        'http_req_failed':    ['rate<0.01'],       // 错误率 < 1%

        // 分场景阈值（用 group 名区分；sleep 已移到 group 外，group_duration 仅含请求耗时）
        // 首次基线允许冷缓存回源：1000 篇种子在低 VU 短时跑里大部分是 miss，拿到基线后再收紧
        'group_duration{group:::ArticleDetail}': ['p(95)<500'],
        'group_duration{group:::ArticleList}':    ['p(95)<500'],
        'group_duration{group:::AuthorList}':     ['p(95)<500'],
        'group_duration{group:::Search}':         ['p(95)<1000'],
        'group_duration{group:::Categories}':     ['p(95)<500'],
    },
    scenarios: {
        // 恒定速率持续压测 —— 模拟真实用户访问模式
        steady_load: {
            executor: 'constant-vus',
            vus: VUS,
            duration: DURATION,
        },
    },
};

// ---- 每个 VU 初始化一次：选定要访问的文章/作者 ID（固定范围，让缓存有机会命中）----
function pickArticleId() {
    // 种子文章共 1000 篇 (ID 1-1000)，用固定范围让缓存命中率有意义
    return HOT + ((Math.random() * 1000) | 0);     // 1-1000
}
function pickPage() {
    return (Math.random() * 20) | 0;               // 0-19
}
function pickAuthorId() {
    return 1 + ((Math.random() * 10) | 0);         // 1-10 (lt_author1..10)
}

// ---- 工具函数：封装一次 GET，统一 check；extraHeaders 用于鉴权等 ----
function getJson(url, extraHeaders) {
    const res = http.get(url, {
        headers: Object.assign({ Accept: 'application/json' }, extraHeaders || {}),
        timeout: '10s',
    });
    check(res, {
        'status is 200':     r => r.status === 200,
        'body has Result':   r => r.body && r.body.includes('"success"'),
    });
    return res;
}

// ---- setup: 所有 VU 启动前登录一次管理员，拿 token 供鉴权端点复用 ----
export function setup() {
    const res = http.post(`${BASE}/api/users/login`, JSON.stringify({
        username: ADMIN_USER,
        password: ADMIN_PASS,
    }), {
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        timeout: '10s',
    });
    const token = res.json('data.accessToken');
    if (res.status !== 200 || !token) {
        throw new Error(`管理员登录失败: status=${res.status} body=${res.body}`);
    }
    return { token };
}

// ---- 主迭代：每个 VU 在 duration 内反复执行 ----
export default function (data) {
    const artId = pickArticleId();
    const page  = pickPage();
    const auth  = pickAuthorId();

    // 注意：sleep 放在 group 外，避免被计入 group_duration（该指标只应反映请求耗时）。
    const authHeaders = { Authorization: `Bearer ${data.token}` };

    // 1. 文章详情 —— 最频繁的读路径
    group('ArticleDetail', function () {
        getJson(`${BASE}/api/articles/${artId}`);
    });
    sleep(0.1);

    // 2. 公开文章列表（分页）
    group('ArticleList', function () {
        getJson(`${BASE}/api/articles?page=${page}&size=10`);
    });
    sleep(0.1);

    // 3. 按作者查文章（带权限感知的缓存 key）
    group('AuthorList', function () {
        getJson(`${BASE}/api/articles/author/${auth}?page=0&size=10`);
    });
    sleep(0.1);

    // 4. ES 全文检索
    group('Search', function () {
        getJson(`${BASE}/api/articles/search?keyword=${KEYWORD}&page=0&size=10`);
    });
    sleep(0.1);

    // 5. 收藏夹列表 + 统计（管理员专属，测 category:list 缓存）
    group('Categories', function () {
        getJson(`${BASE}/api/categories?page=0&size=10`, authHeaders);
        getJson(`${BASE}/api/categories/statistics`, authHeaders);
    });
    sleep(0.1);
}

// ---- 摘要信息：一次跑后输出总请求数与关键指标 ----
// 注意: k6 新版 handleSummary 的数据结构是 data.metrics.<指标>.values,
// 迭代数在 metrics.iterations.values.count (不是 root_group), 分位键名带括号 p(95)。
export function handleSummary(data) {
    const m = (data && data.metrics) || {};
    const get = (path) => path.split('.').reduce((o, k) => (o == null ? undefined : o[k]), m);
    const dur = get('http_req_duration.values') || {};
    const p = (k) => {
        if (dur[k] != null) return dur[k];
        const flat = dur[k.replace('(', '').replace(')', '')];
        if (flat != null) return flat;
        if (k === 'p(50)' && dur['med'] != null) return dur['med'];   // k6 中位数键名为 med
        return undefined;
    };
    const num = (v) => (v == null || isNaN(v) ? null : Number(v));
    const fmt = (v, d = 2) => { const n = num(v); return n == null ? 'N/A' : n.toFixed(d); };

    console.log('\n====== 压测摘要 ======');
    console.log(`迭代总数:  ${fmt(get('iterations.values.count'), 0)}`);
    console.log(`VUs 峰值:  ${fmt(get('vus_max.values.max'), 0)}`);
    console.log(`总 RPS:    ${fmt(get('http_reqs.values.rate'))}`);
    const failed = num(get('http_req_failed.values.rate'));
    console.log(`错误率:    ${failed == null ? 'N/A' : (failed * 100).toFixed(2)}%`);
    console.log(`P50/P95/P99: ${fmt(p('p(50)'), 1)} / ${fmt(p('p(95)'), 1)} / ${fmt(p('p(99)'), 1)} ms`);
    return {};   // 不生成额外文件
}
