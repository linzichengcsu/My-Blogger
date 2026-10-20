package csulzc.My_Personal_Blogger.aspect;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 缓存穿透防护切面（哨兵缓存）。
 *
 * <p>背景：Service 层查询不存在的资源时抛 {@link EntityNotFoundException}，异常不进入
 * 缓存写入流程，导致对不存在 ID 的每次请求都会穿透缓存直达数据库（恶意探测/爬虫可放大）。</p>
 *
 * <p>机制：</p>
 * <ul>
 *   <li>DB 确认资源不存在时，写入一个短 TTL 的"不存在"哨兵 key；</li>
 *   <li>TTL 内相同 ID 的请求在进 Service/缓存之前即被拦截，直接抛 404，不打 DB；</li>
 *   <li>哨兵过期后放行一次回源重新确认，保证数据真实存在后最多滞后一个哨兵 TTL 可见。</li>
 * </ul>
 *
 * <p>文章 ID 为数据库自增、不会复用，哨兵不会产生长期误判；Redis 故障时降级为无防护直通，
 * 不影响主流程。</p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.cache.enabled", havingValue = "true", matchIfMissing = true)
public class CachePenetrationGuardAspect {

    /** 哨兵 key 前缀：cache:notfound:{资源}:{id} */
    private static final String SENTINEL_PREFIX = "cache:notfound:";

    /**
     * 哨兵 TTL：需足够短，避免"探测时不存在、随后被创建"的 ID 被长期误判（文章自增 ID
     * 理论上不复用，此处仅作为兜底窗口）；同时不能太短，否则失去拦截意义。
     */
    private static final Duration SENTINEL_TTL = Duration.ofMinutes(5);

    private final RedisTemplate<String, Object> redisTemplate;

    @Around("execution(* csulzc.My_Personal_Blogger.service.ArticleService.getArticleById(..))")
    public Object guardArticleById(ProceedingJoinPoint joinPoint) throws Throwable {
        Object arg = joinPoint.getArgs().length > 0 ? joinPoint.getArgs()[0] : null;
        if (!(arg instanceof Long articleId)) {
            return joinPoint.proceed();
        }
        return guard(joinPoint, SENTINEL_PREFIX + "article:" + articleId, "文章不存在");
    }

    @Around("execution(* csulzc.My_Personal_Blogger.service.CategoryService.getCategoryById(..))")
    public Object guardCategoryById(ProceedingJoinPoint joinPoint) throws Throwable {
        Object arg = joinPoint.getArgs().length > 0 ? joinPoint.getArgs()[0] : null;
        if (!(arg instanceof Long categoryId)) {
            return joinPoint.proceed();
        }
        return guard(joinPoint, SENTINEL_PREFIX + "category:" + categoryId, "收藏夹不存在");
    }

    /**
     * 哨兵检查 → 放行回源 → 404 时写哨兵。
     */
    private Object guard(ProceedingJoinPoint joinPoint, String sentinelKey, String notFoundMessage) throws Throwable {
        // 1. 命中哨兵：该 ID 近期已被 DB 确认不存在，直接 404，不查缓存也不打 DB
        if (isSentinelPresent(sentinelKey)) {
            throw new EntityNotFoundException(notFoundMessage);
        }

        try {
            return joinPoint.proceed();
        } catch (EntityNotFoundException e) {
            // 2. DB 确认不存在：写入短 TTL 哨兵，TTL 内相同 ID 的请求被直接拦截
            markNotFound(sentinelKey);
            throw e;
        }
    }

    private boolean isSentinelPresent(String sentinelKey) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(sentinelKey));
        } catch (Exception e) {
            // Redis 故障时降级为无防护直通，不能让防护逻辑本身成为故障点
            log.warn("读取缓存穿透哨兵失败，本次放行：{}", e.getMessage());
            return false;
        }
    }

    private void markNotFound(String sentinelKey) {
        try {
            redisTemplate.opsForValue().set(sentinelKey, "1", SENTINEL_TTL);
        } catch (Exception e) {
            log.warn("写入缓存穿透哨兵失败（不影响主流程）：{}", e.getMessage());
        }
    }
}
