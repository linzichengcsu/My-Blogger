package csulzc.My_Personal_Blogger.service;

import csulzc.My_Personal_Blogger.api.dto.article.ArticleCreateRequest;
import csulzc.My_Personal_Blogger.api.dto.article.ArticleDetailDTO;
import csulzc.My_Personal_Blogger.api.dto.article.ArticleListItemDTO;
import csulzc.My_Personal_Blogger.api.dto.article.ArticleUpdateRequest;
import csulzc.My_Personal_Blogger.api.dto.category.CategoryRequest;
import csulzc.My_Personal_Blogger.api.dto.category.CategoryTreeDTO;
import csulzc.My_Personal_Blogger.api.dto.common.PageResponseDTO;
import csulzc.My_Personal_Blogger.config.RedisConfig;
import csulzc.My_Personal_Blogger.domain.entity.Article;
import csulzc.My_Personal_Blogger.domain.entity.Category;
import csulzc.My_Personal_Blogger.domain.entity.User;
import csulzc.My_Personal_Blogger.repository.ArticleRepository;
import csulzc.My_Personal_Blogger.repository.CategoryRepository;
import csulzc.My_Personal_Blogger.repository.UserRepository;
import csulzc.My_Personal_Blogger.security.SecurityContextUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Redis 缓存集成测试
 * <p>
 * 前置条件：本地 Docker 容器 blog-redis 已启动（docker exec blog-redis redis-cli ping 返回 PONG）
 * 覆盖场景：缓存写入、缓存命中（不查库）、缓存值反序列化、写操作驱逐缓存
 */
@DataJpaTest
@ActiveProfiles("test")
@ImportAutoConfiguration({AopAutoConfiguration.class, DataRedisAutoConfiguration.class})
@Import({RedisConfig.class, ArticleService.class, CategoryService.class, SecurityContextUtil.class})
@TestPropertySource(properties = "app.cache.enabled=true")
@DisplayName("Redis 缓存测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RedisCacheTest {

    @Autowired
    private ArticleService articleService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CacheManager cacheManager;

    private User testUser;
    private Category testCategory;
    private Long testUserId;

    private void setCurrentUser(Long userId) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @BeforeEach
    void setUp() {
        // 清空所有 Redis 缓存：测试方法事务回滚不会回滚 Redis，必须手动清理避免残留数据互相干扰
        cacheManager.getCacheNames()
                .forEach(cacheName -> cacheManager.getCache(cacheName).clear());

        testUser = User.builder()
                .username("testuser")
                .email("test@example.com")
                .passwordHash("password123")
                .displayName("测试用户")
                .status(User.UserStatus.ACTIVE)
                .build();
        entityManager.persist(testUser);
        testUserId = testUser.getId();

        testCategory = Category.builder()
                .name("测试分类")
                .description("这是测试分类的描述")
                .build();
        entityManager.persist(testCategory);
        entityManager.flush();

        setCurrentUser(testUserId);
    }

    @AfterEach
    void tearDown() {
        entityManager.clear();
        SecurityContextHolder.clearContext();
    }

    private Article createArticleEntity(String title) {
        Article article = Article.builder()
                .title(title)
                .content("这是用于测试 Redis 缓存的文章内容，长度足够二十个字符以上")
                .summary("这是用于测试缓存的文章摘要")
                .status(Article.ArticleStatus.RELEASE)
                .author(testUser)
                .build();
        article.addCategory(testCategory);
        entityManager.persist(article);
        entityManager.flush();
        return article;
    }

    @Test
    @Order(1)
    @DisplayName("测试文章详情缓存 - 第二次调用命中缓存不查库")
    void testArticleDetail_SecondCallHitsCache() {
        // Given - 创建文章并首次查询（写入缓存）
        Article article = createArticleEntity("缓存命中测试");
        Long articleId = article.getId();

        ArticleDetailDTO first = articleService.getArticleById(articleId);
        assertNotNull(first);
        assertEquals("缓存命中测试", first.getTitle());

        // When - 直接修改数据库，绕过 Service 层（模拟外部数据变更）
        article.setTitle("数据库修改后的标题");
        entityManager.flush();
        entityManager.clear();

        // Then - 再次查询应命中 Redis 缓存返回旧值（若未命中则查库返回新标题，断言失败）
        ArticleDetailDTO second = articleService.getArticleById(articleId);
        assertEquals("缓存命中测试", second.getTitle());

        Cache detailCache = cacheManager.getCache("article:detail");
        assertThat(detailCache).isNotNull();
        assertThat(detailCache.get(articleId)).isNotNull();
    }

    @Test
    @Order(2)
    @DisplayName("测试缓存值序列化往返 - 反序列化后字段完整")
    void testCacheValue_SerializationRoundTrip() {
        // Given - 创建文章并连续查询两次（第二次从 Redis 反序列化）
        Article article = createArticleEntity("序列化往返测试");
        Long articleId = article.getId();

        ArticleDetailDTO first = articleService.getArticleById(articleId);

        // When - 第二次调用命中缓存并反序列化
        // Then - 字段完整（若 DTO 缺少 @NoArgsConstructor，此处会抛 InvalidDefinitionException）
        ArticleDetailDTO second = articleService.getArticleById(articleId);

        assertThat(second).isInstanceOf(ArticleDetailDTO.class);
        assertEquals(articleId, second.getId());
        assertEquals(first.getTitle(), second.getTitle());
        assertEquals(Article.ArticleStatus.RELEASE, second.getStatus());
        assertNotNull(second.getAuthor());
        assertEquals("testuser", second.getAuthor().getUsername());
        assertThat(second.getCategories()).hasSize(1);
        assertEquals("测试分类", second.getCategories().get(0).getName());
    }

    @Test
    @Order(3)
    @DisplayName("测试更新文章 - 驱逐详情缓存")
    void testUpdateArticle_EvictsDetailCache() {
        // Given - 查询文章使详情缓存写入
        Article article = createArticleEntity("缓存驱逐测试");
        Long articleId = article.getId();
        articleService.getArticleById(articleId);

        Cache detailCache = cacheManager.getCache("article:detail");
        assertThat(detailCache.get(articleId)).isNotNull();

        // When - 更新文章
        ArticleUpdateRequest updateRequest = ArticleUpdateRequest.builder()
                .title("更新后的缓存测试标题")
                .build();
        articleService.updateArticle(articleId, updateRequest);

        // Then - 详情缓存被驱逐，再次查询返回新数据
        assertThat(detailCache.get(articleId)).isNull();

        ArticleDetailDTO after = articleService.getArticleById(articleId);
        assertEquals("更新后的缓存测试标题", after.getTitle());
    }

    @Test
    @Order(4)
    @DisplayName("测试创建文章 - 驱逐文章列表缓存")
    void testCreateArticle_EvictsListCache() {
        // Given - 查询列表使列表缓存写入
        Pageable pageable = PageRequest.of(0, 10);
        articleService.getArticleList(pageable);

        Cache listCache = cacheManager.getCache("article:list");
        assertThat(listCache.get(pageable)).isNotNull();

        // When - 创建新文章
        ArticleCreateRequest createRequest = ArticleCreateRequest.builder()
                .title("缓存联动新文章")
                .content("这是用于测试列表缓存驱逐的新文章内容，长度足够二十个字符")
                .categoryIds(Set.of(testCategory.getId()))
                .status(Article.ArticleStatus.RELEASE)
                .build();
        articleService.createArticle(createRequest);

        // Then - 列表缓存被清空（allEntries = true），重新查询包含新文章
        assertThat(listCache.get(pageable)).isNull();

        PageResponseDTO<ArticleListItemDTO> refreshed = articleService.getArticleList(pageable);
        assertThat(refreshed.getTotalElements()).isEqualTo(1);
    }

    @Test
    @Order(5)
    @DisplayName("测试分类树缓存 - 创建分类后驱逐")
    void testCategoryTree_CacheAndEvict() {
        // Given - 首次构建分类树（写入缓存）
        List<CategoryTreeDTO> firstTree = categoryService.buildCategoryTree();
        assertThat(firstTree).hasSize(1);

        Cache treeCache = cacheManager.getCache("category:tree");
        assertThat(treeCache.get(SimpleKey.EMPTY)).isNotNull();

        // When - 创建新顶级分类
        CategoryRequest createRequest = CategoryRequest.builder()
                .name("Python")
                .description("Python 编程语言")
                .build();
        categoryService.createCategory(createRequest);

        // Then - 分类树缓存被驱逐，重建后包含新分类
        assertThat(treeCache.get(SimpleKey.EMPTY)).isNull();

        List<CategoryTreeDTO> refreshedTree = categoryService.buildCategoryTree();
        assertThat(refreshedTree).hasSize(2);
        assertThat(refreshedTree)
                .extracting(CategoryTreeDTO::getName)
                .contains("Python");
    }
}