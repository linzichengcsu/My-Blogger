package csulzc.My_Personal_Blogger.service.infrastructure;

import csulzc.My_Personal_Blogger.config.ArticleStatProperties;
import csulzc.My_Personal_Blogger.repository.ArticleLikeRepository;
import csulzc.My_Personal_Blogger.repository.ArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ArticleStatService 计数降级逻辑测试（纯 Mockito，不依赖真实 Redis/数据库）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ArticleStatService 计数降级测试")
class ArticleStatServiceTest {

    @Mock
    private ArticleStatProperties properties;
    @Mock
    private ArticleRepository articleRepository;
    @Mock
    private ArticleLikeRepository articleLikeRepository;
    @Mock
    private ObjectProvider<StringRedisTemplate> redisProvider;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;

    private ArticleStatService service;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        service = new ArticleStatService(properties, articleRepository, articleLikeRepository,
                redisProvider, transactionTemplate);
    }

    @Test
    @DisplayName("模块关闭时浏览同步写数据库")
    void recordView_fallsBackToDb_whenDisabled() {
        when(properties.isEnabled()).thenReturn(false);

        service.recordView(1L);

        verify(articleRepository).incrementViewCount(1L);
        verifyNoInteractions(redisProvider);
    }

    @Test
    @DisplayName("Redis 异常时浏览降级为数据库同步更新")
    void recordView_fallsBackToDb_whenRedisFails() {
        when(properties.isEnabled()).thenReturn(true);
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        doThrow(new RuntimeException("redis down"))
                .when(redis).execute(any(RedisScript.class), anyList(), any());

        service.recordView(1L);

        verify(articleRepository).incrementViewCount(1L);
    }

    @Test
    @DisplayName("首次点赞同步写库并累加点赞数")
    void recordLike_fallsBackToDb_whenDisabled() {
        when(properties.isEnabled()).thenReturn(false);
        when(articleRepository.existsById(1L)).thenReturn(true);
        when(articleLikeRepository.insertIgnore(eq(1L), eq(2L), any(LocalDateTime.class))).thenReturn(1);

        boolean accepted = service.recordLike(1L, 2L);

        assertThat(accepted).isTrue();
        verify(articleRepository).incrementLikeCount(1L);
    }

    @Test
    @DisplayName("重复点赞同步写库时不再累加点赞数")
    void recordLike_duplicateDoesNotIncrement_whenDisabled() {
        when(properties.isEnabled()).thenReturn(false);
        when(articleRepository.existsById(1L)).thenReturn(true);
        when(articleLikeRepository.insertIgnore(eq(1L), eq(2L), any(LocalDateTime.class))).thenReturn(0);

        boolean accepted = service.recordLike(1L, 2L);

        assertThat(accepted).isFalse();
        verify(articleRepository, never()).incrementLikeCount(anyLong());
    }

    @Test
    @DisplayName("文章不存在时点赞被拒绝")
    void recordLike_rejectsMissingArticle_whenDisabled() {
        when(properties.isEnabled()).thenReturn(false);
        when(articleRepository.existsById(1L)).thenReturn(false);

        boolean accepted = service.recordLike(1L, 2L);

        assertThat(accepted).isFalse();
        verifyNoInteractions(articleLikeRepository);
    }

    @Test
    @DisplayName("Redis 异常时点赞降级为数据库同步去重")
    void recordLike_fallsBackToDb_whenRedisFails() {
        when(properties.isEnabled()).thenReturn(true);
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        doThrow(new RuntimeException("redis down"))
                .when(redis).execute(any(RedisScript.class), anyList(), any(), any(), any(), any());
        when(articleRepository.existsById(1L)).thenReturn(true);
        when(articleLikeRepository.insertIgnore(eq(1L), eq(2L), any(LocalDateTime.class))).thenReturn(1);

        boolean accepted = service.recordLike(1L, 2L);

        assertThat(accepted).isTrue();
        verify(articleRepository).incrementLikeCount(1L);
    }
}
