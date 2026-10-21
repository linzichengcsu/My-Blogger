package csulzc.My_Personal_Blogger.config;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.CacheStatisticsCollector;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
@ConditionalOnProperty(name = "app.cache.enabled", havingValue = "true", matchIfMissing = true)
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(RedisSerializer.string());
        template.setHashKeySerializer(RedisSerializer.string());
        template.setValueSerializer(jsonSerializer());
        template.setHashValueSerializer(jsonSerializer());
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 缓存 TTL 抖动比例：±15%。
     * 同一批写入的 key 过期时间会均匀散布在 [TTL×0.85, TTL×1.15] 区间，防止集中穿透引发雪崩。
     */
    private static final double TTL_JITTER = 0.15;

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(new JitteredTtlFunction(Duration.ofMinutes(30), TTL_JITTER))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.string()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer()))
                .disableCachingNullValues();

        // 防击穿：带锁 CacheWriter 是 @Cacheable(sync=true) 生效的前提（sync 的互斥由 writer 实现）。
        // 回源时对 <cacheName>~lock 执行 SET NX 取锁 + 锁内双检，未抢到锁的线程按 50ms 轮询等待；
        // 锁 TTL 10s：防止持锁线程崩溃后锁永不释放导致后续回源全部阻塞（默认持久锁存在该死锁风险）。
        //
        // withStatisticsCollector(CacheStatisticsCollector.create())：
        // Spring Data Redis 4.x 的 RedisCacheWriter 默认使用 CacheStatisticsCollector.none()（空实现），
        // 导致 Micrometer CacheMeterBinder 通过 RedisCache.getStatistics() 拿到的永远是零值——
        // Actuator /actuator/metrics/cache.gets|puts|removals 全部为 0，击穿测试的 miss/hit 增量判据因此失效。
        // 必须显式挂上真实的统计收集器才能让 Boot 4 自动配置的 RedisCacheMeterBinderProvider 正确记录指标。
        RedisCacheWriter cacheWriter = RedisCacheWriter.lockingRedisCacheWriter(factory,
                Duration.ofMillis(50),
                RedisCacheWriter.TtlFunction.just(Duration.ofSeconds(10)),
                BatchStrategies.keys())
                .withStatisticsCollector(CacheStatisticsCollector.create());

        return RedisCacheManager.builder(cacheWriter)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("article:detail", defaultConfig)             // 30min 抖动 (默认)
                .withCacheConfiguration("article:list",   defaultConfig)              // 30min 抖动 (默认)
                .withCacheConfiguration("article:author", defaultConfig)              // 30min 抖动 (按作者列表)
                .withCacheConfiguration("category:detail", defaultConfig)             // 30min 抖动 (默认)
                .withCacheConfiguration("category:list",
                        defaultConfig.entryTtl(new JitteredTtlFunction(Duration.ofHours(1), TTL_JITTER)))
                .withCacheConfiguration("category:tree",
                        defaultConfig.entryTtl(new JitteredTtlFunction(Duration.ofHours(1), TTL_JITTER)))
                .withCacheConfiguration("category:stats",
                        defaultConfig.entryTtl(new JitteredTtlFunction(Duration.ofMinutes(10), TTL_JITTER)))
                // withCacheConfiguration 登记的全部 cache name 会在 RedisCacheManager 的
                // afterPropertiesSet() -> loadCaches() 阶段被 eager 创建（Spring Cache 默认行为），
                // Micrometer CacheMeterBinder 因此能在启动时注册所有 cache name 的指标，
                // Actuator /actuator/metrics/cache.gets 启动后即可看到全部 tags。
                .build();
    }

    private GenericJacksonJsonRedisSerializer jsonSerializer() {
        // Jackson 3 迁移：JSR-310 时间类型支持已内置于 jackson-databind 核心（通过
        // JavaTimeInitializer 自动注册），无需显式注册 JavaTimeModule。
        // GenericJacksonJsonRedisSerializer 默认不启用 default typing，需通过 builder 显式启用，
        // 否则反序列化时无法恢复原始 DTO 类型，会退化为 LinkedHashMap 并抛 ClassCastException。
        return GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(
                        BasicPolymorphicTypeValidator.builder()
                                .allowIfBaseType(Object.class)
                                .build())
                .build();
    }
}
