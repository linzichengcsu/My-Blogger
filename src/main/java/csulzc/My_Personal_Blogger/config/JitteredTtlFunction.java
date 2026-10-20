package csulzc.My_Personal_Blogger.config;

import org.springframework.data.redis.cache.RedisCacheWriter;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 带随机抖动的 TTL 函数，用于防止缓存雪崩。
 *
 * <p>在基准 TTL 基础上按配置比例（默认 ±15%）做随机偏移，使同一批写入的 key
 * 过期时间均匀散布，避免在某个时间点集中穿透到数据库。</p>
 *
 * <p>用法：</p>
 * <pre>
 * RedisCacheConfiguration cfg = RedisCacheConfiguration.defaultCacheConfig()
 *         .entryTtl(new JitteredTtlFunction(Duration.ofMinutes(30), 0.15));
 * </pre>
 *
 * @see RedisCacheWriter.TtlFunction
 */
public final class JitteredTtlFunction implements RedisCacheWriter.TtlFunction {

    /** 默认抖动比例：±15% */
    private static final double DEFAULT_JITTER = 0.15;

    /** 抖动下限保护：30 秒，避免长 TTL 抖成几乎无缓存 */
    private static final Duration MIN_TTL = Duration.ofSeconds(30);

    private final Duration baseTtl;
    private final double jitterRatio;

    /**
     * 构造带默认 ±15% 抖动的 TTL 函数。
     */
    public JitteredTtlFunction(Duration baseTtl) {
        this(baseTtl, DEFAULT_JITTER);
    }

    /**
     * 构造带自定义抖动比例的 TTL 函数。
     *
     * @param baseTtl     基准 TTL（不得为 null，不得为负数）
     * @param jitterRatio 抖动比例，取值范围 (0, 0.5]，表示 ±ratio 的上下浮动
     */
    public JitteredTtlFunction(Duration baseTtl, double jitterRatio) {
        if (baseTtl == null || baseTtl.isNegative()) {
            throw new IllegalArgumentException("baseTtl 必须为非负的 Duration");
        }
        if (jitterRatio <= 0 || jitterRatio > 0.5) {
            throw new IllegalArgumentException("jitterRatio 必须在 (0, 0.5] 范围内，实际值：" + jitterRatio);
        }
        this.baseTtl = baseTtl;
        this.jitterRatio = jitterRatio;
    }

    /**
     * 每次写入缓存时被 Spring Data Redis 调用，返回该 key 的实际 TTL。
     *
     * @param key   即将写入的缓存 key
     * @param value 即将写入的缓存 value
     * @return 经过随机抖动后的 TTL，不低于 {@link #MIN_TTL}
     */
    @Override
    public Duration getTimeToLive(Object key, Object value) {
        long baseMillis = baseTtl.toMillis();
        long jitter = (long) (baseMillis * jitterRatio * (ThreadLocalRandom.current().nextDouble() * 2 - 1));
        long jittered = Math.max(baseMillis + jitter, MIN_TTL.toMillis());
        return Duration.ofMillis(jittered);
    }

    /** 返回基准 TTL（不含抖动），便于日志/监控查看。 */
    public Duration baseTtl() {
        return baseTtl;
    }

    /** 返回当前抖动比例。 */
    public double jitterRatio() {
        return jitterRatio;
    }

    @Override
    public String toString() {
        return "JitteredTtlFunction{base=" + baseTtl + ", jitter=±" + (jitterRatio * 100) + "%}";
    }
}
