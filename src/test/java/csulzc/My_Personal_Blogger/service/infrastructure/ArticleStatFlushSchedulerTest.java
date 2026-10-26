package csulzc.My_Personal_Blogger.service.infrastructure;

import csulzc.My_Personal_Blogger.config.ArticleStatProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ArticleStatFlushScheduler 批量落库幂等性测试（纯 Mockito）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ArticleStatFlushScheduler 批量落库测试")
class ArticleStatFlushSchedulerTest {

    @Mock
    private ArticleStatService articleStatService;
    @Mock
    private ArticleStatProperties properties;
    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;

    private ArticleStatFlushScheduler scheduler;

    @BeforeEach
    void setUp() {
        when(properties.getFlushBatchSize()).thenReturn(500);
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        scheduler = new ArticleStatFlushScheduler(articleStatService, properties, jdbcTemplate, transactionTemplate);
    }

    @Test
    @DisplayName("浏览量增量按文章聚合成一次批量 UPDATE")
    void flush_persistsViewDeltasInBatch() {
        when(articleStatService.dirtyArticleIds(500)).thenReturn(List.of(1L, 2L));
        when(articleStatService.takeViewDelta(1L)).thenReturn(3L);
        when(articleStatService.takeViewDelta(2L)).thenReturn(0L);
        when(jdbcTemplate.batchUpdate(contains("UPDATE articles SET view_count"), any(SqlParameterSource[].class)))
                .thenReturn(new int[]{1});

        scheduler.flush();

        ArgumentCaptor<SqlParameterSource[]> captor = ArgumentCaptor.forClass(SqlParameterSource[].class);
        verify(jdbcTemplate).batchUpdate(contains("UPDATE articles SET view_count"), captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(((MapSqlParameterSource) captor.getValue()[0]).getValue("id")).isEqualTo(1L);
        assertThat(((MapSqlParameterSource) captor.getValue()[0]).getValue("delta")).isEqualTo(3L);
        verify(articleStatService, never()).restoreViewDelta(anyLong(), anyLong());
    }

    @Test
    @DisplayName("浏览量落库失败时把增量回填 Redis")
    void flush_restoresViewDeltas_whenDbFails() {
        when(articleStatService.dirtyArticleIds(500)).thenReturn(List.of(1L));
        when(articleStatService.takeViewDelta(1L)).thenReturn(3L);
        when(jdbcTemplate.batchUpdate(contains("UPDATE articles SET view_count"), any(SqlParameterSource[].class)))
                .thenThrow(new RuntimeException("db down"));

        scheduler.flush();

        verify(articleStatService).restoreViewDelta(1L, 3L);
    }

    @Test
    @DisplayName("点赞按实际插入行数累加，重复事件不会多计数")
    void flush_persistsLikesByIdempotentInsert() {
        when(articleStatService.dirtyArticleIds(500)).thenReturn(List.of());
        when(articleStatService.pendingLikeMembers(500))
                .thenReturn(Set.of("10:100", "10:101", "20:200"));
        when(jdbcTemplate.queryForList(contains("SELECT id FROM articles"), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(List.of(10L));
        when(jdbcTemplate.batchUpdate(contains("INSERT IGNORE INTO article_like"), any(SqlParameterSource[].class)))
                .thenReturn(new int[]{1, 0});
        when(jdbcTemplate.batchUpdate(contains("UPDATE articles SET like_count"), any(SqlParameterSource[].class)))
                .thenReturn(new int[]{1});

        scheduler.flush();

        ArgumentCaptor<SqlParameterSource[]> insertCaptor = ArgumentCaptor.forClass(SqlParameterSource[].class);
        verify(jdbcTemplate).batchUpdate(contains("INSERT IGNORE INTO article_like"), insertCaptor.capture());
        assertThat(insertCaptor.getValue()).hasSize(2);
        assertThat(Arrays.stream(insertCaptor.getValue())
                .map(param -> ((MapSqlParameterSource) param).getValue("articleId"))
                .toList()).containsOnly(10L);

        ArgumentCaptor<SqlParameterSource[]> updateCaptor = ArgumentCaptor.forClass(SqlParameterSource[].class);
        verify(jdbcTemplate).batchUpdate(contains("UPDATE articles SET like_count"), updateCaptor.capture());
        assertThat(updateCaptor.getValue()).hasSize(1);
        assertThat(((MapSqlParameterSource) updateCaptor.getValue()[0]).getValue("id")).isEqualTo(10L);
        assertThat(((MapSqlParameterSource) updateCaptor.getValue()[0]).getValue("delta")).isEqualTo(1L);

        verify(articleStatService).removePendingLikeMembers(Set.of("10:100", "10:101", "20:200"));
    }

    @Test
    @DisplayName("点赞落库失败时保留待落库事件")
    void flush_keepsPendingLikes_whenDbFails() {
        when(articleStatService.dirtyArticleIds(500)).thenReturn(List.of());
        when(articleStatService.pendingLikeMembers(500)).thenReturn(Set.of("10:100"));
        when(jdbcTemplate.queryForList(contains("SELECT id FROM articles"), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(List.of(10L));
        when(jdbcTemplate.batchUpdate(contains("INSERT IGNORE INTO article_like"), any(SqlParameterSource[].class)))
                .thenThrow(new RuntimeException("db down"));

        scheduler.flush();

        verify(articleStatService, never()).removePendingLikeMembers(anyCollection());
    }
}
