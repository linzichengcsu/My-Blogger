package csulzc.My_Personal_Blogger.service;

import csulzc.My_Personal_Blogger.api.dto.category.CategoryDTO;
import csulzc.My_Personal_Blogger.api.dto.user.UserProfileDTO;
import csulzc.My_Personal_Blogger.domain.entity.*;
import csulzc.My_Personal_Blogger.repository.*;
import csulzc.My_Personal_Blogger.security.SecurityContextUtil;
import csulzc.My_Personal_Blogger.api.dto.article.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@ActiveProfiles("test")
@Import({ArticleService.class, CategoryService.class, SecurityContextUtil.class})
@DisplayName("ArticleService 测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ArticleServiceTest {
    @Autowired
    private ArticleService articleService;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SecurityContextUtil securityContextUtil;

    private ArticleCreateRequest articleCreateRequest;
    private ArticleUpdateRequest articleUpdateRequest;
    private ArticleDetailDTO articleDetailDTO;
    private ArticleListItemDTO  articleListItemDTO;

    private Article testArticle;
    private Long testUserId;
    private Long testArticleId;
    private User testUser;
    private Category testCategory;

    private void setCurrentUser(Long userId) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }


    @BeforeEach
    void setUp()
    {
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

        testArticle = Article.builder()
                .title("测试文章")
                .content("这是测试文章的内容")
                .summary("这是测试文章的摘要")
                .coverImage("https://example.com/cover.jpg")
                .status(Article.ArticleStatus.DRAFT)
                .likeCount(0)
                .favoriteCount(0)
                .author(testUser)
                .build();
        testArticle.addCategory(testCategory);
        entityManager.persist(testArticle);
        testArticleId = testArticle.getId();

        articleCreateRequest = ArticleCreateRequest.builder()
                .title("测试文章")
                .content("这是测试文章的内容")
                .summary("这是测试文章的摘要")
                .coverImage("https://example.com/cover.jpg")
                .categoryIds(Set.of(testCategory.getId()))
                .status(Article.ArticleStatus.DRAFT)
                .tags(List.of("测试", "文章"))
                .build();
        articleUpdateRequest = ArticleUpdateRequest.builder()
                .title("更新后的标题")
                .content("这是更新后的内容")
                .summary("这是更新后的摘要")
                .coverImage("https://example.com/cover.jpg")
                .categoryIds(Set.of(testCategory.getId()))
                .status(Article.ArticleStatus.DRAFT)
                .tags(List.of("更新", "文章"))
                .build();
        articleDetailDTO = ArticleDetailDTO.builder()
                .title("测试文章")
                .content("这是测试文章的内容")
                .summary("这是测试文章的摘要")
                .coverImage("https://example.com/cover.jpg")
                .author(UserProfileDTO.builder()
                        .id(testUserId)
                        .username("testuser")
                        .displayName("测试用户")
                        .avatar("https://example.com/avatar.jpg")
                        .bio("这是测试用户的简介")
                        .createdAt(LocalDateTime.now())
                        .articleCount(0L)
                        .followerCount(0L)
                        .build()
                )
                .categories(List.of(CategoryDTO.builder()
                        .id(testCategory.getId())
                        .name("测试分类")
                        .description("这是测试分类的描述")
                        .build())
                )
                .tags(List.of("测试", "评论"))
                .status(Article.ArticleStatus.DRAFT)
                .likeCount(0)
                .favoriteCount(0)
                .commentCount(0)
                .isLiked(false)
                .isFavorite(false)
                .build();
        articleListItemDTO = ArticleListItemDTO.builder()
                .id(testArticleId)
                .title("测试文章")
                .summary("这是测试文章的摘要")
                .coverImage("https://example.com/cover.jpg")
                .author(UserProfileDTO.builder()
                        .id(testUserId)
                        .username("testuser")
                        .displayName("测试用户")
                        .avatar("https://example.com/avatar.jpg")
                        .bio("这是测试用户的简介")
                        .createdAt(LocalDateTime.now())
                        .articleCount(0L)
                        .followerCount(0L)
                        .build()
                )
                .categories(List.of(CategoryDTO.builder()
                        .id(testCategory.getId())
                        .name("测试分类")
                        .description("这是测试分类的描述")
                        .build())
                )
                .createdAt(LocalDateTime.now())
                .likeCount(0)
                .commentCount(0)
                .favoriteCount(0)
                .build();

        setCurrentUser(testUserId);
    }

    @AfterEach
    void tearDown()
    {
        entityManager.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @Order(1)
    @DisplayName("测试创建文章 - 成功")
    void testCreateArticle_Success() {
        ArticleDetailDTO result = articleService.createArticle(articleCreateRequest);

        assertNotNull(result);
        assertEquals("测试文章", result.getTitle());
        assertEquals("这是测试文章的内容", result.getContent());
        assertEquals(Article.ArticleStatus.DRAFT, result.getStatus());
        assertThat(result.getCategories()).hasSize(1);
        assertEquals("测试分类", result.getCategories().get(0).getName());
    }

    @Test
    @Order(2)
    @DisplayName("测试更新文章 - 成功")
    void testUpdateArticle_Success() {
        Long articleId = testArticleId;

        ArticleDetailDTO result = articleService.updateArticle(articleId, articleUpdateRequest);

        assertNotNull(result);
        assertEquals("更新后的标题", result.getTitle());
        assertEquals("这是更新后的内容", result.getContent());
        assertEquals("这是更新后的摘要", result.getSummary());
    }

    @Test
    @Order(3)
    @DisplayName("测试更新文章 - 文章不存在")
    void testUpdateArticle_NotFound() {
        Long nonExistentId = 999L;

        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> {
            articleService.updateArticle(nonExistentId, articleUpdateRequest);
        });
    }

    @Test
    @Order(4)
    @DisplayName("测试更新文章 - 无权限")
    void testUpdateArticle_NoPermission() {
        Long anotherUserId = 999L;
        Long articleId = testArticleId;

        setCurrentUser(anotherUserId);

        assertThrows(SecurityException.class, () -> {
            articleService.updateArticle(articleId, articleUpdateRequest);
        });
    }

    @Test
    @Order(5)
    @DisplayName("测试发布文章 - 成功")
    void testPublishArticle_Success() {
        Long articleId = testArticleId;

        ArticleDetailDTO result = articleService.publishArticle(articleId);

        assertNotNull(result);
        assertEquals(Article.ArticleStatus.RELEASE, result.getStatus());
    }

    @Test
    @Order(6)
    @DisplayName("测试发布文章 - 文章不存在")
    void testPublishArticle_NotFound() {
        Long nonExistentId = 999L;

        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> {
            articleService.publishArticle(nonExistentId);
        });
    }

    @Test
    @Order(7)
    @DisplayName("测试归档文章 - 成功")
    void testArchiveArticle_Success() {
        Long articleId = testArticleId;

        ArticleDetailDTO result = articleService.archiveArticle(articleId);

        assertNotNull(result);
        assertEquals(Article.ArticleStatus.ARCHIVE, result.getStatus());
    }

    @Test
    @Order(8)
    @DisplayName("测试归档文章 - 无权限")
    void testArchiveArticle_NoPermission() {
        Long articleId = testArticleId;
        Long anotherUserId = 999L;

        setCurrentUser(anotherUserId);

        assertThrows(SecurityException.class, () -> {
            articleService.archiveArticle(articleId);
        });
    }

    @Test
    @Order(9)
    @DisplayName("测试根据 ID 获取文章详情")
    void testGetArticleById() {
        Long articleId = testArticleId;

        ArticleDetailDTO result = articleService.getArticleById(articleId);

        assertNotNull(result);
        assertEquals("测试文章", result.getTitle());
        assertEquals(testUserId, result.getAuthor().getId());
    }

    @Test
    @Order(10)
    @DisplayName("测试获取文章列表")
    void testGetArticleList() {
        for (int i = 1; i <= 5; i++) {
            Article article = Article.builder()
                    .title("测试文章" + i)
                    .content("这是测试文章的内容" + i)
                    .author(testUser)
                    .status(Article.ArticleStatus.RELEASE)
                    .build();
            entityManager.persist(article);
        }
        entityManager.flush();

        Pageable pageable = PageRequest.of(0, 3);
        var result = articleService.getArticleList(pageable);

        assertNotNull(result);
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getTotalElements()).isEqualTo(6);
        assertThat(result.getTotalPages()).isEqualTo(2);
    }

    @Test
    @Order(11)
    @DisplayName("测试根据作者获取文章列表")
    void testGetArticlesByAuthor() {
        for (int i = 1; i <= 3; i++) {
            Article article = Article.builder()
                    .title("作者文章" + i)
                    .content("内容" + i)
                    .author(testUser)
                    .status(Article.ArticleStatus.RELEASE)
                    .build();
            entityManager.persist(article);
        }
        entityManager.flush();

        Pageable pageable = PageRequest.of(0, 10);
        var result = articleService.getArticlesByAuthor(testUser, pageable);

        assertNotNull(result);
        assertThat(result.getContent()).hasSize(4);
    }

    @Test
    @Order(12)
    @DisplayName("测试搜索文章")
    void testSearchArticles() {
        Article springArticle = Article.builder()
                .title("Spring Boot 教程")
                .content("内容")
                .author(testUser)
                .build();
        entityManager.persist(springArticle);

        Article javaArticle = Article.builder()
                .title("Java 并发编程")
                .content("内容")
                .author(testUser)
                .build();
        entityManager.persist(javaArticle);
        entityManager.flush();

        Pageable pageable = PageRequest.of(0, 10);
        var result = articleService.searchArticles("Spring", pageable);

        assertNotNull(result);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).contains("Spring");
    }

    @Test
    @Order(13)
    @DisplayName("测试删除文章 - 成功")
    void testDeleteArticle_Success() {
        Long articleId = testArticleId;

        articleService.deleteArticle(articleId);

        Optional<Article> deleted = articleRepository.findById(articleId);
        assertThat(deleted).isEmpty();
    }

    @Test
    @Order(14)
    @DisplayName("测试删除文章 - 文章不存在")
    void testDeleteArticle_NotFound() {
        Long nonExistentId = 999L;

        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> {
            articleService.deleteArticle(nonExistentId);
        });
    }

    @Test
    @Order(15)
    @DisplayName("测试删除文章 - 无权限")
    void testDeleteArticle_NoPermission() {
        Long articleId = testArticleId;
        Long anotherUserId = 999L;

        setCurrentUser(anotherUserId);

        assertThrows(SecurityException.class, () -> {
            articleService.deleteArticle(articleId);
        });
    }

    @Test
    @Order(16)
    @DisplayName("测试创建文章 - 分类不存在")
    void testCreateArticle_CategoryNotFound() {
        ArticleCreateRequest invalidRequest = ArticleCreateRequest.builder()
                .title("测试文章")
                .content("这是测试文章的内容")
                .categoryIds(Set.of(999L))
                .build();

        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> {
            articleService.createArticle(invalidRequest);
        });
    }

    @Test
    @Order(17)
    @DisplayName("测试创建多篇文章并验证分页")
    void testCreateMultipleArticlesWithPagination() {
        for (int i = 0; i < 10; i++) {
            ArticleCreateRequest request = ArticleCreateRequest.builder()
                    .title("批量文章" + i)
                    .content("这是批量创建的文章内容，长度足够 20 个字符以上")
                    .categoryIds(Set.of(testCategory.getId()))
                    .status(Article.ArticleStatus.RELEASE)
                    .build();
            articleService.createArticle(request);
        }
        entityManager.flush();

        Pageable pageable = PageRequest.of(1, 5);
        var result = articleService.getArticleList(pageable);

        assertThat(result.getContent()).hasSize(5);
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(5);
    }

    @Test
    @Order(18)
    @DisplayName("测试文章状态流转")
    void testArticleStatusTransition() {
        ArticleCreateRequest draftRequest = ArticleCreateRequest.builder()
                .title("状态流转测试")
                .content("这是用于测试状态流转的文章内容")
                .categoryIds(Set.of(testCategory.getId()))
                .status(Article.ArticleStatus.DRAFT)
                .build();
        ArticleDetailDTO draftArticle = articleService.createArticle(draftRequest);
        Long articleId = draftArticle.getId();

        ArticleDetailDTO releasedArticle = articleService.publishArticle(articleId);
        assertThat(releasedArticle.getStatus()).isEqualTo(Article.ArticleStatus.RELEASE);

        ArticleDetailDTO archivedArticle = articleService.archiveArticle(articleId);
        assertThat(archivedArticle.getStatus()).isEqualTo(Article.ArticleStatus.ARCHIVE);
    }

    @Test
    @Order(19)
    @DisplayName("测试更新文章的部分字段")
    void testUpdateArticlePartialFields() {
        ArticleUpdateRequest partialUpdate = ArticleUpdateRequest.builder()
                .title("只更新标题")
                .build();
        Long articleId = testArticleId;

        ArticleDetailDTO result = articleService.updateArticle(articleId, partialUpdate);

        assertEquals("只更新标题", result.getTitle());
        assertEquals("这是测试文章的内容", result.getContent());
    }

    @Test
    @Order(20)
    @DisplayName("测试空文章列表")
    void testEmptyArticleList() {
        articleRepository.deleteAll();
        entityManager.flush();

        Pageable pageable = PageRequest.of(0, 10);
        var result = articleService.getArticleList(pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    @Test
    @Order(21)
    @DisplayName("测试批量发布文章 - 成功")
    void testBatchPublishArticles_Success() {
        // Given - 创建多篇草稿文章
        List<Long> draftIds = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Article article = Article.builder()
                    .title("草稿文章" + i)
                    .content("这是草稿文章的内容，长度足够二十个字符以上")
                    .author(testUser)
                    .status(Article.ArticleStatus.DRAFT)
                    .build();
            entityManager.persist(article);
            draftIds.add(article.getId());
        }
        entityManager.flush();

        // When - 批量发布
        int updatedCount = articleService.batchPublishArticles(draftIds);

        // Then - 验证结果
        assertThat(updatedCount).isEqualTo(3);

        entityManager.clear();
        for (Long id : draftIds) {
            Article published = entityManager.find(Article.class, id);
            assertThat(published.getStatus()).isEqualTo(Article.ArticleStatus.RELEASE);
        }
    }

    @Test
    @Order(22)
    @DisplayName("测试批量归档文章 - 成功")
    void testBatchArchiveArticles_Success() {
        // Given - 创建多篇已发布文章
        List<Long> publishedIds = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Article article = Article.builder()
                    .title("已发布文章" + i)
                    .content("这是已发布文章的内容，长度足够二十个字符以上")
                    .author(testUser)
                    .status(Article.ArticleStatus.RELEASE)
                    .build();
            entityManager.persist(article);
            publishedIds.add(article.getId());
        }
        entityManager.flush();

        // When - 批量归档
        int updatedCount = articleService.batchArchiveArticles(publishedIds);

        // Then - 验证结果
        assertThat(updatedCount).isEqualTo(3);

        entityManager.clear();
        for (Long id : publishedIds) {
            Article archived = entityManager.find(Article.class, id);
            assertThat(archived.getStatus()).isEqualTo(Article.ArticleStatus.ARCHIVE);
        }
    }

    @Test
    @Order(23)
    @DisplayName("测试批量删除文章 - 成功")
    void testBatchDeleteArticles_Success() {
        // Given - 创建多篇文章
        List<Long> deleteIds = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Article article = Article.builder()
                    .title("待删除文章" + i)
                    .content("这是待删除文章的内容，长度足够")
                    .author(testUser)
                    .status(Article.ArticleStatus.DRAFT)
                    .build();
            entityManager.persist(article);
            deleteIds.add(article.getId());
        }
        entityManager.flush();

        // When - 批量删除
        int deletedCount = articleService.batchDeleteArticles(deleteIds);

        // Then - 验证结果
        assertThat(deletedCount).isEqualTo(3);

        entityManager.clear();
        for (Long id : deleteIds) {
            Article deleted = entityManager.find(Article.class, id);
            assertThat(deleted).isNull();
        }
    }

    @Test
    @Order(24)
    @DisplayName("测试批量发布文章 - 空列表")
    void testBatchPublishArticles_EmptyList() {
        // When - 传入空列表
        int updatedCount = articleService.batchPublishArticles(java.util.List.of());

        // Then - 返回 0
        assertThat(updatedCount).isEqualTo(0);
    }

    @Test
    @Order(25)
    @DisplayName("测试批量删除文章 - 空列表")
    void testBatchDeleteArticles_EmptyList() {
        // When - 传入空列表
        int deletedCount = articleService.batchDeleteArticles(java.util.List.of());

        // Then - 返回 0
        assertThat(deletedCount).isEqualTo(0);
    }

    @Test
    @Order(26)
    @DisplayName("测试文章点赞 - 增加点赞数")
    void testLikeArticle() {
        // Given - 创建一篇文章
        Article article = Article.builder()
                .title("点赞测试文章")
                .content("这是用于测试点赞的文章内容，长度足够")
                .author(testUser)
                .likeCount(5)
                .status(Article.ArticleStatus.RELEASE)
                .build();
        entityManager.persist(article);
        entityManager.flush();
        Long articleId = article.getId();

        // When - 执行点赞
        articleService.likeArticle(articleId);

        // Then - 验证点赞数 +1
        entityManager.clear();
        Article liked = entityManager.find(Article.class, articleId);
        assertThat(liked.getLikeCount()).isEqualTo(6);
    }

    @Test
    @Order(27)
    @DisplayName("测试文章浏览 - 增加浏览数")
    void testViewArticle() {
        // Given - 创建一篇文章
        Article article = Article.builder()
                .title("浏览测试文章")
                .content("这是用于测试浏览的文章内容，长度足够")
                .author(testUser)
                .viewCount(10)
                .status(Article.ArticleStatus.RELEASE)
                .build();
        entityManager.persist(article);
        entityManager.flush();
        Long articleId = article.getId();

        // When - 执行浏览
        articleService.viewArticle(articleId);

        // Then - 验证浏览数 +1
        entityManager.clear();
        Article viewed = entityManager.find(Article.class, articleId);
        assertThat(viewed.getViewCount()).isEqualTo(11);
    }

}
