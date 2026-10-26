package csulzc.My_Personal_Blogger.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * 忘记密码邮箱验证码服务。
 *
 * <p>Redis 键设计：</p>
 * <ul>
 *   <li>{@code blog:pwd:code:{username}}：验证码，TTL 10 分钟，验证成功或连续错误后删除（一次性）；</li>
 *   <li>{@code blog:pwd:tries:{username}}：同一验证码的连续错误次数，达上限后作废验证码（防 6 位码暴力枚举）；</li>
 *   <li>{@code blog:pwd:cooldown:{username}}：发送冷却 60 秒（防邮件轰炸）；</li>
 *   <li>{@code blog:pwd:hourly:{username}}：每小时最多发送次数（防轰炸兜底）。</li>
 * </ul>
 *
 * <p>降级约定：邮箱验证码的防爆破依赖 Redis 存储，Redis 不可用时本模块 fail-closed
 * （验证码无法签发与校验，忘记密码功能暂不可用），绝不退回到「用户名 + 邮箱即可重置」
 * 的无验证码状态——那正是本模块要修复的账户接管漏洞。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetCodeService {

    private static final String CODE_PREFIX = "blog:pwd:code:";
    private static final String TRIES_PREFIX = "blog:pwd:tries:";
    private static final String COOLDOWN_PREFIX = "blog:pwd:cooldown:";
    private static final String HOURLY_PREFIX = "blog:pwd:hourly:";

    /** 验证码有效期（分钟） */
    @Value("${app.password-reset.code-ttl-minutes:10}")
    private int codeTtlMinutes;

    /** 同一验证码允许的最大错误次数，超过即作废 */
    @Value("${app.password-reset.max-verify-attempts:5}")
    private int maxVerifyAttempts;

    /** 同一账号两次发送的最小间隔（秒） */
    @Value("${app.password-reset.send-cooldown-seconds:60}")
    private int sendCooldownSeconds;

    /** 同一账号每小时最大发送次数 */
    @Value("${app.password-reset.hourly-send-limit:5}")
    private int hourlySendLimit;

    /** 模块开关：测试等无 Redis 环境可整体关闭 */
    @Value("${app.password-reset.enabled:true}")
    private boolean enabled;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ObjectProvider<StringRedisTemplate> redisProvider;

    /**
     * 签发验证码并返回。受发送冷却与每小时限额约束。
     *
     * @throws csulzc.My_Personal_Blogger.exception.TooManyRequestsException 发送过于频繁
     * @throws IllegalStateException Redis 不可用，验证码功能暂不可用
     */
    public String issueCode(String username) {
        requireRedis();
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        String key = normalize(username);

        Boolean first = redis.opsForValue().setIfAbsent(COOLDOWN_PREFIX + key, "1",
                Duration.ofSeconds(sendCooldownSeconds));
        if (!Boolean.TRUE.equals(first)) {
            throw new csulzc.My_Personal_Blogger.exception.TooManyRequestsException(
                    "验证码发送过于频繁，请 " + sendCooldownSeconds + " 秒后再试");
        }
        try {
            Long hourly = redis.opsForValue().increment(HOURLY_PREFIX + key);
            if (hourly != null && hourly == 1L) {
                redis.expire(HOURLY_PREFIX + key, Duration.ofHours(1));
            }
            if (hourly != null && hourly > hourlySendLimit) {
                throw new csulzc.My_Personal_Blogger.exception.TooManyRequestsException(
                        "获取验证码次数已达上限，请 1 小时后再试");
            }

            String code = String.format("%06d", RANDOM.nextInt(1_000_000));
            redis.opsForValue().set(CODE_PREFIX + key, code, Duration.ofMinutes(codeTtlMinutes));
            redis.delete(TRIES_PREFIX + key);
            log.info("签发忘记密码验证码: username={}, ttl={}min", username, codeTtlMinutes);
            return code;
        } catch (csulzc.My_Personal_Blogger.exception.TooManyRequestsException e) {
            // 限额触发时回滚本次冷却，避免用户被双重惩罚
            redis.delete(COOLDOWN_PREFIX + key);
            throw e;
        } catch (Exception e) {
            redis.delete(COOLDOWN_PREFIX + key);
            throw new IllegalStateException("验证码服务暂不可用，请稍后重试", e);
        }
    }

    /**
     * 校验并消费验证码。正确则立即删除（一次性）；错误累计达上限后作废验证码。
     *
     * @return true 校验通过；false 验证码错误、过期或已作废
     * @throws IllegalStateException Redis 不可用，验证码功能暂不可用
     */
    public boolean verifyAndConsume(String username, String code) {
        requireRedis();
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        String key = normalize(username);
        try {
            String stored = redis.opsForValue().get(CODE_PREFIX + key);
            if (stored == null) {
                return false;
            }
            if (!stored.equals(code)) {
                Long tries = redis.opsForValue().increment(TRIES_PREFIX + key);
                if (tries != null && tries == 1L) {
                    redis.expire(TRIES_PREFIX + key, Duration.ofMinutes(codeTtlMinutes));
                }
                if (tries != null && tries >= maxVerifyAttempts) {
                    // 连续错误达上限：作废验证码，攻击者无法对 6 位码无限枚举
                    redis.delete(CODE_PREFIX + key);
                    redis.delete(TRIES_PREFIX + key);
                    log.warn("验证码连续错误达上限已作废: username={}", username);
                }
                return false;
            }
            redis.delete(CODE_PREFIX + key);
            redis.delete(TRIES_PREFIX + key);
            return true;
        } catch (Exception e) {
            log.error("验证码校验失败: username={}, error={}", username, e.getMessage());
            throw new IllegalStateException("验证码服务暂不可用，请稍后重试", e);
        }
    }

    private void requireRedis() {
        if (!enabled) {
            throw new IllegalStateException("验证码功能未启用");
        }
        if (redisProvider.getIfAvailable() == null) {
            throw new IllegalStateException("验证码服务暂不可用，请稍后重试");
        }
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
