package csulzc.My_Personal_Blogger.concurrent;

import csulzc.My_Personal_Blogger.StressParams;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并发正确性测试（H2 内存库，默认运行，无需 Docker）。
 *
 * <p>覆盖与数据库引擎无关的并发不变量：</p>
 * <ul>
 *   <li>S1：同一用户并发点赞 → 唯一约束保证只持久化 1 行、计数只 +1（H2 抛异常 / MySQL 返回 false，
 *       但<b>不变量一致</b>）；</li>
 *   <li>S2：多用户并发点赞 → 原子 UPDATE 不丢计数；</li>
 *   <li>S3：并发浏览 → 原子 UPDATE 不丢计数。</li>
 * </ul>
 *
 * <p>依赖 MySQL 特有语义的断言（{@code INSERT IGNORE} 静默去重、InnoDB 行锁下的注册唯一约束竞态）
 * 见 {@link MysqlConcurrentCorrectnessTest}，需要 Docker 时才运行。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@DisabledIfSystemProperty(named = "concurrent.tests.skip", matches = "true")
@DisplayName("并发正确性测试（H2，引擎无关不变量）")
class ConcurrentCorrectnessTest extends AbstractConcurrentCorrectnessTest {

    @Test
    @DisplayName("S1 同用户并发点赞：唯一约束保证只计数一次（H2 抛异常 / MySQL 返回 false，不变量一致）")
    void sameUserLikesConcurrently_dedupExactlyOnce() {
        Long articleId = seedArticle("s1");
        Long userId = seedUser("s1u");

        int threads = StressParams.threads("like-dedup", 60);
        RunResult result = runConcurrently("like-dedup", threads,
                i -> articleStatService.recordLike(articleId, userId));

        // 跨引擎数据不变量：只有 1 次点赞真正生效；所有线程都有明确结果（成功或被并发拒绝）
        assertThat(result.accepted()).as("同一用户并发点赞只应被接受 1 次").isEqualTo(1);
        assertThat(result.success() + result.errors().size())
                .as("所有线程都应有明确结果（成功或被拒）").isEqualTo(threads);
        // H2 用表级锁，重复键并发可能表现为唯一约束冲突，也可能是锁等待超时(CannotAcquireLockException)；
        // 二者都只是"并发拒绝"、不会破坏数据。MySQL 下 errors 为空（INSERT IGNORE 静默返回 0），空集断言恒真。
        assertThat(result.errors()).as("重复点赞的失败只能是并发拒绝（约束冲突或锁超时）")
                .allSatisfy(t -> assertThat(t)
                        .isInstanceOfAny(DataIntegrityViolationException.class, CannotAcquireLockException.class));

        // 持久化正确性：article_like 表中 (articleId, userId) 只有 1 行，likeCount 只累加到 1
        assertThat(countLikes(articleId, userId)).as("点赞记录表应只插入 1 行").isEqualTo(1L);
        assertThat(getLikeCount(articleId)).as("点赞数应恰好为 1").isEqualTo(1);
    }

    @Test
    @DisplayName("S2 多用户并发点赞：原子 UPDATE 不丢计数")
    void distinctUsersLikeConcurrently_noLostUpdate() {
        Long articleId = seedArticle("s2");
        int threads = StressParams.threads("like-count", 80);

        RunResult result = runConcurrently("like-count", threads,
                i -> articleStatService.recordLike(articleId, 10_000L + i));

        assertThat(result.accepted()).as(threads + " 个不同用户的点赞都应被接受").isEqualTo(threads);
        assertThat(result.success()).isEqualTo(threads);
        assertThat(result.errors()).isEmpty();

        assertThat(getLikeCount(articleId)).as(threads + " 次原子累加后点赞数应恰好为 " + threads).isEqualTo(threads);
    }

    @Test
    @DisplayName("S3 并发浏览量累加：原子 UPDATE 无丢失")
    void concurrentViews_noLostUpdate() {
        Long articleId = seedArticle("s3");
        int threads = StressParams.threads("view-count", 200);

        RunResult result = runConcurrently("view-count", threads, i -> {
            articleStatService.recordView(articleId);
            return true;
        });

        assertThat(result.success()).as(threads + " 次浏览都应成功").isEqualTo(threads);
        assertThat(result.errors()).isEmpty();

        assertThat(getViewCount(articleId)).as(threads + " 次原子累加后浏览量应恰好为 " + threads).isEqualTo(threads);
    }
}
