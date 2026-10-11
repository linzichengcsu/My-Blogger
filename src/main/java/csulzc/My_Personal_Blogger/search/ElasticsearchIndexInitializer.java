package csulzc.My_Personal_Blogger.search;

import csulzc.My_Personal_Blogger.config.ElasticsearchProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Elasticsearch 索引启动初始化器。
 *
 * <p>仅在 {@code app.search.elasticsearch.enabled=true} 时注册：</p>
 * <ul>
 *   <li>{@code reindex-on-startup=true}：全量重建索引（删旧建新 + 数据库回灌）；</li>
 *   <li>否则若 {@code auto-create-index=true}：索引缺失时自动创建。</li>
 * </ul>
 *
 * <p>ES 未启动时初始化失败只记日志，不影响应用启动（搜索自动降级为 LIKE）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.search.elasticsearch.enabled", havingValue = "true")
public class ElasticsearchIndexInitializer implements ApplicationRunner {

    private final ArticleSearchService articleSearchService;
    private final ElasticsearchProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (properties.isReindexOnStartup()) {
                articleSearchService.reindexAll();
            } else if (properties.isAutoCreateIndex()) {
                articleSearchService.createIndexIfAbsent();
            }
        } catch (Exception e) {
            log.error("Elasticsearch 索引初始化异常（搜索将降级为数据库 LIKE）：{}", e.getMessage(), e);
        }
    }
}
