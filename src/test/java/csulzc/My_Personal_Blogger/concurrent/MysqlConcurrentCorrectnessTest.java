package csulzc.My_Personal_Blogger.concurrent;

import csulzc.My_Personal_Blogger.StressParams;
import csulzc.My_Personal_Blogger.api.dto.user.UserRegisterRequest;
import csulzc.My_Personal_Blogger.domain.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并发正确性测试（真实 MySQL，复现生产语义）。
 *
 * <p>H2 无法等价模拟的两类 MySQL 行为，必须用真实 InnoDB 验证：</p>
 * <ul>
 *   <li>S1-strict：{@code INSERT IGNORE} 对重复键<b>静默返回 0 而非抛异常</b>（H2 会抛异常）——
 *       验证点赞去重降级路径在 MySQL 下的优雅返回契约；</li>
 *   <li>S4：并发注册同名用户 → InnoDB <b>行级锁</b>下只有 1 个成功，其余被唯一约束拒绝
 *       （H2 用表级锁会触发 {@code CannotAcquireLockException}，与生产行为不符）。</li>
 * </ul>
 *
 * <p>需要 Docker 守护进程可用；本机未启动 Docker 时由
 * {@code @Testcontainers(disabledWithoutDocker = true)} 自动跳过，不影响默认 {@code mvn test}。
 * CI（ubuntu-latest）自带 Docker，会自动执行。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@DisabledIfSystemProperty(named = "concurrent.tests.skip", matches = "true")
@DisplayName("并发正确性测试（真实 MySQL，生产语义）")
class MysqlConcurrentCorrectnessTest extends AbstractConcurrentCorrectnessTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("blog_concurrent")
            .withUsername("tc")
            .withPassword("tc");

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8",
                MYSQL.getHost(), MYSQL.getMappedPort(3306), MYSQL.getDatabaseName()));
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        // 覆盖 test profile 中硬编码的 H2 方言，否则 Hibernate 用 H2 方言连 MySQL 会生成不兼容 SQL
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.MySQLDialect");
    }

    @Test
    @DisplayName("S1-strict 同用户并发点赞：MySQL INSERT IGNORE 静默去重，所有请求正常完成")
    void sameUserLikesConcurrently_insertIgnoreGracefulOnMysql() {
        Long articleId = seedArticle("ms1");
        Long userId = seedUser("ms1u");

        int threads = StressParams.threads("mysql-like-dedup", 60);
        RunResult result = runConcurrently("mysql-like-dedup", threads,
                i -> articleStatService.recordLike(articleId, userId));

        // MySQL 语义：INSERT IGNORE 命中唯一约束时返回 0（不抛异常），因此 60 个请求全部正常完成
        assertThat(result.success()).as("INSERT IGNORE 在 MySQL 下不应抛异常，所有请求都应正常完成")
                .isEqualTo(threads);
        assertThat(result.errors()).as("不应有异常").isEmpty();
        assertThat(result.accepted()).as("同一用户并发点赞只应被接受 1 次").isEqualTo(1);

        assertThat(countLikes(articleId, userId)).as("点赞记录表应只插入 1 行").isEqualTo(1L);
        assertThat(getLikeCount(articleId)).as("点赞数应恰好为 1").isEqualTo(1);
    }

    @Test
    @DisplayName("S4 并发注册同名用户：InnoDB 行锁 + 唯一约束保证只持久化 1 条")
    void concurrentRegisterSameUsername_onlyOnePersisted() {
        String username = uniq("mreg");
        String email = username + "@example.com";
        int threads = StressParams.threads("mysql-register-uniq", 30);

        RunResult result = runConcurrently("mysql-register-uniq", threads, i -> {
            userService.register(UserRegisterRequest.builder()
                    .username(username)
                    .email(email)
                    .password("TestPassw0rd!")
                    .displayName(username)
                    .build(), User.UserRole.USER);
            return true;
        });

        // 30 个并发请求只有 1 个成功（无异常），其余全部被拒绝
        assertThat(result.success()).as("同名并发注册只应有 1 次成功").isEqualTo(1);
        assertThat(result.errors()).hasSize(threads - 1);

        // 失败原因只能是两种：先查到已存在 → IllegalArgumentException；撞唯一约束 → DataIntegrityViolationException
        // （H2 下会出现 CannotAcquireLockException，MySQL InnoDB 行锁不会）
        assertThat(result.errors()).allSatisfy(t -> assertThat(t)
                .isInstanceOfAny(IllegalArgumentException.class, DataIntegrityViolationException.class));

        // 持久化正确性：数据库中该用户名只存在 1 条记录（唯一约束是最终防线）
        assertThat(countUsersByUsername(username)).as("数据库中该用户名应只持久化 1 条").isEqualTo(1L);
    }
}
