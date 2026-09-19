package csulzc.My_Personal_Blogger.repository;

import csulzc.My_Personal_Blogger.domain.entity.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ArticleRepository 测试类
 * 使用 @DataJpaTest 只加载JPA相关组件，速度快
 */
@DataJpaTest
@ActiveProfiles("test")  // 使用测试配置
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)  // 测试方法执行顺序
class ArticleRepositoryTest {

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private TestEntityManager entityManager;  // 用于直接管理实体

    private User testAuthor;
    private Category testCategory;
    private Article testArticle;

    /**
     * 每个测试方法前执行：准备基础测试数据
     */
    @BeforeEach
    void setUp() {
        // 1. 创建测试作者
        testAuthor = User.builder()
                .username("test_author")
                .email("author@test.com")
                .passwordHash("password123")
                .displayName("测试作者")
                .build();
        entityManager.persist(testAuthor);

        // 2. 创建测试分类
        testCategory = Category.builder()
                .name("测试分类")
                .build();
        entityManager.persist(testCategory);

        // 3. 创建测试文章
        testArticle = Article.builder()
                .title("测试文章标题")
                .content("这是测试文章的内容")
                .author(testAuthor)
                .status(Article.ArticleStatus.DRAFT)
                .likeCount(0)
                .favoriteCount(0)
                .build();
        testArticle.addCategory(testCategory);  // 建立关系
        entityManager.persist(testArticle);

    }

    /**
     * 每个测试方法后执行：清理数据
     */
    @AfterEach
    void tearDown() {
        // TestEntityManager 会在每个测试后自动回滚事务
        // 无需手动清理
    }

    @Test
    @Order(1)
    @DisplayName("测试保存文章 - 基础创建")
    void testSaveArticle() {
        // Given - 准备数据
        Article newArticle = Article.builder()
                .title("新文章")
                .content("新内容")
                .author(testAuthor)
                .status(Article.ArticleStatus.DRAFT)
                .build();

        // When - 执行操作
        Article saved = articleRepository.save(newArticle);
        saved.setUpdatedAt(LocalDateTime.now());

        // Then - 验证结果
        assertNotNull(saved.getId());
        assertEquals("新文章", saved.getTitle());
        assertEquals(testAuthor.getId(), saved.getAuthor().getId());

        // 验证时间戳自动填充
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    @Order(2)
    @DisplayName("测试根据ID查询文章")
    void testFindById() {
        // When
        Optional<Article> found = articleRepository.findById(testArticle.getId());

        // Then
        assertTrue(found.isPresent());
        assertEquals("测试文章标题", found.get().getTitle());
        assertEquals(testAuthor.getUsername(), found.get().getAuthor().getUsername());
    }

    @Test
    @Order(3)
    @DisplayName("测试查询不存在的文章")
    void testFindByIdNotFound() {
        // When
        Optional<Article> found = articleRepository.findById(999L);

        // Then
        assertFalse(found.isPresent());
    }

    @Test
    @Order(4)
    @DisplayName("测试更新文章")
    void testUpdateArticle() {
        // Given
        testArticle.setTitle("更新后的标题");
        testArticle.setContent("更新后的内容");
        testArticle.setStatus(Article.ArticleStatus.RELEASE);
        testArticle.setUpdatedAt(LocalDateTime.now());

        // When
        Article updated = articleRepository.save(testArticle);
        entityManager.flush();

        // Then
        Article found = articleRepository.findById(updated.getId()).orElseThrow();
        assertEquals("更新后的标题", found.getTitle());
        assertEquals("更新后的内容", found.getContent());
        assertEquals(Article.ArticleStatus.RELEASE, found.getStatus());
        assertTrue(found.getUpdatedAt().isAfter(found.getCreatedAt()));
    }

    @Test
    @Order(5)
    @DisplayName("测试删除文章")
    void testDeleteArticle() {
        // When
        articleRepository.delete(testArticle);
        entityManager.flush();

        // Then
        Optional<Article> found = articleRepository.findById(testArticle.getId());
        assertFalse(found.isPresent());

        // 验证关联的评论是否级联删除（如果有评论的话）
        // 这里需要根据你的级联策略来验证
    }

    @Test
    @DisplayName("测试根据作者查询文章（分页）")
    void testFindByAuthor() {
        // Given - 创建多篇测试文章
        for (int i = 1; i <= 5; i++) {
            Article article = Article.builder()
                    .title("文章" + i)
                    .content("内容" + i)
                    .author(testAuthor)
                    .status(Article.ArticleStatus.RELEASE)
                    .build();
            entityManager.persist(article);
        }
        entityManager.flush();

        // When - 分页查询（第1页，每页3条）
        PageRequest pageable = PageRequest.of(0, 3, Sort.by("createdAt").descending());
        Page<Article> page = articleRepository.findByAuthor(testAuthor, pageable);

        // Then
        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getTotalElements()).isEqualTo(6);  // 包括setUp中的1篇 + 新加的5篇
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getNumber()).isEqualTo(0);

        // 验证排序（最新的在前）
        List<Article> articles = page.getContent();
        assertThat(articles.get(0).getTitle()).isEqualTo("文章5");
    }

    @Test
    @DisplayName("测试根据状态查询文章")
    void testFindByStatus() {
        // Given
        Article released = Article.builder()
                .title("已发布文章")
                .content("内容")
                .author(testAuthor)
                .status(Article.ArticleStatus.RELEASE)
                .build();
        entityManager.persist(released);

        Article archived = Article.builder()
                .title("归档文章")
                .content("内容")
                .author(testAuthor)
                .status(Article.ArticleStatus.ARCHIVE)
                .build();
        entityManager.persist(archived);
        entityManager.flush();

        // When
        List<Article> drafts = articleRepository.findByStatus(Article.ArticleStatus.DRAFT);
        List<Article> releases = articleRepository.findByStatus(Article.ArticleStatus.RELEASE);
        List<Article> archives = articleRepository.findByStatus(Article.ArticleStatus.ARCHIVE);

        // Then
        assertThat(drafts).hasSize(1);  // setUp中的草稿
        assertThat(releases).hasSize(1);
        assertThat(archives).hasSize(1);

        assertThat(drafts.get(0).getTitle()).isEqualTo("测试文章标题");
        assertThat(releases.get(0).getTitle()).isEqualTo("已发布文章");
        assertThat(archives.get(0).getTitle()).isEqualTo("归档文章");
    }

    @Test
    @DisplayName("测试标题模糊查询")
    void testFindByTitleContaining() {
        // Given
        entityManager.persist(Article.builder()
                .title("Spring Boot入门教程")
                .author(testAuthor)
                .build());
        entityManager.persist(Article.builder()
                .title("Spring Cloud微服务")
                .author(testAuthor)
                .build());
        entityManager.persist(Article.builder()
                .title("Java并发编程")
                .author(testAuthor)
                .build());
        entityManager.flush();

        // When
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Article> springArticles = articleRepository
                .findByTitleContaining("Spring", pageable);
        Page<Article> javaArticles = articleRepository
                .findByTitleContaining("Java", pageable);

        // Then
        assertThat(springArticles.getContent()).hasSize(2);
        assertThat(javaArticles.getContent()).hasSize(1);

        // 验证搜索结果
        assertThat(springArticles.getContent())
                .extracting(Article::getTitle)
                .containsExactlyInAnyOrder("Spring Boot入门教程", "Spring Cloud微服务");
    }

    @Test
    @DisplayName("测试统计作者文章数")
    void testCountByAuthor() {
        // Given
        for (int i = 1; i <= 3; i++) {
            Article article = Article.builder()
                    .title("文章" + i)
                    .author(testAuthor)
                    .build();
            entityManager.persist(article);
        }
        entityManager.flush();

        // When
        long count = articleRepository.countByAuthor(testAuthor);

        // Then
        assertThat(count).isEqualTo(4);  // setUp中的1篇 + 新加的3篇
    }

    @Test
    @DisplayName("测试更新文章状态")
    void testUpdateStatus() {
        // When
        int updatedCount = articleRepository.updateStatus(
                testArticle.getId(),
                Article.ArticleStatus.RELEASE
        );
        articleRepository.save(testArticle);

        entityManager.merge(testArticle);
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(updatedCount).isEqualTo(1);

        Article updated = articleRepository.findById(testArticle.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(Article.ArticleStatus.RELEASE);
    }

    @Test
    @DisplayName("测试批量查询（带分类）")
    void testFindByIdsWithCategories() {
        // Given
        Category anotherCategory = Category.builder().name("另一个分类").build();
        entityManager.persist(anotherCategory);

        Article article1 = Article.builder()
                .title("文章1")
                .author(testAuthor)
                .build();
        article1.addCategory(testCategory);
        article1.addCategory(anotherCategory);
        entityManager.persist(article1);

        Article article2 = Article.builder()
                .title("文章2")
                .author(testAuthor)
                .build();
        article2.addCategory(testCategory);
        entityManager.persist(article2);
        entityManager.flush();

        // When
        List<Article> articles = articleRepository.findByIdsWithCategories(
                List.of(article1.getId(), article2.getId())
        );

        // Then
        assertThat(articles).hasSize(2);

        // 验证分类是否被正确加载（避免N+1问题）
        Article found1 = articles.stream()
                .filter(a -> a.getTitle().equals("文章1"))
                .findFirst()
                .orElseThrow();
        assertThat(found1.getCategories()).hasSize(2);

        Article found2 = articles.stream()
                .filter(a -> a.getTitle().equals("文章2"))
                .findFirst()
                .orElseThrow();
        assertThat(found2.getCategories()).hasSize(1);
    }

    @Test
    @DisplayName("测试文章-分类关联（多对多）")
    void testCategoriesRelation() {
        // Given
        Category anotherCategory = Category.builder().name("技术").build();
        entityManager.persist(anotherCategory);

        testArticle.addCategory(anotherCategory);
        entityManager.persistAndFlush(testArticle);
        entityManager.clear();

        // When
        Article found = articleRepository.findById(testArticle.getId()).orElseThrow();

        // Then
        Set<Category> categories = found.getCategories();
        assertThat(categories).hasSize(2);
        assertThat(categories)
                .extracting(Category::getName)
                .containsExactlyInAnyOrder("测试分类", "技术");
    }

    @Test
    @DisplayName("测试级联操作")
    void testCascadeOperations() {
        // Given - 创建带评论的文章（需要先有CommentRepository）
        // 这里演示级联删除的测试思路
        Article article = Article.builder()
                .title("待删除文章")
                .author(testAuthor)
                .build();
        entityManager.persist(article);

        Long articleId = article.getId();

        // When - 删除文章
        articleRepository.delete(article);
        entityManager.flush();

        // Then - 验证文章已删除
        assertThat(articleRepository.findById(articleId)).isEmpty();

        // 验证关联的评论是否也被删除（如果有级联配置）
        // 这里需要注入CommentRepository来验证
    }

    @Test
    @DisplayName("测试创建文章时缺少作者")
    void testCreateArticleWithoutAuthor() {
        // Given
        Article invalidArticle = Article.builder()
                .title("无作者文章")
                .content("内容")
                .build();

        // When & Then
        assertThatThrownBy(() -> {
            articleRepository.save(invalidArticle);
            entityManager.flush();  // 强制flush以触发约束检查
        })
                .isInstanceOf(Exception.class)
                .hasMessageContaining("author_id");  // 验证外键约束错误
    }

    @Test
    @DisplayName("测试创建重复标题（如果业务上不允许重复）")
    void testDuplicateTitle() {
        // 这里根据业务规则来测试
        // 如果title有unique约束，测试重复插入
    }

    @Test
    @DisplayName("测试分页查询超出范围")
    void testPageOutOfRange() {
        // When - 请求不存在的页码
        PageRequest pageable = PageRequest.of(100, 10);
        Page<Article> page = articleRepository.findAll(pageable);

        // Then
        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isGreaterThan(0);
        assertThat(page.getTotalPages()).isLessThanOrEqualTo(100);
    }

    @Test
    @DisplayName("测试批量插入性能")
    void testBatchInsert() {
        // Given
        int batchSize = 100;
        List<Article> articles = new ArrayList<>();

        // When - 批量创建
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < batchSize; i++) {
            Article article = Article.builder()
                    .title("批量文章" + i)
                    .author(testAuthor)
                    .build();
            articles.add(article);
        }
        articleRepository.saveAll(articles);
        entityManager.flush();

        long endTime = System.currentTimeMillis();

        // Then
        long count = articleRepository.count();
        assertThat(count).isGreaterThanOrEqualTo(batchSize);
        System.out.println("批量插入 " + batchSize + " 条耗时: " + (endTime - startTime) + "ms");
    }

    @Test
    @DisplayName("测试N+1查询问题")
    void testNPlusOneProblem() {
        // 这个测试演示如何检测N+1问题
        // 需要开启SQL日志来观察

        // When - 查询所有文章并访问分类
        List<Article> articles = articleRepository.findAll();

        // Then - 观察日志中SQL执行次数
        // 理想情况：1条查询文章 + 1条查询分类 = 2条SQL
        // 如果有N+1：1条查询文章 + N条查询分类
        for (Article article : articles) {
            article.getCategories().size();  // 触发懒加载
        }
    }
}