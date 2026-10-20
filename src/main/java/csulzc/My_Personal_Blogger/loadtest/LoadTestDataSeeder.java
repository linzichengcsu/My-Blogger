package csulzc.My_Personal_Blogger.loadtest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 压测种子数据灌入器（仅 {@code loadtest} profile 生效）。
 *
 * <p>压测结论是否有意义，取决于数据量级是否接近真实：空库或几十条数据下，
 * 缓存命中率、索引选择、连接池竞争全都测不出来。本类在应用启动时批量灌入：</p>
 * <ul>
 *   <li>10 个作者账号（统一密码 {@value #AUTHOR_PASSWORD}，用户名 lt_author1..10）</li>
 *   <li>8 个顶级收藏夹 + 4 个子收藏夹</li>
 *   <li>可配置数量的已发布文章（默认 1000 篇，见 {@code app.loadtest.seed.article-count}）</li>
 *   <li>文章-收藏夹关联</li>
 * </ul>
 *
 * <p>执行顺序：{@link Order} 设为最高优先级，先于 {@code ElasticsearchIndexInitializer}
 * 执行，从而保证 loadtest profile 开启的 reindex-on-startup 能把种子文章全部灌入 ES。</p>
 *
 * <p>幂等：库中已有文章时直接跳过，重复启动不会重复灌入。</p>
 *
 * <p>安全：仅在 loadtest profile 下注册，dev / prod 环境永远不会执行。</p>
 */
@Slf4j
@Component
@Profile("loadtest")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.loadtest.seed.enabled", havingValue = "true", matchIfMissing = false)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoadTestDataSeeder implements ApplicationRunner {

    /** 压测统一账号密码：bcrypt 哈希由 BCryptPasswordEncoder 预生成并校验通过 */
    private static final String AUTHOR_PASSWORD = "LoadTest123!";
    private static final String AUTHOR_PASSWORD_HASH =
            "$2a$10$aOgGRvApnZAgKrl2ObUf7.Pkx9YmGwA7Tm4lC0ZSL71sbdLKqp9.m";

    private static final int AUTHOR_COUNT = 10;
    private static final int TOP_CATEGORY_COUNT = 8;
    private static final int CHILD_CATEGORY_COUNT = 4;

    private final JdbcTemplate jdbcTemplate;

    @Value("${app.loadtest.seed.article-count:1000}")
    private int articleCount;

    @Override
    public void run(ApplicationArguments args) {
        Integer existing = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM articles", Integer.class);
        if (existing != null && existing > 0) {
            log.info("压测种子数据已存在（{} 篇文章），跳过灌入", existing);
            return;
        }

        long startMs = System.currentTimeMillis();
        log.info("开始灌入压测种子数据：{} 位作者 / {} 篇已发布文章", AUTHOR_COUNT, articleCount);

        List<Long> authorIds = insertAuthors();
        List<Long> categoryIds = insertCategories(authorIds.get(0));
        List<Long> articleIds = insertArticles(authorIds);
        insertArticleCategories(articleIds, categoryIds);

        log.info("压测种子数据灌入完成：作者 {} / 收藏夹 {} / 文章 {}，耗时 {}ms",
                authorIds.size(), categoryIds.size(), articleIds.size(), System.currentTimeMillis() - startMs);
        log.info("压测登录账号：lt_author1 ~ lt_author{}，统一密码：{}", AUTHOR_COUNT, AUTHOR_PASSWORD);
    }

    private List<Long> insertAuthors() {
        LocalDateTime now = LocalDateTime.now();
        List<Object[]> batch = new ArrayList<>(AUTHOR_COUNT);
        for (int i = 1; i <= AUTHOR_COUNT; i++) {
            batch.add(new Object[]{
                    now, now,
                    "lt_author" + i,
                    AUTHOR_PASSWORD_HASH,
                    "lt_author" + i + "@loadtest.local",
                    "压测作者" + i
            });
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO users (version, created_at, updated_at, username, password_hash,
                                   email, display_name, status, role)
                VALUES (0, ?, ?, ?, ?, ?, ?, 'ACTIVE', 'USER')
                """, batch);
        return queryIds("SELECT id FROM users WHERE username LIKE 'lt_author%' ORDER BY id");
    }

    private List<Long> insertCategories(Long ownerId) {
        LocalDateTime now = LocalDateTime.now();
        String[] topNames = {"Java", "Spring", "数据库", "中间件", "算法", "架构", "运维", "随笔"};
        String[] topDescs = {"Java 语言相关", "Spring 生态", "数据库与存储", "Redis / MQ 等中间件",
                "数据结构与算法", "系统架构设计", "部署与运维", "杂谈随笔"};

        List<Object[]> topBatch = new ArrayList<>(TOP_CATEGORY_COUNT);
        for (int i = 0; i < TOP_CATEGORY_COUNT; i++) {
            topBatch.add(new Object[]{now, now, "lt-" + topNames[i], topDescs[i], ownerId});
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO categories (version, created_at, updated_at, name, description, owner_id)
                VALUES (0, ?, ?, ?, ?, ?)
                """, topBatch);

        List<Long> topIds = queryIds("SELECT id FROM categories WHERE name LIKE 'lt-%' AND parent_category_id IS NULL ORDER BY id");

        List<Object[]> childBatch = new ArrayList<>(CHILD_CATEGORY_COUNT);
        String[] childNames = {"Java 并发", "JVM 调优", "Spring 缓存", "SQL 优化"};
        for (int i = 0; i < CHILD_CATEGORY_COUNT; i++) {
            Long parentId = topIds.get(i % topIds.size());
            childBatch.add(new Object[]{now, now, "lt-" + childNames[i], "子收藏夹：" + childNames[i], ownerId, parentId});
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO categories (version, created_at, updated_at, name, description, owner_id, parent_category_id)
                VALUES (0, ?, ?, ?, ?, ?, ?)
                """, childBatch);

        return queryIds("SELECT id FROM categories WHERE name LIKE 'lt-%' ORDER BY id");
    }

    private List<Long> insertArticles(List<Long> authorIds) {
        LocalDateTime now = LocalDateTime.now();
        List<Object[]> batch = new ArrayList<>(articleCount);
        for (int i = 1; i <= articleCount; i++) {
            // 创建时间沿时间轴散开（每篇间隔 7 分钟），避免分页排序时全挤在同一时刻
            LocalDateTime createdAt = now.minusMinutes((long) i * 7);
            int authorId = 1 + (i % AUTHOR_COUNT);
            batch.add(new Object[]{
                    createdAt, createdAt,
                    "压测文章 #" + i + " Redis 缓存雪崩击穿穿透与 Elasticsearch 检索验证",
                    "第 " + i + " 篇压测正文：覆盖缓存命中、缓存驱逐、全文检索、分页与排序等典型读路径，"
                            + "内容长度控制在列宽限制内以模拟真实博客文章。",
                    "第 " + i + " 篇压测摘要：关键词 Redis 缓存 Elasticsearch 压测",
                    i % 500,
                    i % 200,
                    (i * 37) % 10000
            });
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO articles (version, created_at, updated_at, title, content, summary,
                                      status, like_count, favorite_count, view_count, author_id)
                VALUES (0, ?, ?, ?, ?, ?, 'RELEASE', ?, ?, ?, ?)
                """, batch);
        return queryIds("SELECT id FROM articles ORDER BY id DESC LIMIT " + articleCount);
    }

    private void insertArticleCategories(List<Long> articleIds, List<Long> categoryIds) {
        List<Object[]> batch = new ArrayList<>(articleIds.size());
        for (int i = 0; i < articleIds.size(); i++) {
            batch.add(new Object[]{articleIds.get(i), categoryIds.get(i % categoryIds.size())});
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO article_category (article_id, category_id) VALUES (?, ?)", batch);
    }

    private List<Long> queryIds(String sql) {
        return new ArrayList<>(jdbcTemplate.queryForList(sql, Long.class));
    }
}
