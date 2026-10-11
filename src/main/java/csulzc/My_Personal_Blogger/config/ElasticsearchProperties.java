package csulzc.My_Personal_Blogger.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Elasticsearch 搜索模块配置。
 *
 * <p>前缀：{@code app.search.elasticsearch}，示例：</p>
 * <pre>
 * app:
 *   search:
 *     elasticsearch:
 *       enabled: true            # 是否启用 ES 搜索（false 时自动降级为 JPA LIKE）
 *       auto-create-index: true  # 启动时自动创建缺失的索引与 mapping
 *       reindex-on-startup: false# 启动时全量重建索引（首次部署/数据回填用）
 *       index-name: articles     # 索引名
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.search.elasticsearch")
public class ElasticsearchProperties {

    /** 是否启用 ES 全文检索；false 时搜索自动降级为数据库 LIKE 查询 */
    private boolean enabled = false;

    /** 启动时若索引不存在则自动创建（含 IK 分词 mapping） */
    private boolean autoCreateIndex = true;

    /** 启动时全量重建索引（先删后建，从数据库回灌），适合首次部署或数据修复 */
    private boolean reindexOnStartup = false;

    /** ES 索引名 */
    private String indexName = "articles";

    /** 全量重建时的每批读取大小 */
    private int reindexBatchSize = 500;
}
