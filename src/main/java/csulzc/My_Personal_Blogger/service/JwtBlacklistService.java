package csulzc.My_Personal_Blogger.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * JWT 主动失效服务。
 *
 * <ul>
 *   <li>单 token 黑名单：{@code blog:jwt:blacklist:{jti}}，登出时写入，TTL = token 剩余有效期
 *       （最长不超过 refresh token 有效期，到期 token 本身已过期，Redis 自动清除）；</li>
 *   <li>用户封禁标记：{@code blog:jwt:ban:{userId}} = 封禁时间戳，封禁/锁定/删除时写入，
 *       签发时间（iat）早于该时间戳的 token 一律视为失效。</li>
 * </ul>
 *
 * <p>遵循故障降级约定：模块关闭或 Redis 不可用时 fail-open，系统行为最多退回到
 * 未防护状态（仅签名 + exp 校验），不会引入更严重的新故障。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtBlacklistService {

    private static final String BLACKLIST_PREFIX = "blog:jwt:blacklist:";
    private static final String BAN_PREFIX = "blog:jwt:ban:";

    private final ObjectProvider<StringRedisTemplate> redisProvider;

    /** 模块开关：测试等无 Redis 环境可整体关闭 */
    @Value("${app.jwt-blacklist.enabled:true}")
    private boolean enabled;

    /**
     * 把单个 token 拉入黑名单。
     *
     * @param jti       token 唯一标识
     * @param ttlMillis token 剩余有效期（毫秒），<=0 时无需写入（token 已过期）
     */
    public void blacklistToken(String jti, long ttlMillis) {
        if (!enabled || jti == null || ttlMillis <= 0) {
            return;
        }
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            log.warn("Redis 不可用，JWT 黑名单写入跳过（fail-open）: jti={}", jti);
            return;
        }
        try {
            redis.opsForValue().set(BLACKLIST_PREFIX + jti, "1", ttlMillis, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("JWT 黑名单写入失败（fail-open）: jti={}, error={}", jti, e.getMessage());
        }
    }

    /**
     * 判断 token 是否在黑名单中。Redis 异常时返回 false（fail-open）。
     */
    public boolean isBlacklisted(String jti) {
        if (!enabled || jti == null) {
            return false;
        }
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey(BLACKLIST_PREFIX + jti));
        } catch (Exception e) {
            log.warn("JWT 黑名单查询失败（fail-open）: jti={}, error={}", jti, e.getMessage());
            return false;
        }
    }

    /**
     * 封禁用户：记录封禁时间戳，iat 早于此时间的 token 全部失效。
     *
     * @param ttlMillis 标记保留时长，应取 refresh token 有效期（其到期后该用户旧 token 已全部自然过期）
     */
    public void banUser(Long userId, long banTimeMillis, long ttlMillis) {
        if (!enabled || userId == null || ttlMillis <= 0) {
            return;
        }
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            log.warn("Redis 不可用，用户封禁标记写入跳过（fail-open）: userId={}", userId);
            return;
        }
        try {
            redis.opsForValue().set(BAN_PREFIX + userId, String.valueOf(banTimeMillis), ttlMillis, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("用户封禁标记写入失败（fail-open）: userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 判断 token 是否签发在用户封禁之前（是则应失效）。Redis 异常时返回 false（fail-open）。
     */
    public boolean isIssuedBeforeBan(Long userId, Date issuedAt) {
        if (!enabled || userId == null || issuedAt == null) {
            return false;
        }
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return false;
        }
        try {
            String banTime = redis.opsForValue().get(BAN_PREFIX + userId);
            return banTime != null && issuedAt.getTime() < Long.parseLong(banTime);
        } catch (Exception e) {
            log.warn("用户封禁标记查询失败（fail-open）: userId={}, error={}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * 解除封禁标记（激活/解锁时调用）。
     */
    public void unbanUser(Long userId) {
        if (!enabled || userId == null) {
            return;
        }
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return;
        }
        try {
            redis.delete(BAN_PREFIX + userId);
        } catch (Exception e) {
            log.warn("用户封禁标记删除失败: userId={}, error={}", userId, e.getMessage());
        }
    }
}
