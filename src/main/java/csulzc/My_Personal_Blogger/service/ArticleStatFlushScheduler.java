package csulzc.My_Personal_Blogger.service;

import csulzc.My_Personal_Blogger.config.ArticleStatProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文章浏览 / 点赞计数定时批量落库。
 *
 * <p>落库具备幂等性：</p>
 * <ul>
 *   <li>浏览：Lua 原子取出增量并移出脏集合，多实例不会重复累加；失败时把增量放回 Redis。</li>
 *   <li>点赞：{@code INSERT IGNORE} + 唯一约束保证重复落库不会多计数，点赞数按实际插入行数增加。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.article-stat.enabled", havingValue = "true", matchIfMissing = true)
public class ArticleStatFlushScheduler {

    private static final String UPDATE_VIEW_SQL =
            "UPDATE articles SET view_count = COALESCE(view_count, 0) + :delta WHERE id = :id";

    private static final String INSERT_LIKE_SQL =
            "INSERT IGNORE INTO article_like (article_id, user_id, created_at) " +
                    "VALUES (:articleId, :userId, :createdAt)";

    private static final String UPDATE_LIKE_SQL =
            "UPDATE articles SET like_count = COALESCE(like_count, 0) + :delta WHERE id = :id";

    private static final String SELECT_EXISTING_ARTICLE_SQL =
            "SELECT id FROM articles WHERE id IN (:ids)";

    private final ArticleStatService articleStatService;
    private final ArticleStatProperties properties;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(
            fixedDelayString = "${app.article-stat.flush-interval-ms:30000}",
            initialDelayString = "${app.article-stat.flush-interval-ms:30000}")
    public void flush() {
        try {
            flushViewDeltas();
        } catch (Exception e) {
            log.warn("浏览量批量落库失败，增量已回填 Redis，等待下次重试：{}", e.getMessage());
        }
        try {
            flushPendingLikes();
        } catch (Exception e) {
            log.warn("点赞批量落库失败，事件保留在 Redis，等待下次重试：{}", e.getMessage());
        }
    }

    private void flushViewDeltas() {
        List<Long> articleIds = articleStatService.dirtyArticleIds(properties.getFlushBatchSize());
        if (articleIds.isEmpty()) {
            return;
        }

        Map<Long, Long> deltas = new LinkedHashMap<>();
        for (Long articleId : articleIds) {
            long delta = articleStatService.takeViewDelta(articleId);
            if (delta > 0) {
                deltas.put(articleId, delta);
            }
        }
        if (deltas.isEmpty()) {
            return;
        }

        try {
            transactionTemplate.executeWithoutResult(status -> {
                MapSqlParameterSource[] batch = deltas.entrySet().stream()
                        .map(e -> new MapSqlParameterSource()
                                .addValue("delta", e.getValue())
                                .addValue("id", e.getKey()))
                        .toArray(MapSqlParameterSource[]::new);
                jdbcTemplate.batchUpdate(UPDATE_VIEW_SQL, batch);
            });
        } catch (Exception e) {
            deltas.forEach(articleStatService::restoreViewDelta);
            throw e;
        }
    }

    private void flushPendingLikes() {
        Set<String> members = articleStatService.pendingLikeMembers(properties.getFlushBatchSize());
        if (members.isEmpty()) {
            return;
        }

        Map<Long, List<Long>> userIdsByArticle = new LinkedHashMap<>();
        for (String member : members) {
            int separator = member.indexOf(':');
            if (separator <= 0 || separator >= member.length() - 1) {
                continue;
            }
            try {
                Long articleId = Long.valueOf(member.substring(0, separator));
                Long userId = Long.valueOf(member.substring(separator + 1));
                userIdsByArticle.computeIfAbsent(articleId, k -> new ArrayList<>()).add(userId);
            } catch (NumberFormatException ignored) {
                // 非法 member 会在本次成功落库后统一从 ZSET 移除
            }
        }
        if (userIdsByArticle.isEmpty()) {
            articleStatService.removePendingLikeMembers(members);
            return;
        }

        Set<Long> existingArticleIds = loadExistingArticleIds(userIdsByArticle.keySet());
        List<MapSqlParameterSource> insertParams = new ArrayList<>();
        List<Long> insertArticleIds = new ArrayList<>();
        for (Map.Entry<Long, List<Long>> entry : userIdsByArticle.entrySet()) {
            if (!existingArticleIds.contains(entry.getKey())) {
                continue;
            }
            LocalDateTime now = LocalDateTime.now();
            for (Long userId : entry.getValue()) {
                insertParams.add(new MapSqlParameterSource()
                        .addValue("articleId", entry.getKey())
                        .addValue("userId", userId)
                        .addValue("createdAt", now));
                insertArticleIds.add(entry.getKey());
            }
        }

        Map<Long, Long> insertedByArticle = new LinkedHashMap<>();
        transactionTemplate.executeWithoutResult(status -> {
            if (!insertParams.isEmpty()) {
                int[] affected = jdbcTemplate.batchUpdate(
                        INSERT_LIKE_SQL,
                        insertParams.toArray(MapSqlParameterSource[]::new));
                for (int i = 0; i < affected.length; i++) {
                    if (affected[i] > 0) {
                        insertedByArticle.merge(insertArticleIds.get(i), 1L, Long::sum);
                    }
                }
            }
            if (!insertedByArticle.isEmpty()) {
                MapSqlParameterSource[] batch = insertedByArticle.entrySet().stream()
                        .map(e -> new MapSqlParameterSource()
                                .addValue("delta", e.getValue())
                                .addValue("id", e.getKey()))
                        .toArray(MapSqlParameterSource[]::new);
                jdbcTemplate.batchUpdate(UPDATE_LIKE_SQL, batch);
            }
        });

        // 仅在数据库事务提交成功后移除事件；若提交后进程崩溃，重试时 INSERT IGNORE 返回 0，不会重复计数。
        articleStatService.removePendingLikeMembers(members);
    }

    private Set<Long> loadExistingArticleIds(Set<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return Set.of();
        }
        List<Long> existing = jdbcTemplate.queryForList(
                SELECT_EXISTING_ARTICLE_SQL,
                new MapSqlParameterSource("ids", articleIds),
                Long.class);
        return new HashSet<>(existing);
    }
}
