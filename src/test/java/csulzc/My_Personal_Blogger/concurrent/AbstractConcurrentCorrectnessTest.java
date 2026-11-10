package csulzc.My_Personal_Blogger.concurrent;

import csulzc.My_Personal_Blogger.domain.entity.Article;
import csulzc.My_Personal_Blogger.domain.entity.User;
import csulzc.My_Personal_Blogger.repository.ArticleLikeRepository;
import csulzc.My_Personal_Blogger.repository.ArticleRepository;
import csulzc.My_Personal_Blogger.repository.UserRepository;
import csulzc.My_Personal_Blogger.service.business.UserService;
import csulzc.My_Personal_Blogger.service.infrastructure.ArticleStatService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * 并发正确性测试的共享基类：双闩锁并发执行器 + 数据准备/读取 + 性能指标汇总。
 *
 * <p>子类只需编写 {@code @Test} 方法调用 {@link #runConcurrently} 并断言最终持久化状态即可。
 * 断言必须基于<b>与执行时序无关的不变量</b>（行数、计数），不能依赖线程调度顺序，否则会偶发抖动。</p>
 */
abstract class AbstractConcurrentCorrectnessTest {

    protected static final Logger log = LoggerFactory.getLogger(AbstractConcurrentCorrectnessTest.class);

    /** 每个用例的等待上限（毫秒），避免异常时整轮挂死 */
    protected static final long AWAIT_TIMEOUT_MS = 60_000L;

    private static final AtomicLong SEQ = new AtomicLong(0);

    @Autowired
    protected ArticleStatService articleStatService;

    @Autowired
    protected UserService userService;

    @Autowired
    protected ArticleRepository articleRepository;

    @Autowired
    protected ArticleLikeRepository articleLikeRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected TransactionTemplate transactionTemplate;

    @PersistenceContext
    protected EntityManager entityManager;

    private final List<ExecutorService> pools = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (ExecutorService pool : pools) {
            pool.shutdownNow();
        }
        pools.clear();
    }

    // ─────────────────────────── 并发 harness ───────────────────────────

    /**
     * 双闩锁并发执行：所有线程在 startGate 上排队，主线程 countDown 后瞬间齐射，
     * 再用 endGate 等待全部收敛，避免线程分批到达造成假阴性。
     *
     * @param task 第 i 个线程的任务，返回值表示"被接受/有意义的成功"（如点赞去重返回 true）；
     *             无返回值的场景（浏览/注册）在无异常时返回 true 即可
     */
    protected RunResult runConcurrently(String name, int threads, IntFunction<Boolean> task) {
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "concurrent-" + name);
            t.setDaemon(true);
            return t;
        });
        pools.add(pool);

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger accepted = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Long> latenciesNs = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    startGate.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    endGate.countDown();
                    return;
                }
                long start = System.nanoTime();
                try {
                    Boolean acc = task.apply(idx);
                    success.incrementAndGet();
                    if (Boolean.TRUE.equals(acc)) {
                        accepted.incrementAndGet();
                    }
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    latenciesNs.add(System.nanoTime() - start);
                    endGate.countDown();
                }
            });
        }

        long wallStart = System.nanoTime();
        startGate.countDown();
        boolean finished = await(endGate);
        long wallMs = (System.nanoTime() - wallStart) / 1_000_000L;

        RunResult result = new RunResult(threads, success.get(), accepted.get(),
                new ArrayList<>(errors), new ArrayList<>(latenciesNs), wallMs, finished);
        log.info("[{}] {}", name, result.summary());
        return result;
    }

    private boolean await(CountDownLatch latch) {
        try {
            return latch.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // ─────────────────────────── 数据准备 / 读取 ───────────────────────────

    protected Long seedArticle(String prefix) {
        Long authorId = seedUser(prefix + "a");
        return inTx(() -> {
            User author = userRepository.findById(authorId).orElseThrow();
            Article article = Article.builder()
                    .title(uniq(prefix))
                    .content("content-" + prefix)
                    .summary("summary-" + prefix)
                    .status(Article.ArticleStatus.RELEASE)
                    .author(author)
                    .likeCount(0)
                    .favoriteCount(0)
                    .viewCount(0)
                    .build();
            return articleRepository.save(article).getId();
        });
    }

    protected Long seedUser(String prefix) {
        return inTx(() -> {
            User user = User.builder()
                    .username(uniq(prefix))
                    .email(uniq(prefix) + "@example.com")
                    .passwordHash("$2a$10$dummyhashvaluefordatapurposeonly12345678901234567890")
                    .displayName(prefix)
                    .status(User.UserStatus.ACTIVE)
                    .role(User.UserRole.USER)
                    .build();
            return userRepository.save(user).getId();
        });
    }

    protected int getLikeCount(Long articleId) {
        return inTx(() -> articleRepository.findById(articleId).orElseThrow().getLikeCount());
    }

    protected int getViewCount(Long articleId) {
        return inTx(() -> articleRepository.findById(articleId).orElseThrow().getViewCount());
    }

    protected long countLikes(Long articleId, Long userId) {
        return inTx(() -> entityManager.createQuery(
                        "SELECT COUNT(al) FROM ArticleLike al WHERE al.articleId = :a AND al.userId = :u", Long.class)
                .setParameter("a", articleId)
                .setParameter("u", userId)
                .getSingleResult());
    }

    protected long countUsersByUsername(String username) {
        return inTx(() -> entityManager.createQuery(
                        "SELECT COUNT(u) FROM User u WHERE u.username = :un", Long.class)
                .setParameter("un", username)
                .getSingleResult());
    }

    protected <T> T inTx(Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }

    protected static String uniq(String prefix) {
        return prefix + "_" + SEQ.incrementAndGet();
    }

    // ─────────────────────────── 结果模型 ───────────────────────────

    protected record RunResult(int threads, int success, int accepted,
                               List<Throwable> errors, List<Long> latenciesNs,
                               long wallMs, boolean finished) {

        String summary() {
            long min = latenciesNs.stream().mapToLong(Long::longValue).min().orElse(0);
            long max = latenciesNs.stream().mapToLong(Long::longValue).max().orElse(0);
            double avg = latenciesNs.stream().mapToLong(Long::longValue).average().orElse(0);
            double throughput = wallMs > 0 ? success * 1000.0 / wallMs : 0;
            String status = finished ? "OK" : "TIMEOUT";
            return String.format("%s threads=%d wall=%dms success=%d accepted=%d errors=%d " +
                            "latency(min/avg/max)=%.2f/%.2f/%.2fms throughput=%.1f ops/s",
                    status, threads, wallMs, success, accepted, errors.size(),
                    min / 1_000_000.0, avg / 1_000_000.0, max / 1_000_000.0, throughput);
        }
    }
}
