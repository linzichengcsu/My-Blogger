package csulzc.My_Personal_Blogger.service;

import csulzc.My_Personal_Blogger.exception.TooManyRequestsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 登录失败限流服务：防在线暴力破解与撞库。
 *
 * <p>双维度固定窗口计数（Redis INCR + EXPIRE）：</p>
 * <ul>
 *   <li>账号维度：{@code blog:login:fail:user:{loginId}}，同一登录标识失败达阈值后锁定；</li>
 *   <li>IP 维度：{@code blog:login:fail:ip:{ip}}，防止单 IP 批量换号撞库，也防止
 *       攻击者恶意锁定他人账号（纯账号锁的 DoS 风险由 IP 维度兜底）。</li>
 * </ul>
 *
 * <p>每次失败都会刷新 TTL：即「自最后一次失败起」的滑动锁定窗口。
 * 登录成功只清除账号维度计数（IP 维度按 TTL 自然衰减，避免共享出口 IP 被单人清零）。</p>
 *
 * <p>遵循故障降级约定：模块关闭或 Redis 不可用时 fail-open，系统行为最多退回到
 * 未防护状态，不会引入比原问题更严重的新故障。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final String USER_FAIL_PREFIX = "blog:login:fail:user:";
    private static final String IP_FAIL_PREFIX = "blog:login:fail:ip:";

    /** 窗口内最大失败次数（账号 / IP 各自独立计数） */
    @Value("${app.login-protection.max-attempts:5}")
    private int maxAttempts;

    /** 锁定时长（分钟），每次失败后重新计时 */
    @Value("${app.login-protection.lock-minutes:15}")
    private int lockMinutes;

    /** 模块开关：压测、测试等场景可整体关闭 */
    @Value("${app.login-protection.enabled:true}")
    private boolean enabled;

    private final ObjectProvider<StringRedisTemplate> redisProvider;

    /**
     * 判断账号或 IP 是否已被锁定。锁定时抛出 429 异常。
     *
     * @param loginId 登录标识（用户名或邮箱）
     * @param clientIp 客户端 IP，可为 null（无法解析时跳过 IP 维度）
     */
    public void checkLocked(String loginId, String clientIp) {
        if (!enabled) {
            return;
        }
        StringRedisTemplate redis = redis();
        if (redis == null) {
            return;
        }
        try {
            String userCount = redis.opsForValue().get(USER_FAIL_PREFIX + normalize(loginId));
            if (userCount != null && Long.parseLong(userCount) >= maxAttempts) {
                throw lockedException("该账号");
            }
            if (clientIp != null) {
                String ipCount = redis.opsForValue().get(IP_FAIL_PREFIX + clientIp);
                if (ipCount != null && Long.parseLong(ipCount) >= maxAttempts) {
                    throw lockedException("当前 IP");
                }
            }
        } catch (TooManyRequestsException e) {
            throw e;
        } catch (Exception e) {
            log.warn("登录限流检查失败（fail-open）: loginId={}, error={}", loginId, e.getMessage());
        }
    }

    /**
     * 记录一次登录失败（用户不存在、密码错误均计入）。
     */
    public void recordFailure(String loginId, String clientIp) {
        if (!enabled) {
            return;
        }
        StringRedisTemplate redis = redis();
        if (redis == null) {
            return;
        }
        try {
            Duration ttl = Duration.ofMinutes(lockMinutes);
            String userKey = USER_FAIL_PREFIX + normalize(loginId);
            Long userCount = redis.opsForValue().increment(userKey);
            if (userCount != null && userCount == 1L) {
                redis.expire(userKey, ttl);
            }
            if (clientIp != null) {
                String ipKey = IP_FAIL_PREFIX + clientIp;
                Long ipCount = redis.opsForValue().increment(ipKey);
                if (ipCount != null && ipCount == 1L) {
                    redis.expire(ipKey, ttl);
                }
            }
        } catch (Exception e) {
            log.warn("登录失败计数写入失败（fail-open）: loginId={}, error={}", loginId, e.getMessage());
        }
    }

    /**
     * 登录成功：清除账号维度失败计数。IP 维度不清（防共享出口 IP 被单人清零）。
     */
    public void recordSuccess(String loginId) {
        if (!enabled) {
            return;
        }
        StringRedisTemplate redis = redis();
        if (redis == null) {
            return;
        }
        try {
            redis.delete(USER_FAIL_PREFIX + normalize(loginId));
        } catch (Exception e) {
            log.warn("登录成功清除计数失败（fail-open）: loginId={}, error={}", loginId, e.getMessage());
        }
    }

    private TooManyRequestsException lockedException(String subject) {
        return new TooManyRequestsException(subject + "失败次数过多，请 " + lockMinutes + " 分钟后重试");
    }

    private StringRedisTemplate redis() {
        return redisProvider.getIfAvailable();
    }

    /** 登录标识统一小写，避免大小写变体绕过账号维度计数 */
    private String normalize(String loginId) {
        return loginId == null ? "" : loginId.trim().toLowerCase();
    }
}
