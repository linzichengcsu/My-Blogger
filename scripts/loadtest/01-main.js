// =====================================================================
// Blog 主力压测脚本 (k6)
//
// 覆盖场景 (按 docs/loadtest-plan.md 中的 S1-S4):
//   1. ArticleDetail     - 文章详情 (GET /api/articles/{id})       测缓存命中
//   2. ArticleList       - 文章列表 (GET /api/articles?page&size)  测分页缓存
//   3. AuthorList        - 作者文章列表 (GET /api/articles/author/{id})  测身份感知 key
//   4. Search            - ES 全文检索 (GET /api/articles/search?keyword=Redis)
//   5. Categories        - 收藏夹列表 + 树形结构 (测 category:list + category:tree)
//
// 运行前提：
//   - 应用已以 loadtest profile 启动，且种子数据灌入完成
//   - 默认种子文章 ID 从 1001 开始 (10 作者 + 迁移记录 + admin 记录后)；
//     可用 ./scripts/loadtest/bootstrap.js 先跑一遍拿真实 ID
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
//   HOT_ARTICLE  默认 1001 (一个确定存在的种子文章 ID)
// =====================================================================

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE     = __ENV.BASE_URL      || 'http://127.0.0.1:8080';
const DURATION = __ENV.DURATION      || '60s';
const VUS      = parseInt(__ENV.VUS) || 20;
const KEYWORD  = __ENV.KEYWORD       || 'Redis';
const HOT      = parseInt(__ENV.HOT_ARTICLE) || 1001;

export const options = {
    thresholds: {
        // 核心阈值 —— 首次跑先用宽松值，拿到基线后再收紧
        'http_req_duration': ['p(95)<500', 'p(99)<1500'],
        'http_req_failed':    ['rate<0.01'],       // 错误率 < 1%

        // 分场景阈值（用 group 名区分）
        'group_duration{group:::ArticleDetail}': ['p(95)<200'],
        'group_duration{group:::ArticleList}':    ['p(95)<300'],
        'group_duration{group:::Search}':         ['p(95)<500'],
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
    // 种子文章从 HOT 开始，用固定范围让缓存命中率有意义
    return HOT + ((Math.random() * 200) | 0);     // 1001-1200
}
function pickPage() {
    return (Math.random() * 20) | 0;               // 0-19
}
function pickAuthorId() {
    return 1 + ((Math.random() * 10) | 0);         // 1-10 (lt_author1..10)
}

// ---- 工具函数：封装一次 GET，统一 check ----
function getJson(url) {
    const res = http.get(url, {
        headers: { Accept: 'application/json' },
        timeout: '10s',
    });
    check(res, {
        'status is 200':     r => r.status === 200,
        'body has Result':   r => r.body && r.body.includes('"success"'),
    });
    return res;
}

// ---- 主迭代：每个 VU 在 duration 内反复执行 ----
export default function () {
    const artId = pickArticleId();
    const page  = pickPage();
    const auth  = pickAuthorId();

    // 1. 文章详情 —— 最频繁的读路径
    group('ArticleDetail', function () {
        getJson(`${BASE}/api/articles/${artId}`);
        sleep(0.1);
    });

    // 2. 公开文章列表（分页）
    group('ArticleList', function () {
        getJson(`${BASE}/api/articles?page=${page}&size=10`);
        sleep(0.1);
    });

    // 3. 按作者查文章（带权限感知的缓存 key）
    group('AuthorList', function () {
        getJson(`${BASE}/api/articles/author/${auth}?page=0&size=10`);
        sleep(0.1);
    });

    // 4. ES 全文检索
    group('Search', function () {
        getJson(`${BASE}/api/articles/search?keyword=${KEYWORD}&page=0&size=10`);
        sleep(0.1);
    });

    // 5. 收藏夹列表 + 树形结构
    group('Categories', function () {
        getJson(`${BASE}/api/categories/top-level`);
        sleep(0.05);
        getJson(`${BASE}/api/categories/tree`);
        sleep(0.05);
    });
}

// ---- 摘要信息：一次跑后输出总请求数与各 group 的 RPS ----
export function handleSummary(data) {
    console.log('\n====== 压测摘要 ======');
    console.log(`迭代总数:  ${data.root_group.iterations}`);
    console.log(`VUs 峰值:  ${data.root_group.vus_max}`);
    console.log(`总 RPS:    ${data.metrics.http_reqs.values.rps.toFixed(2)}`);
    console.log(`错误率:    ${(data.metrics.http_req_failed.values.rate * 100).toFixed(2)}%`);
    console.log(`P50/P95/P99: ${data.metrics.http_req_duration.values.p50.toFixed(1)} / ` +
                `${data.metrics.http_req_duration.values.p95.toFixed(1)} / ` +
                `${data.metrics.http_req_duration.values.p99.toFixed(1)} ms`);
    return {};   // 不生成额外文件
}
