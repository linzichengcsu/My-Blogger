package csulzc.My_Personal_Blogger.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文章浏览/点赞计数模块配置。
 *
 * <pre>
 * app:
 *   article-stat:
 *     enabled: true            # Redis 计数与定时落库开关；false 时降级为数据库同步更新
 *     flush-interval-ms: 30000 # 定时批量落库间隔
 *     flush-batch-size: 500    # 单次最多处理多少篇文章/点赞事件
 *     like-cache-ttl-seconds: 2592000 # Redis 点赞去重集合的 TTL（30 天）
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.article-stat")
public class ArticleStatProperties {

    /** 是否启用 Redis 计数；关闭或 Redis 异常时同步降级为数据库更新 */
    private boolean enabled = true;

    /** 定时批量落库间隔（毫秒） */
    private long flushIntervalMs = 30_000;

    /** 单次落库最多处理的文章数 / 点赞事件数 */
    private int flushBatchSize = 500;

    /** 点赞去重集合在 Redis 中的过期时间（秒），过期后由数据库唯一约束兜底 */
    private long likeCacheTtlSeconds = 30L * 24 * 60 * 60;
}
