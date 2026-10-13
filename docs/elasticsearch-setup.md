# Elasticsearch + IK 全文检索接入说明

本文档说明本项目文章搜索模块如何从「数据库 LIKE 模糊匹配」升级为「Elasticsearch 全文检索」。

## 1. 架构总览

```
浏览器
  │  GET /api/articles/search?keyword=xxx
  ▼
ArticleController.searchArticles
  ▼
ArticleService.searchArticles
  ▼
ArticleSearchService.search        ← 核心入口
  │  ├─ enabled=false 或 ES 不可用 ──► 降级 JPA LIKE（原行为，仅标题）
  │  └─ enabled=true ──► ES multi_match（title^3 / summary^2 / content）+ 高亮 + 相关度排序
  ▼
PageResponseDTO<ArticleListItemDTO>（含 highlightTitle / highlightSummary）

写入侧（事务提交后最终一致）：
create / update / publish / archive / delete / batch*（ArticleService）
  └─ 事务内发布 ArticleIndexEvent
       └─ @TransactionalEventListener(AFTER_COMMIT) 消费
            └─ ArticleSearchService.indexArticle / bulkIndex / deleteArticle / bulkDelete
```

- 索引名：`articles`（可配置 `app.search.elasticsearch.index-name`）
- 中文分词：IK 插件（`ik_max_word` 索引 / `ik_smart` 搜索）
- 高亮：命中片段以 `<em class="es-highlight">...</em>` 包裹，返回在 `highlightTitle` / `highlightSummary`
- 降级策略：ES 未启用 / 连接失败 / 查询异常时，搜索自动回退为数据库 `LIKE`，写操作同步失败只记日志不影响主流程
- 熔断：ES 连续失败后进入熔断（不再逐请求等待连接超时），任一次 ES 操作成功自动恢复

## 2. 版本选型（重要）

| 组件 | 版本 | 说明 |
|---|---|---|
| Spring Boot | 4.1.1 | 项目现有版本 |
| spring-boot-starter-data-elasticsearch | 4.1.1（BOM 管理） | 传递引入 elasticsearch-java 9.4.5 |
| **Elasticsearch 服务端** | **9.4.5** | 与客户端同版本，兼容性最佳 |
| **IK 分词插件** | **9.4.5** | 与 ES 版本一一对应 |

> ⚠️ ES 服务端版本必须与 `elasticsearch-java` 客户端（9.4.5）匹配，否则可能因 API 不兼容报错。
> IK 插件版本必须与 ES 服务端版本一致（`elasticsearch-analysis-ik-9.x.y.zip`）。

## 3. 服务端部署

### 3.1 Windows（本地开发）

1. 下载 Elasticsearch 9.4.5 发行包并解压：
   - 官方：<https://www.elastic.co/downloads/elasticsearch>
2. 安装 IK 中文分词插件（版本与 ES 一致）：
   ```bash
   cd <ES_HOME>
   bin\elasticsearch-plugin.bat install https://get.infini.cloud/elasticsearch/analysis-ik/9.4.5
   ```
3. 启动 ES（需 JDK 21+，ES 自带捆绑 JDK 则无需配置）：
   ```bash
   bin\elasticsearch.bat
   ```
4. 验证（出现 9200 端口即成功）：
   ```bash
   curl http://localhost:9200
   # 期望输出带 version.number = 9.4.5 的 JSON
   ```
5. 验证 IK 已安装：
   ```bash
   curl "http://localhost:9200/_analyze" -H "Content-Type: application/json" \
     -d "{\"text\":\"我是中国人\",\"analyzer\":\"ik_smart\"}"
   ```

### 3.2 Linux（生产，参考）

```bash
# 下载并解压
wget https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-9.4.5-linux-x86_64.tar.gz
tar -xzf elasticsearch-9.4.5-linux-x86_64.tar.gz
cd elasticsearch-9.4.5

# 安装 IK 插件
bin/elasticsearch-plugin install https://get.infini.cloud/elasticsearch/analysis-ik/9.4.5

# 建议以非 root 运行并配置 systemd / supervisor
# 生产建议同时配置 X-Pack 安全（见 3.3）
```

### 3.3 开启安全认证（可选，生产建议）

启用 X-Pack 安全后，需在 `config/elasticsearch.yml` 配置：

```yaml
xpack.security.enabled: true
```

并在应用侧 `application-prod.yml` 配置：

```yaml
spring:
  elasticsearch:
    uris: https://<es-host>:9200
    username: ${ELASTICSEARCH_USERNAME}
    password: ${ELASTICSEARCH_PASSWORD}
```

## 4. 应用配置

### 4.1 配置项

| 配置项 | 默认 | 说明 |
|---|---|---|
| `spring.elasticsearch.uris` | `http://localhost:9200` | ES 地址，可用 `ELASTICSEARCH_URIS` 环境变量覆盖 |
| `spring.elasticsearch.username/password` | 空 | 开启 X-Pack 时填写 |
| `app.search.elasticsearch.enabled` | `false` | 是否启用 ES 搜索；`false` 时降级为 JPA LIKE |
| `app.search.elasticsearch.auto-create-index` | `true` | 启动时索引缺失则自动创建 |
| `app.search.elasticsearch.reindex-on-startup` | `false` | 启动时全量重建索引（首次部署/修复用） |
| `app.search.elasticsearch.index-name` | `articles` | 索引名 |
| `app.search.elasticsearch.reindex-batch-size` | `500` | 全量重建每批大小 |

- dev / prod 均已默认 `enabled: true`；测试环境（`test` profile）为 `false`，CI 无需 ES。
- 若本地未启动 ES 就运行 dev，搜索与写操作会自动降级/跳过（有 WARN 日志），不影响功能。

### 4.2 首次部署流程

1. 启动 ES 并安装 IK（见第 3 节）。
2. 将生产配置 `app.search.elasticsearch.reindex-on-startup` 临时改为 `true` 并启动应用，
   应用启动时会删除旧索引、按 mapping 重建、并从数据库全量回灌所有文章。
3. 确认日志输出 `全量重建 Elasticsearch 索引完成，共 N 篇文章` 后，将 `reindex-on-startup` 改回 `false` 再重启。

> 日常的文章增删改会通过事务事件自动同步索引，无需手工干预。

## 5. 搜索效果与接口

- 接口不变：`GET /api/articles/search?keyword=xxx&page=0&size=10[&sortBy=...&sortDirection=...]`
- 默认按相关度（`_score`）排序；指定 `sortBy`（createdAt/likeCount/viewCount 等）时按字段排序。
- 响应新增字段：`highlightTitle`、`highlightSummary`（可为空），包含 `<em class="es-highlight">` 高亮片段。
- 关键词最长 100 字符，超长返回 400。

## 6. 运维与常见问题

| 现象 | 原因 / 处理 |
|---|---|
| 日志出现「Elasticsearch 搜索失败，降级为数据库 LIKE 搜索」 | ES 未启动或连接异常；启动 ES 后，下一次搜索会自动恢复 |
| 写操作后搜索不到新文章 | 事件在事务提交后同步（AFTER_COMMIT），是**最终一致**；若 ES 当时不可用则索引丢失，需手动重建 |
| 中文搜索无结果 | 确认 IK 插件已安装且与 ES 版本一致；确认索引 mapping 的 analyzer 为 `ik_max_word` |
| 想完全禁用 ES | 将 `app.search.elasticsearch.enabled` 设为 `false`（恢复纯 LIKE 搜索） |
| 数据与 ES 不一致 | 用 `reindex-on-startup: true` 全量重建 |

## 7. 相关文件

| 文件 | 作用 |
|---|---|
| `search/ArticleSearchService.java` | 搜索 / 索引同步 / 索引管理核心 |
| `search/ArticleIndexEvent.java` + `ArticleIndexPayload.java` | 同步事件与载荷 |
| `search/ArticleIndexEventListener.java` | 事务提交后消费事件 |
| `search/ArticleDocument.java` | ES 文档模型 |
| `search/ElasticsearchIndexInitializer.java` | 启动建索引 / 全量重建 |
| `config/ElasticsearchProperties.java` | 配置属性绑定 |
| `resources/elasticsearch/articles-mapping.json` | 索引 mapping（IK 分词） |
| `service/ArticleListAssembler.java` | 列表 DTO 组装（JPA 与 ES 共用） |
