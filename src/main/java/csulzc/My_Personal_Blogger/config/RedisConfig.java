package csulzc.My_Personal_Blogger.config;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
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

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("category:list",
                        defaultConfig.entryTtl(new JitteredTtlFunction(Duration.ofHours(1), TTL_JITTER)))
                .withCacheConfiguration("category:tree",
                        defaultConfig.entryTtl(new JitteredTtlFunction(Duration.ofHours(1), TTL_JITTER)))
                .withCacheConfiguration("category:stats",
                        defaultConfig.entryTtl(new JitteredTtlFunction(Duration.ofMinutes(10), TTL_JITTER)))
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
