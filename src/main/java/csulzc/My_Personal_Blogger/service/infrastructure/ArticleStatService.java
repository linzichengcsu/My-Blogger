package csulzc.My_Personal_Blogger.service.infrastructure;

import csulzc.My_Personal_Blogger.config.ArticleStatProperties;
import csulzc.My_Personal_Blogger.repository.ArticleLikeRepository;
import csulzc.My_Personal_Blogger.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 文章浏览 / 点赞计数服务。
 *
 * <p>请求路径只访问 Redis：</p>
 * <ul>
 *   <li>浏览：{@code INCR} 浏览量增量，并把文章 ID 放入待落库集合；</li>
 *   <li>点赞：{@code SADD} 去重集合 + {@code ZADD} 待落库事件，点赞数由落库时
 *       实际插入的行数决定，重复点赞不会多计数。</li>
 * </ul>
 *
 * <p>Redis 不可用或模块关闭时，自动降级为数据库同步更新，保证功能不回退到不可用状态。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleStatService {

    private static final String VIEW_KEY_PREFIX = "blog:article:view:";
    private static final String VIEW_DIRTY_KEY = "blog:article:view:dirty";
    private static final String LIKE_USERS_KEY_PREFIX = "blog:article:like:users:";
    static final String LIKE_PENDING_KEY = "blog:article:like:pending";

    /** Redis 故障后的快速降级窗口，避免每次请求都等待连接超时 */
    private static final long REDIS_BACKOFF_MS = 30_000L;

    /** 浏览 +1，并标记该文章浏览量待落库 */
    private static final DefaultRedisScript<Long> RECORD_VIEW_SCRIPT = new DefaultRedisScript<>(
            "redis.call('INCR', KEYS[1]); " +
                    "redis.call('SADD', KEYS[2], ARGV[1]); " +
                    "return 1",
            Long.class);

    /**
     * 点赞：先 SADD 去重，只有首次点赞才加入待落库 ZSET。
     * ARGV: userId, score(epoch millis), member(articleId:userId), ttlSeconds
     */
    private static final DefaultRedisScript<Long> RECORD_LIKE_SCRIPT = new DefaultRedisScript<>(
            "local added = redis.call('SADD', KEYS[1], ARGV[1]); " +
                    "if added == 1 then " +
                    "  redis.call('ZADD', KEYS[2], ARGV[2], ARGV[3]); " +
                    "  redis.call('EXPIRE', KEYS[1], ARGV[4]); " +
                    "end; " +
                    "return added",
            Long.class);

    /** 原子取出并清零浏览量增量，同时把文章移出待落库集合 */
    private static final DefaultRedisScript<Long> TAKE_VIEW_DELTA_SCRIPT = new DefaultRedisScript<>(
            "local v = tonumber(redis.call('GET', KEYS[1]) or '0'); " +
                    "redis.call('DEL', KEYS[1]); " +
                    "redis.call('SREM', KEYS[2], ARGV[1]); " +
                    "return v",
            Long.class);

    private final ArticleStatProperties properties;
    private final ArticleRepository articleRepository;
    private final ArticleLikeRepository articleLikeRepository;
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final TransactionTemplate transactionTemplate;

    /** Redis 快速降级截止时间（epoch millis），期间请求直接走数据库 */
    private volatile long redisBackoffUntil = 0L;

    // ==================== 写路径 ====================

    /**
     * 记录一次浏览。
     */
    public void recordView(Long articleId) {
        StringRedisTemplate redis = redis();
        if (redis == null) {
            recordViewInDb(articleId);
            return;
        }
        try {
            redis.execute(RECORD_VIEW_SCRIPT,
                    List.of(viewKey(articleId), VIEW_DIRTY_KEY),
                    String.valueOf(articleId));
            markRedisSuccess();
        } catch (Exception e) {
            markRedisFailure();
            log.warn("Redis 记录浏览失败，降级为数据库同步更新：articleId={}, error={}", articleId, e.getMessage());
            recordViewInDb(articleId);
        }
    }

    /**
     * 记录一次点赞。
     *
     * @return true 表示本次点赞被接受；false 表示重复点赞（或文章不存在）
     */
    public boolean recordLike(Long articleId, Long userId) {
        StringRedisTemplate redis = redis();
        if (redis == null) {
            return recordLikeInDb(articleId, userId);
        }
        try {
            Long added = redis.execute(RECORD_LIKE_SCRIPT,
                    List.of(likeUsersKey(articleId), LIKE_PENDING_KEY),
                    String.valueOf(userId),
                    String.valueOf(System.currentTimeMillis()),
                    articleId + ":" + userId,
                    String.valueOf(properties.getLikeCacheTtlSeconds()));
            markRedisSuccess();
            return added != null && added == 1L;
        } catch (Exception e) {
            markRedisFailure();
            log.warn("Redis 记录点赞失败，降级为数据库同步更新：articleId={}, userId={}, error={}",
                    articleId, userId, e.getMessage());
            return recordLikeInDb(articleId, userId);
        }
    }

    private void recordViewInDb(Long articleId) {
        transactionTemplate.executeWithoutResult(status -> articleRepository.incrementViewCount(articleId));
    }

    private boolean recordLikeInDb(Long articleId, Long userId) {
        Boolean liked = transactionTemplate.execute(status -> {
            if (!articleRepository.existsById(articleId)) {
                return false;
            }
            int inserted = articleLikeRepository.insertIgnore(articleId, userId, LocalDateTime.now());
            if (inserted > 0) {
                articleRepository.incrementLikeCount(articleId);
                return true;
            }
            return false;
        });
        return Boolean.TRUE.equals(liked);
    }

    // ==================== 供定时落库任务使用 ====================

    /**
     * 当前待落库的文章 ID 列表（最多 limit 个）。
     */
    public List<Long> dirtyArticleIds(int limit) {
        StringRedisTemplate redis = redis();
        if (redis == null) {
            return List.of();
        }
        Set<String> members = redis.opsForSet().members(VIEW_DIRTY_KEY);
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String member : members) {
            if (ids.size() >= limit) {
                break;
            }
            try {
                ids.add(Long.valueOf(member));
            } catch (NumberFormatException e) {
                redis.opsForSet().remove(VIEW_DIRTY_KEY, member);
            }
        }
        return ids;
    }

    /**
     * 原子取出并清零指定文章的浏览量增量（同时移出待落库集合）。
     */
    public long takeViewDelta(Long articleId) {
        StringRedisTemplate redis = redis();
        if (redis == null) {
            return 0L;
        }
        Long delta = redis.execute(TAKE_VIEW_DELTA_SCRIPT,
                List.of(viewKey(articleId), VIEW_DIRTY_KEY),
                String.valueOf(articleId));
        return delta == null ? 0L : delta;
    }

    /**
     * 落库失败时把已取出的浏览量增量放回 Redis。
     */
    public void restoreViewDelta(Long articleId, long delta) {
        if (delta <= 0) {
            return;
        }
        StringRedisTemplate redis = redis();
        if (redis == null) {
            return;
        }
        redis.opsForValue().increment(viewKey(articleId), delta);
        redis.opsForSet().add(VIEW_DIRTY_KEY, String.valueOf(articleId));
    }

    /**
     * 待落库的点赞事件（member 形如 articleId:userId），按时间升序。
     */
    public Set<String> pendingLikeMembers(int limit) {
        StringRedisTemplate redis = redis();
        if (redis == null) {
            return Set.of();
        }
        Set<String> members = redis.opsForZSet().range(LIKE_PENDING_KEY, 0, limit - 1L);
        return members == null ? Set.of() : members;
    }

    /**
     * 移除已成功落库的点赞事件。
     */
    public void removePendingLikeMembers(Collection<String> members) {
        if (members == null || members.isEmpty()) {
            return;
        }
        StringRedisTemplate redis = redis();
        if (redis == null) {
            return;
        }
        redis.opsForZSet().remove(LIKE_PENDING_KEY, members.toArray());
    }

    // ==================== 内部工具 ====================

    private StringRedisTemplate redis() {
        if (!properties.isEnabled() || System.currentTimeMillis() < redisBackoffUntil) {
            return null;
        }
        return redisProvider.getIfAvailable();
    }

    private void markRedisFailure() {
        redisBackoffUntil = System.currentTimeMillis() + REDIS_BACKOFF_MS;
    }

    private void markRedisSuccess() {
        redisBackoffUntil = 0L;
    }

    private String viewKey(Long articleId) {
        return VIEW_KEY_PREFIX + articleId;
    }

    private String likeUsersKey(Long articleId) {
        return LIKE_USERS_KEY_PREFIX + articleId;
    }
}
