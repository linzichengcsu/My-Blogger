package csulzc.My_Personal_Blogger.service;

import csulzc.My_Personal_Blogger.exception.TooManyRequestsException;
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

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 忘记密码验证码测试（纯 Mockito）：签发冷却、小时限额、一次性校验、错误上限作废、Redis 缺失 fail-closed。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordResetCodeService 验证码测试")
class PasswordResetCodeServiceTest {

    private static final String CODE_KEY = "blog:pwd:code:user1";
    private static final String TRIES_KEY = "blog:pwd:tries:user1";
    private static final String COOLDOWN_KEY = "blog:pwd:cooldown:user1";
    private static final String HOURLY_KEY = "blog:pwd:hourly:user1";

    @Mock
    private ObjectProvider<StringRedisTemplate> redisProvider;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private PasswordResetCodeService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetCodeService(redisProvider);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "codeTtlMinutes", 10);
        ReflectionTestUtils.setField(service, "maxVerifyAttempts", 5);
        ReflectionTestUtils.setField(service, "sendCooldownSeconds", 60);
        ReflectionTestUtils.setField(service, "hourlySendLimit", 5);
        lenient().when(redisProvider.getIfAvailable()).thenReturn(redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.setIfAbsent(eq(COOLDOWN_KEY), anyString(), any(Duration.class))).thenReturn(true);
        lenient().when(valueOperations.increment(HOURLY_KEY)).thenReturn(1L);
    }

    @Test
    @DisplayName("Redis 不可用 -> fail-closed：签发与校验均抛异常，绝不退回无验证码状态")
    void redisUnavailable_failsClosed() {
        when(redisProvider.getIfAvailable()).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> service.issueCode("user1"));
        assertThrows(IllegalStateException.class, () -> service.verifyAndConsume("user1", "123456"));
    }

    @Test
    @DisplayName("签发成功：6 位验证码写入，TTL 10 分钟，清除历史错误计数")
    void issueCode_success() {
        String code = service.issueCode("user1");

        assertEquals(6, code.length());
        assertTrue(code.matches("\\d{6}"));
        verify(valueOperations).set(eq(CODE_KEY), eq(code), eq(Duration.ofMinutes(10)));
        verify(redisTemplate).delete(TRIES_KEY);
    }

    @Test
    @DisplayName("发送冷却期内 -> 抛 429")
    void issueCode_cooldownActive() {
        when(valueOperations.setIfAbsent(eq(COOLDOWN_KEY), anyString(), any(Duration.class))).thenReturn(false);

        assertThrows(TooManyRequestsException.class, () -> service.issueCode("user1"));
    }

    @Test
    @DisplayName("每小时发送超限 -> 抛 429 并回滚本次冷却")
    void issueCode_hourlyLimitExceeded() {
        when(valueOperations.increment(HOURLY_KEY)).thenReturn(6L);

        assertThrows(TooManyRequestsException.class, () -> service.issueCode("user1"));
        verify(redisTemplate).delete(COOLDOWN_KEY);
    }

    @Test
    @DisplayName("校验通过 -> 立即消费（一次性），返回 true")
    void verifyAndConsume_correctCode_consumedOnce() {
        when(valueOperations.get(CODE_KEY)).thenReturn("123456");

        assertTrue(service.verifyAndConsume("user1", "123456"));
        verify(redisTemplate).delete(CODE_KEY);
        verify(redisTemplate).delete(TRIES_KEY);
    }

    @Test
    @DisplayName("验证码已过期/不存在 -> false")
    void verifyAndConsume_expiredCode() {
        when(valueOperations.get(CODE_KEY)).thenReturn(null);

        assertFalse(service.verifyAndConsume("user1", "123456"));
    }

    @Test
    @DisplayName("验证码错误 -> false 且累计错误次数")
    void verifyAndConsume_wrongCode_countsAttempt() {
        when(valueOperations.get(CODE_KEY)).thenReturn("123456");
        when(valueOperations.increment(TRIES_KEY)).thenReturn(1L);

        assertFalse(service.verifyAndConsume("user1", "654321"));
        verify(redisTemplate).expire(eq(TRIES_KEY), any(Duration.class));
        verify(redisTemplate, never()).delete(CODE_KEY);
    }

    @Test
    @DisplayName("连续错误达上限 -> 验证码作废，6 位码无法被无限枚举")
    void verifyAndConsume_maxAttempts_invalidatesCode() {
        when(valueOperations.get(CODE_KEY)).thenReturn("123456");
        when(valueOperations.increment(TRIES_KEY)).thenReturn(5L);

        assertFalse(service.verifyAndConsume("user1", "654321"));
        verify(redisTemplate).delete(CODE_KEY);
        verify(redisTemplate).delete(TRIES_KEY);
    }
}
