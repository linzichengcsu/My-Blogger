package csulzc.My_Personal_Blogger.controller;

import tools.jackson.databind.ObjectMapper;
import csulzc.My_Personal_Blogger.api.dto.article.ArticleCreateRequest;
import csulzc.My_Personal_Blogger.api.dto.article.ArticleDetailDTO;
import csulzc.My_Personal_Blogger.api.dto.article.ArticleUpdateRequest;
import csulzc.My_Personal_Blogger.api.dto.common.PageResponseDTO;
import csulzc.My_Personal_Blogger.api.dto.common.BatchIdRequest;
import csulzc.My_Personal_Blogger.api.response.Result;
import csulzc.My_Personal_Blogger.config.JwtProperties;
import csulzc.My_Personal_Blogger.repository.UserRepository;
import csulzc.My_Personal_Blogger.security.JwtTokenProvider;
import csulzc.My_Personal_Blogger.security.SecurityContextUtil;
import csulzc.My_Personal_Blogger.service.ArticleService;
import csulzc.My_Personal_Blogger.service.UserService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ArticleController.class)
@DisplayName("ArticleController 测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import({ArticleControllerTest.TestSecurityConfig.class, JwtTokenProvider.class})
@EnableConfigurationProperties(JwtProperties.class)
class ArticleControllerTest {

// ... existing code ...

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        @Primary
        public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)  // 禁用 CSRF
                    .authorizeHttpRequests(auth -> auth
                            .anyRequest().permitAll()   // 允许所有请求
                    );
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ArticleService articleService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SecurityContextUtil securityContextUtil;

    private ArticleCreateRequest createRequest;
    private ArticleUpdateRequest updateRequest;
    private ArticleDetailDTO articleDetailDTO;

    @BeforeEach
    void setUp() {
        // 准备测试数据
        createRequest = ArticleCreateRequest.builder()
                .title("测试文章标题")
                .content("Exhaust your hand. Deal 10 Damage for each card Exhausted. Exhaust.")
                .summary("测试摘要")
                .coverImage("https://example.com/cover.jpg")
                .categoryIds(java.util.Set.of(1L))
                .tags(List.of("测试", "Java"))
                .build();

        updateRequest = ArticleUpdateRequest.builder()
                .title("更新后的标题")
                .content("Exhaust all non-Attack cards in your hand. Gain 7 Block for each card Exhausted.")
                .summary("更新后的摘要")
                .build();

        articleDetailDTO = ArticleDetailDTO.builder()
                .id(1L)
                .title("测试文章标题")
                .content("Exhaust your hand. Deal 10 Damage for each card Exhausted. Exhaust.")
                .summary("测试摘要")
                .status(csulzc.My_Personal_Blogger.domain.entity.Article.ArticleStatus.DRAFT)
                .likeCount(0)
                .favoriteCount(0)
                .commentCount(0)
                .isLiked(false)
                .isFavorite(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @Order(1)
    @DisplayName("测试创建文章 - 成功")
    void testCreateArticle_Success() throws Exception {
        // Given - 准备Mock行为
        given(articleService.createArticle(any(ArticleCreateRequest.class)))
                .willReturn(articleDetailDTO);

        // When & Then - 执行请求并验证结果
        mockMvc.perform(post("/api/articles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("authorId", "1")
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("文章创建成功"))
                .andExpect(jsonPath("$.data.title").value("测试文章标题"))
                .andExpect(jsonPath("$.data.content").value("Exhaust your hand. Deal 10 Damage for each card Exhausted. Exhaust."));

        // 验证Service方法被调用
        then(articleService).should().createArticle(any(ArticleCreateRequest.class));
    }

    @Test
    @Order(2)
    @DisplayName("测试创建文章 - 参数验证失败（标题为空）")
    void testCreateArticle_ValidationFailed_EmptyTitle() throws Exception {
        // Given - 准备无效数据
        ArticleCreateRequest invalidRequest = ArticleCreateRequest.builder()
                .title("")  // 空标题
                .content("Exhaust your hand. Deal 10 Damage for each card Exhausted. Exhaust.")
                .categoryIds(java.util.Set.of(1L))
                .build();

        // When & Then - 应该返回400错误
        mockMvc.perform(post("/api/articles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("authorId", "1")
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(3)
    @DisplayName("测试创建文章 - 内容过短")
    void testCreateArticle_ContentTooShort() throws Exception {
        // Given - 准备内容过短的数据
        ArticleCreateRequest shortContentRequest = ArticleCreateRequest.builder()
                .title("测试文章")
                .content("太短")  // 内容长度不足
                .categoryIds(java.util.Set.of(1L))
                .build();

        // When & Then - 应该返回400错误（如果Service有验证）
        given(articleService.createArticle(any(ArticleCreateRequest.class)))
                .willThrow(new IllegalArgumentException("文章内容长度不能少于20个字符"));

        mockMvc.perform(post("/api/articles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shortContentRequest)))
                .andExpect(status().isBadRequest());
    }


    @Test
    @Order(4)
    @DisplayName("测试更新文章 - 成功")
    void testUpdateArticle_Success() throws Exception {
        // Given
        Long articleId = 1L;
        Long userId = 1L;

        ArticleDetailDTO updatedArticle = ArticleDetailDTO.builder()
                .id(articleId)
                .title("更新后的标题")
                .content("Exhaust all non-Attack cards in your hand. Gain 7 Block for each card Exhausted.")
                .summary("更新后的摘要")
                .status(csulzc.My_Personal_Blogger.domain.entity.Article.ArticleStatus.DRAFT)
                .likeCount(0)
                .favoriteCount(0)
                .commentCount(0)
                .isLiked(false)
                .isFavorite(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        given(articleService.updateArticle(eq(articleId), any(ArticleUpdateRequest.class)))
                .willReturn(updatedArticle);

        // When & Then
        mockMvc.perform(put("/api/articles/{articleId}", articleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("userId", String.valueOf(userId))
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("文章更新成功"))
                .andExpect(jsonPath("$.data.title").value("更新后的标题"));

        then(articleService).should().updateArticle(eq(articleId), any(ArticleUpdateRequest.class));
    }

    @Test
    @Order(5)
    @DisplayName("测试更新文章 - 文章不存在")
    void testUpdateArticle_NotFound() throws Exception {
        // Given
        Long nonExistentId = 999L;
        given(articleService.updateArticle(eq(nonExistentId), any(ArticleUpdateRequest.class)))
                .willThrow(new jakarta.persistence.EntityNotFoundException("文章不存在"));

        // When & Then
        mockMvc.perform(put("/api/articles/{articleId}", nonExistentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("userId", "1")
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(6)
    @DisplayName("测试发布文章 - 成功")
    void testPublishArticle_Success() throws Exception {
        // Given
        Long articleId = 1L;
        Long userId = 1L;

        ArticleDetailDTO publishedArticle = ArticleDetailDTO.builder()
                .id(articleId)
                .title("测试文章")
                .status(csulzc.My_Personal_Blogger.domain.entity.Article.ArticleStatus.RELEASE)
                .likeCount(0)
                .favoriteCount(0)
                .commentCount(0)
                .isLiked(false)
                .isFavorite(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        given(articleService.publishArticle(eq(articleId)))
                .willReturn(publishedArticle);

        // When & Then
        mockMvc.perform(post("/api/articles/{articleId}/publish", articleId)
                        .param("userId", String.valueOf(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("文章发布成功"))
                .andExpect(jsonPath("$.data.status").value("RELEASE"));

        then(articleService).should().publishArticle(eq(articleId));
    }

    @Test
    @Order(7)
    @DisplayName("测试归档文章 - 成功")
    void testArchiveArticle_Success() throws Exception {
        // Given
        Long articleId = 1L;
        Long userId = 1L;

        ArticleDetailDTO archivedArticle = ArticleDetailDTO.builder()
                .id(articleId)
                .title("测试文章")
                .status(csulzc.My_Personal_Blogger.domain.entity.Article.ArticleStatus.ARCHIVE)
                .likeCount(0)
                .favoriteCount(0)
                .commentCount(0)
                .isLiked(false)
                .isFavorite(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        given(articleService.archiveArticle(eq(articleId)))
                .willReturn(archivedArticle);

        // When & Then
        mockMvc.perform(post("/api/articles/{articleId}/archive", articleId)
                        .param("userId", String.valueOf(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("文章归档成功"))
                .andExpect(jsonPath("$.data.status").value("ARCHIVE"));
    }

    @Test
    @Order(8)
    @DisplayName("测试获取文章详情 - 成功")
    void testGetArticleById_Success() throws Exception {
        // Given
        Long articleId = 1L;
        given(articleService.getArticleById(eq(articleId)))
                .willReturn(articleDetailDTO);

        // When & Then
        mockMvc.perform(get("/api/articles/{articleId}", articleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(articleId))
                .andExpect(jsonPath("$.data.title").value("测试文章标题"));

        then(articleService).should().getArticleById(eq(articleId));
    }

    @Test
    @Order(9)
    @DisplayName("测试获取文章详情 - 无效ID")
    void testGetArticleById_InvalidId() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/articles/{articleId}", -1))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(10)
    @DisplayName("测试获取文章列表 - 成功")
    void testGetArticleList_Success() throws Exception {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        List<csulzc.My_Personal_Blogger.api.dto.article.ArticleListItemDTO> articles = Collections.emptyList();
        PageResponseDTO<csulzc.My_Personal_Blogger.api.dto.article.ArticleListItemDTO> pageResponse =
                PageResponseDTO.<csulzc.My_Personal_Blogger.api.dto.article.ArticleListItemDTO>builder()
                        .content(articles)
                        .page(0)
                        .size(10)
                        .totalElements(0L)
                        .totalPages(0)
                        .build();

        given(articleService.getArticleList(any(Pageable.class)))
                .willReturn(pageResponse);

        // When & Then
        mockMvc.perform(get("/api/articles")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sortBy", "createdAt")
                        .param("sortDirection", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10));

        then(articleService).should().getArticleList(any(Pageable.class));
    }

    @Test
    @Order(11)
    @DisplayName("测试搜索文章 - 成功")
    void testSearchArticles_Success() throws Exception {
        // Given
        String keyword = "Spring";
        Pageable pageable = PageRequest.of(0, 10);
        List<csulzc.My_Personal_Blogger.api.dto.article.ArticleListItemDTO> articles = Collections.emptyList();
        PageResponseDTO<csulzc.My_Personal_Blogger.api.dto.article.ArticleListItemDTO> pageResponse =
                PageResponseDTO.<csulzc.My_Personal_Blogger.api.dto.article.ArticleListItemDTO>builder()
                        .content(articles)
                        .page(0)
                        .size(10)
                        .totalElements(0L)
                        .totalPages(0)
                        .build();

        given(articleService.searchArticles(eq(keyword), any(Pageable.class)))
                .willReturn(pageResponse);

        // When & Then
        mockMvc.perform(get("/api/articles/search")
                        .param("keyword", keyword)
                        .param("page", "0")
                        .param("size", "10")
                        .param("sortBy", "createdAt")
                        .param("sortDirection", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        then(articleService).should().searchArticles(eq(keyword), any(Pageable.class));
    }

    @Test
    @Order(12)
    @DisplayName("测试搜索文章 - 关键词为空")
    void testSearchArticles_EmptyKeyword() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/articles/search")
                        .param("keyword", "")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(13)
    @DisplayName("测试删除文章 - 成功")
    void testDeleteArticle_Success() throws Exception {
        // Given
        Long articleId = 1L;
        Long userId = 1L;
        doNothing().when(articleService).deleteArticle(eq(articleId));

        // When & Then
        mockMvc.perform(delete("/api/articles/{articleId}", articleId)
                        .param("userId", String.valueOf(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("文章删除成功"));

        then(articleService).should().deleteArticle(eq(articleId));
    }

    @Test
    @Order(14)
    @DisplayName("测试删除文章 - 无权限")
    void testDeleteArticle_NoPermission() throws Exception {
        // Given
        Long articleId = 1L;
        Long userId = 999L;
        doThrow(new RuntimeException("无权限删除此文章"))
                .when(articleService).deleteArticle(eq(articleId));

        // When & Then
        mockMvc.perform(delete("/api/articles/{articleId}", articleId)
                        .param("userId", String.valueOf(userId)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @Order(15)
    @DisplayName("测试分页参数验证 - 页码为负数")
    void testGetArticleList_NegativePage() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/articles")
                        .param("page", "-1")
                        .param("size", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(16)
    @DisplayName("测试分页参数验证 - 每页大小超限")
    void testGetArticleList_SizeExceedsLimit() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/articles")
                        .param("page", "0")
                        .param("size", "101"))  // 超过最大限制100
                .andExpect(status().isBadRequest());
    }

    // ==================== 批量操作测试 ====================

    @Test
    @Order(17)
    @DisplayName("测试批量发布文章 - 成功")
    void testBatchPublishArticles_Success() throws Exception {
        // Given
        BatchIdRequest request = new BatchIdRequest(java.util.List.of(1L, 2L, 3L));
        given(articleService.batchPublishArticles(anyList()))
                .willReturn(3);

        // When & Then
        mockMvc.perform(post("/api/articles/batch/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(3));

        then(articleService).should().batchPublishArticles(anyList());
    }

    @Test
    @Order(18)
    @DisplayName("测试批量发布文章 - ID列表为空")
    void testBatchPublishArticles_EmptyIds() throws Exception {
        // Given
        BatchIdRequest request = new BatchIdRequest(java.util.List.of());

        // When & Then
        mockMvc.perform(post("/api/articles/batch/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(19)
    @DisplayName("测试批量归档文章 - 成功")
    void testBatchArchiveArticles_Success() throws Exception {
        // Given
        BatchIdRequest request = new BatchIdRequest(java.util.List.of(1L, 2L));
        given(articleService.batchArchiveArticles(anyList()))
                .willReturn(2);

        // When & Then
        mockMvc.perform(post("/api/articles/batch/archive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(2));

        then(articleService).should().batchArchiveArticles(anyList());
    }

    @Test
    @Order(20)
    @DisplayName("测试批量删除文章 - 成功")
    void testBatchDeleteArticles_Success() throws Exception {
        // Given
        BatchIdRequest request = new BatchIdRequest(java.util.List.of(1L, 2L, 3L, 4L));
        given(articleService.batchDeleteArticles(anyList()))
                .willReturn(4);

        // When & Then
        mockMvc.perform(post("/api/articles/batch/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(4));

        then(articleService).should().batchDeleteArticles(anyList());
    }

    @Test
    @Order(21)
    @DisplayName("测试点赞文章 - 成功")
    void testLikeArticle_Success() throws Exception {
        // Given
        Long articleId = 1L;
        doNothing().when(articleService).likeArticle(eq(articleId));

        // When & Then
        mockMvc.perform(post("/api/articles/{articleId}/like", articleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("点赞成功"));

        then(articleService).should().likeArticle(eq(articleId));
    }

    @Test
    @Order(22)
    @DisplayName("测试点赞文章 - 无效ID")
    void testLikeArticle_InvalidId() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/articles/{articleId}/like", -1))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(23)
    @DisplayName("测试浏览文章 - 成功")
    void testViewArticle_Success() throws Exception {
        // Given
        Long articleId = 1L;
        doNothing().when(articleService).viewArticle(eq(articleId));

        // When & Then
        mockMvc.perform(post("/api/articles/{articleId}/view", articleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("记录浏览成功"));

        then(articleService).should().viewArticle(eq(articleId));
    }

    @Test
    @Order(24)
    @DisplayName("测试浏览文章 - 无效ID")
    void testViewArticle_InvalidId() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/articles/{articleId}/view", 0))
                .andExpect(status().isBadRequest());
    }

}
