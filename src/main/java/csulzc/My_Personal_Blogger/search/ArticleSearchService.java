package csulzc.My_Personal_Blogger.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.util.NamedValue;
import csulzc.My_Personal_Blogger.api.dto.article.ArticleListItemDTO;
import csulzc.My_Personal_Blogger.api.dto.common.PageResponseDTO;
import csulzc.My_Personal_Blogger.config.ElasticsearchProperties;
import csulzc.My_Personal_Blogger.domain.entity.Article;
import csulzc.My_Personal_Blogger.repository.ArticleRepository;
import csulzc.My_Personal_Blogger.service.business.ArticleListAssembler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 文章全文检索服务（Elasticsearch + IK 中文分词）。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>搜索：{@code multi_match}（title^3 / summary^2 / content）+ 高亮 + 分页；
 *       计数、时间等结构化排序交由数据库处理；ES 不可用（未启用 / 连接失败 /
 *       查询异常）时自动降级为数据库 LIKE 查询。</li>
 *   <li>索引同步：单篇索引 / 删除 / 批量索引 / 批量删除，供事务提交后的监听器调用；
 *       同步失败只记日志，绝不影响主流程。</li>
 *   <li>索引管理：启动时自动创建索引（IK mapping）、全量重建（删索引后从数据库回灌）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleSearchService {

    private static final String HIGHLIGHT_PRE = "<em class=\"es-highlight\">";
    private static final String HIGHLIGHT_POST = "</em>";

    private final ObjectProvider<ElasticsearchClient> clientProvider;
    private final ElasticsearchProperties properties;
    private final ArticleRepository articleRepository;
    private final ArticleListAssembler articleListAssembler;

    /**
     * ES 可用性熔断标志：同步/搜索失败时置 false，后续操作快速降级不再尝试连接；
     * 任一次 ES 操作成功后自动恢复 true。避免 ES 宕机时每次写操作都等待连接超时。
     */
    private volatile boolean esAvailable = true;

    // ========================================================================
    // 搜索
    // ========================================================================

    /**
     * 搜索文章。默认按相关度走 ES；显式指定结构化排序（时间 / 计数 / ID）时改走数据库，
     * 因为 ES 文档已不再冗余存储这些字段。ES 异常/未启用时同样降级为数据库 LIKE。
     */
    public PageResponseDTO<ArticleListItemDTO> search(String keyword, Pageable pageable) {
        if (!isEnabled() || pageable.getSort().isSorted()) {
            return searchByJpa(keyword, pageable);
        }
        try {
            PageResponseDTO<ArticleListItemDTO> result = searchByElasticsearch(keyword, pageable);
            esAvailable = true;
            return result;
        } catch (Exception e) {
            esAvailable = false;
            log.warn("Elasticsearch 搜索失败，降级为数据库 LIKE 搜索：{}", e.getMessage());
            return searchByJpa(keyword, pageable);
        }
    }

    /**
     * ES 全文检索：multi_match（IK 分词）+ 高亮 + 分页，按相关度（_score）返回。
     */
    private PageResponseDTO<ArticleListItemDTO> searchByElasticsearch(String keyword, Pageable pageable) throws IOException {
        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();
        int from = Math.max(page * size, 0);

        SearchResponse<ArticleDocument> response = client().search(s -> {
            s.index(indexName())
                    .query(q -> q.bool(b -> b
                            .must(mq -> mq.multiMatch(mm -> mm
                                    .query(keyword)
                                    .fields(List.of("title^3", "summary^2", "content"))
                                    .type(TextQueryType.BestFields)))
                            .filter(fq -> fq.term(t -> t.field("status").value("RELEASE")))))
                    .from(from)
                    .size(size)
                    .highlight(h -> h
                            .preTags(HIGHLIGHT_PRE)
                            .postTags(HIGHLIGHT_POST)
                            .fields(NamedValue.of("title", HighlightField.of(f -> f.fragmentSize(80).numberOfFragments(1))),
                                    NamedValue.of("summary", HighlightField.of(f -> f.fragmentSize(150).numberOfFragments(1))),
                                    NamedValue.of("content", HighlightField.of(f -> f.fragmentSize(150).numberOfFragments(1)))));
            return s;
        }, ArticleDocument.class);

        List<Long> orderedIds = new ArrayList<>();
        Map<Long, Map<String, String>> highlights = new LinkedHashMap<>();
        for (Hit<ArticleDocument> hit : response.hits().hits()) {
            Long articleId = parseArticleId(hit.id());
            if (articleId == null) {
                continue;
            }
            orderedIds.add(articleId);

            Map<String, List<String>> hl = hit.highlight();
            if (hl != null && !hl.isEmpty()) {
                Map<String, String> fragment = new HashMap<>();
                hl.forEach((field, frags) -> {
                    if (frags != null && !frags.isEmpty()) {
                        fragment.put(field, frags.get(0));
                    }
                });
                if (!fragment.isEmpty()) {
                    highlights.put(articleId, fragment);
                }
            }
        }

        long total = response.hits().total() != null ? response.hits().total().value() : orderedIds.size();
        return articleListAssembler.buildSearchResponse(orderedIds, highlights, total, page, size);
    }

    /**
     * ES 文档 _id 即文章 ID。
     */
    private Long parseArticleId(String docId) {
        if (docId == null) {
            return null;
        }
        try {
            return Long.valueOf(docId);
        } catch (NumberFormatException e) {
            log.warn("Elasticsearch 返回了非法文章 ID：{}", docId);
            return null;
        }
    }

    /**
     * 降级搜索：JPA LIKE（仅标题），仅返回已发布文章。
     */
    private PageResponseDTO<ArticleListItemDTO> searchByJpa(String keyword, Pageable pageable) {
        Page<Article> articlePage = articleRepository.findByTitleContainingAndStatus(
                keyword, Article.ArticleStatus.RELEASE, pageable);
        return articleListAssembler.buildPageResponse(articlePage);
    }

    // ========================================================================
    // 索引同步（供事务提交后的监听器调用；失败只记日志，不影响主流程）
    // ========================================================================

    /**
     * 索引 / 更新单篇文章。
     */
    public void indexArticle(ArticleIndexPayload payload) {
        if (!isEnabled() || payload == null || payload.getArticleId() == null) {
            return;
        }
        try {
            client().index(IndexRequest.of(i -> i
                    .index(indexName())
                    .id(String.valueOf(payload.getArticleId()))
                    .document(toDocument(payload))));
            esAvailable = true;
        } catch (Exception e) {
            esAvailable = false;
            log.warn("索引文章 {} 到 Elasticsearch 失败：{}", payload.getArticleId(), e.getMessage());
        }
    }

    /**
     * 删除单篇文章的索引文档。
     */
    public void deleteArticle(Long articleId) {
        if (!isEnabled() || articleId == null) {
            return;
        }
        try {
            client().delete(d -> d.index(indexName()).id(String.valueOf(articleId)));
            esAvailable = true;
        } catch (Exception e) {
            esAvailable = false;
            log.warn("从 Elasticsearch 删除文章 {} 失败：{}", articleId, e.getMessage());
        }
    }

    /**
     * 批量索引文章（bulk API）。
     */
    public void bulkIndex(List<ArticleIndexPayload> payloads) {
        if (!isEnabled() || payloads == null || payloads.isEmpty()) {
            return;
        }
        try {
            BulkRequest.Builder br = new BulkRequest.Builder();
            for (ArticleIndexPayload payload : payloads) {
                if (payload.getArticleId() == null) {
                    continue;
                }
                ArticleDocument doc = toDocument(payload);
                br.operations(op -> op.index(idx -> idx
                        .index(indexName())
                        .id(String.valueOf(payload.getArticleId()))
                        .document(doc)));
            }
            client().bulk(br.build());
            esAvailable = true;
        } catch (Exception e) {
            esAvailable = false;
            log.warn("批量索引 {} 篇文章到 Elasticsearch 失败：{}", payloads.size(), e.getMessage());
        }
    }

    /**
     * 批量删除文章索引文档（bulk API）。
     */
    public void bulkDelete(List<Long> articleIds) {
        if (!isEnabled() || articleIds == null || articleIds.isEmpty()) {
            return;
        }
        try {
            BulkRequest.Builder br = new BulkRequest.Builder();
            for (Long id : articleIds) {
                br.operations(op -> op.delete(del -> del.index(indexName()).id(String.valueOf(id))));
            }
            client().bulk(br.build());
            esAvailable = true;
        } catch (Exception e) {
            esAvailable = false;
            log.warn("从 Elasticsearch 批量删除 {} 篇文章失败：{}", articleIds.size(), e.getMessage());
        }
    }

    // ========================================================================
    // 索引管理（启动初始化 / 全量重建）
    // ========================================================================

    /**
     * 索引不存在时自动创建（含 IK 分词 mapping）。失败仅告警，不阻断应用启动。
     */
    public void createIndexIfAbsent() {
        if (!isEnabled()) {
            return;
        }
        try {
            boolean exists = client().indices().exists(e -> e.index(indexName())).value();
            if (!exists) {
                createIndex();
                log.info("已创建 Elasticsearch 索引：{}", indexName());
            }
        } catch (Exception e) {
            log.warn("创建 Elasticsearch 索引失败（可稍后按 docs/elasticsearch-setup.md 手动创建）：{}", e.getMessage());
        }
    }

    /**
     * 全量重建索引：删除旧索引 → 按 mapping 重建 → 分页从数据库回灌全部文章。
     * 适合首次部署或数据修复（配置 {@code app.search.elasticsearch.reindex-on-startup=true} 触发）。
     */
    public void reindexAll() {
        if (!isEnabled()) {
            return;
        }
        log.info("开始全量重建 Elasticsearch 索引：{}", indexName());
        try {
            try {
                client().indices().delete(d -> d.index(indexName()));
            } catch (Exception ignored) {
                // 索引可能尚不存在
            }
            createIndex();

            int page = 0;
            int total = 0;
            int batchSize = properties.getReindexBatchSize();
            while (true) {
                Pageable pageable = PageRequest.of(page, batchSize, Sort.by(Sort.Direction.ASC, "id"));
                Page<Article> batch = articleRepository.findAllWithAuthor(pageable);
                if (batch.getContent().isEmpty()) {
                    break;
                }
                List<ArticleIndexPayload> payloads = batch.getContent().stream()
                        .map(ArticleIndexPayload::from)
                        .toList();
                bulkIndex(payloads);
                total += payloads.size();
                if (batch.isLast()) {
                    break;
                }
                page++;
            }
            log.info("全量重建 Elasticsearch 索引完成，共 {} 篇文章", total);
        } catch (Exception e) {
            log.error("全量重建 Elasticsearch 索引失败：{}", e.getMessage(), e);
        }
    }

    private void createIndex() throws IOException {
        String mappingJson = loadMappingJson();
        try (Reader reader = new StringReader(mappingJson)) {
            CreateIndexRequest request = CreateIndexRequest.of(c -> c.index(indexName()).withJson(reader));
            client().indices().create(request);
        }
    }

    private String loadMappingJson() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/elasticsearch/articles-mapping.json")) {
            if (in == null) {
                throw new IllegalStateException("找不到索引 mapping 资源：/elasticsearch/articles-mapping.json");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 组装 ES 文档：只保留全文检索所需的字符串字段。
     */
    private ArticleDocument toDocument(ArticleIndexPayload payload) {
        return ArticleDocument.builder()
                .title(payload.getTitle())
                .summary(payload.getSummary())
                .content(payload.getContent())
                .status(payload.getStatus())
                .build();
    }

    private ElasticsearchClient client() {
        return clientProvider.getObject();
    }

    private boolean isEnabled() {
        return properties.isEnabled() && esAvailable && clientProvider.getIfAvailable() != null;
    }

    private String indexName() {
        return properties.getIndexName();
    }
}
