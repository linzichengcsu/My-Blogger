package csulzc.My_Personal_Blogger.service.infrastructure;

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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录失败限流测试（纯 Mockito）：锁定触发、双维度计数、成功清零、Redis 故障 fail-open。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LoginAttemptService 登录限流测试")
class LoginAttemptServiceTest {

    @Mock
    private ObjectProvider<StringRedisTemplate> redisProvider;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void setUp() {
        loginAttemptService = new LoginAttemptService(redisProvider);
        ReflectionTestUtils.setField(loginAttemptService, "enabled", true);
        ReflectionTestUtils.setField(loginAttemptService, "maxAttempts", 5);
        ReflectionTestUtils.setField(loginAttemptService, "lockMinutes", 15);
        lenient().when(redisProvider.getIfAvailable()).thenReturn(redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Redis 不可用时 fail-open：不锁定、不抛异常")
    void checkLocked_redisUnavailable_failOpen() {
        when(redisProvider.getIfAvailable()).thenReturn(null);

        assertDoesNotThrow(() -> loginAttemptService.checkLocked("user1", "1.2.3.4"));
        assertDoesNotThrow(() -> loginAttemptService.recordFailure("user1", "1.2.3.4"));
        assertDoesNotThrow(() -> loginAttemptService.recordSuccess("user1"));
    }

    @Test
    @DisplayName("账号维度失败次数达阈值 -> 锁定并抛 429")
    void checkLocked_userAttemptsExceeded() {
        when(valueOperations.get("blog:login:fail:user:user1")).thenReturn("5");

        assertThrows(TooManyRequestsException.class,
                () -> loginAttemptService.checkLocked("user1", "1.2.3.4"));
    }

    @Test
    @DisplayName("IP 维度失败次数达阈值 -> 锁定并抛 429（防换号撞库）")
    void checkLocked_ipAttemptsExceeded() {
        when(valueOperations.get("blog:login:fail:user:user1")).thenReturn(null);
        when(valueOperations.get("blog:login:fail:ip:1.2.3.4")).thenReturn("5");

        assertThrows(TooManyRequestsException.class,
                () -> loginAttemptService.checkLocked("user1", "1.2.3.4"));
    }

    @Test
    @DisplayName("未达阈值 -> 放行")
    void checkLocked_belowThreshold_passes() {
        when(valueOperations.get(anyString())).thenReturn("4");

        assertDoesNotThrow(() -> loginAttemptService.checkLocked("user1", "1.2.3.4"));
    }

    @Test
    @DisplayName("记录失败：账号与 IP 双维度各自计数，首本次设置 TTL")
    void recordFailure_incrementsBothDimensions() {
        when(valueOperations.increment("blog:login:fail:user:user1")).thenReturn(1L);
        when(valueOperations.increment("blog:login:fail:ip:1.2.3.4")).thenReturn(1L);

        loginAttemptService.recordFailure("user1", "1.2.3.4");

        verify(valueOperations).increment("blog:login:fail:user:user1");
        verify(valueOperations).increment("blog:login:fail:ip:1.2.3.4");
        verify(redisTemplate).expire(eq("blog:login:fail:user:user1"), any(Duration.class));
        verify(redisTemplate).expire(eq("blog:login:fail:ip:1.2.3.4"), any(Duration.class));
    }

    @Test
    @DisplayName("记录失败：非首次不再刷新 TTL 之外的重复 expire")
    void recordFailure_subsequentNoExpire() {
        when(valueOperations.increment("blog:login:fail:user:user1")).thenReturn(3L);
        when(valueOperations.increment("blog:login:fail:ip:1.2.3.4")).thenReturn(3L);

        loginAttemptService.recordFailure("user1", "1.2.3.4");

        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("登录成功：仅清除账号维度计数，IP 维度保留")
    void recordSuccess_deletesUserKeyOnly() {
        loginAttemptService.recordSuccess("User1");

        verify(redisTemplate).delete("blog:login:fail:user:user1");
        verify(redisTemplate, never()).delete(eq("blog:login:fail:ip:1.2.3.4"));
    }

    @Test
    @DisplayName("登录标识大小写归一：User1 与 user1 命中同一计数键")
    void recordSuccess_normalizesLoginId() {
        loginAttemptService.recordSuccess("User1");

        verify(redisTemplate).delete("blog:login:fail:user:user1");
    }

    @Test
    @DisplayName("Redis 异常时 fail-open：检查不抛异常")
    void checkLocked_redisError_failOpen() {
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("redis down"));

        assertDoesNotThrow(() -> loginAttemptService.checkLocked("user1", "1.2.3.4"));
    }
}
