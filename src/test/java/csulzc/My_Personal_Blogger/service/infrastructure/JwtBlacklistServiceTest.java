package csulzc.My_Personal_Blogger.service.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * JwtBlacklistService 单元测试（纯 Mockito，不依赖真实 Redis）。
 * 覆盖：黑名单写入/查询、TTL 语义、封禁时间戳比对、fail-open 降级。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtBlacklistService JWT 主动失效测试")
class JwtBlacklistServiceTest {

    @Mock
    private ObjectProvider<StringRedisTemplate> redisProvider;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private JwtBlacklistService service;

    @BeforeEach
    void setUp() {
        service = new JwtBlacklistService(redisProvider);
        // @Value 字段在纯 Mockito 环境下不注入，显式开启模块
        ReflectionTestUtils.setField(service, "enabled", true);
    }

    // ==================== 登出黑名单 ====================

    @Test
    @DisplayName("拉黑 token：以剩余有效期为 TTL 写入黑名单")
    void blacklistToken_writesWithRemainingTtl() {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        when(redis.opsForValue()).thenReturn(valueOps);

        service.blacklistToken("jti-1", 3600_000L);

        verify(valueOps).set("blog:jwt:blacklist:jti-1", "1", 3600_000L, TimeUnit.MILLISECONDS);
    }

    @Test
    @DisplayName("拉黑 token：剩余有效期 <=0 时不写入")
    void blacklistToken_skipsWhenExpired() {
        service.blacklistToken("jti-1", 0L);

        verifyNoInteractions(redisProvider);
    }

    @Test
    @DisplayName("拉黑 token：模块关闭时不写入")
    void blacklistToken_skipsWhenDisabled() {
        ReflectionTestUtils.setField(service, "enabled", false);

        service.blacklistToken("jti-1", 3600_000L);

        verifyNoInteractions(redisProvider);
    }

    @Test
    @DisplayName("黑名单查询：命中返回 true，未命中返回 false")
    void isBlacklisted_hitAndMiss() {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        when(redis.hasKey("blog:jwt:blacklist:jti-hit")).thenReturn(true);
        when(redis.hasKey("blog:jwt:blacklist:jti-miss")).thenReturn(false);

        assertThat(service.isBlacklisted("jti-hit")).isTrue();
        assertThat(service.isBlacklisted("jti-miss")).isFalse();
    }

    @Test
    @DisplayName("黑名单查询：Redis 异常时 fail-open 返回 false")
    void isBlacklisted_failOpenOnRedisError() {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        when(redis.hasKey(anyString())).thenThrow(new RuntimeException("redis down"));

        assertThat(service.isBlacklisted("jti-1")).isFalse();
    }

    @Test
    @DisplayName("黑名单查询：Redis 不可用时返回 false")
    void isBlacklisted_falseWhenRedisAbsent() {
        when(redisProvider.getIfAvailable()).thenReturn(null);

        assertThat(service.isBlacklisted("jti-1")).isFalse();
    }

    // ==================== 用户封禁时间戳 ====================

    @Test
    @DisplayName("封禁用户：写入封禁时间戳，TTL 取 refresh 有效期")
    void banUser_writesTimestampWithTtl() {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        when(redis.opsForValue()).thenReturn(valueOps);

        service.banUser(7L, 1000L, 604800_000L);

        verify(valueOps).set("blog:jwt:ban:7", "1000", 604800_000L, TimeUnit.MILLISECONDS);
    }

    @Test
    @DisplayName("封禁比对：iat 早于封禁时间的 token 判定为失效")
    void isIssuedBeforeBan_trueWhenIssuedEarlier() {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("blog:jwt:ban:7")).thenReturn("2000");

        assertThat(service.isIssuedBeforeBan(7L, new Date(1999L))).isTrue();
        assertThat(service.isIssuedBeforeBan(7L, new Date(2000L))).isFalse();
        assertThat(service.isIssuedBeforeBan(7L, new Date(2001L))).isFalse();
    }

    @Test
    @DisplayName("封禁比对：无封禁标记时放行")
    void isIssuedBeforeBan_falseWhenNoBan() {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("blog:jwt:ban:7")).thenReturn(null);

        assertThat(service.isIssuedBeforeBan(7L, new Date(1000L))).isFalse();
    }

    @Test
    @DisplayName("封禁比对：Redis 异常时 fail-open 返回 false")
    void isIssuedBeforeBan_failOpenOnRedisError() {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("redis down"));

        assertThat(service.isIssuedBeforeBan(7L, new Date(1000L))).isFalse();
    }

    @Test
    @DisplayName("解除封禁：删除封禁标记")
    void unbanUser_deletesKey() {
        when(redisProvider.getIfAvailable()).thenReturn(redis);

        service.unbanUser(7L);

        verify(redis).delete("blog:jwt:ban:7");
    }
}
