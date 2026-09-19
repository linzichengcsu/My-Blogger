package csulzc.My_Personal_Blogger.controller;

import tools.jackson.databind.ObjectMapper;
import csulzc.My_Personal_Blogger.api.dto.comment.CommentCreateRequest;
import csulzc.My_Personal_Blogger.api.dto.comment.CommentDTO;
import csulzc.My_Personal_Blogger.api.dto.comment.CommentReplyDTO;
import csulzc.My_Personal_Blogger.api.dto.comment.CommentBatchApprovalRequest;
import csulzc.My_Personal_Blogger.api.dto.common.BatchIdRequest;
import csulzc.My_Personal_Blogger.api.dto.common.PageResponseDTO;
import csulzc.My_Personal_Blogger.api.dto.user.UserProfileDTO;
import csulzc.My_Personal_Blogger.config.JwtProperties;
import csulzc.My_Personal_Blogger.repository.UserRepository;
import csulzc.My_Personal_Blogger.security.JwtTokenProvider;
import csulzc.My_Personal_Blogger.security.SecurityContextUtil;
import csulzc.My_Personal_Blogger.service.CommentService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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

@WebMvcTest(CommentController.class)
@DisplayName("CommentController 测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import({CommentControllerTest.TestSecurityConfig.class, SecurityContextUtil.class, JwtTokenProvider.class})
@EnableConfigurationProperties(JwtProperties.class)
class CommentControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        @Primary
        public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .anyRequest().permitAll()
                    );
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CommentService commentService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserRepository userRepository;

    private CommentCreateRequest createRequest;
    private CommentDTO commentDTO;
    private CommentReplyDTO replyDTO;
    private UserProfileDTO userProfileDTO;

    @BeforeEach
    void setUp() {
        userProfileDTO = UserProfileDTO.builder()
                .id(1L)
                .username("testuser")
                .displayName("测试用户")
                .bio("这是测试用户的简介")
                .avatar("https://example.com/avatar.jpg")
                .createdAt(LocalDateTime.now())
                .build();

        createRequest = CommentCreateRequest.builder()
                .content("这是一条测试评论")
                .parentCommentId(null)
                .build();

        commentDTO = CommentDTO.builder()
                .id(1L)
                .content("这是一条测试评论")
                .commenter(userProfileDTO)
                .articleId(1L)
                .parentCommentId(null)
                .replyCount(0)
                .likeCount(0)
                .isLiked(false)
                .createdAt(LocalDateTime.now())
                .build();

        replyDTO = CommentReplyDTO.builder()
                .id(2L)
                .content("这是一条回复")
                .commenter(userProfileDTO)
                .replyToUser(userProfileDTO)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @Order(1)
    @DisplayName("测试创建评论 - 成功")
    void testCreateComment_Success() throws Exception {
        given(commentService.createComment(eq(1L), any(CommentCreateRequest.class)))
                .willReturn(commentDTO);

        mockMvc.perform(post("/api/comments/article/{articleId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("commenterId", "1")
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("评论发表成功"))
                .andExpect(jsonPath("$.data.content").value("这是一条测试评论"));

        then(commentService).should().createComment(eq(1L), any(CommentCreateRequest.class));
    }

    @Test
    @Order(2)
    @DisplayName("测试创建评论 - 参数验证失败（内容为空）")
    void testCreateComment_ValidationFailed_EmptyContent() throws Exception {
        CommentCreateRequest invalidRequest = CommentCreateRequest.builder()
                .content("")
                .build();

        mockMvc.perform(post("/api/comments/article/{articleId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("commenterId", "1")
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(3)
    @DisplayName("测试创建评论 - 文章ID无效")
    void testCreateComment_InvalidArticleId() throws Exception {
        mockMvc.perform(post("/api/comments/article/{articleId}", -1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("commenterId", "1")
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(4)
    @DisplayName("测试获取评论详情 - 成功")
    void testGetCommentById_Success() throws Exception {
        Long commentId = 1L;
        given(commentService.getCommentById(eq(commentId)))
                .willReturn(commentDTO);

        mockMvc.perform(get("/api/comments/{commentId}", commentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(commentId))
                .andExpect(jsonPath("$.data.content").value("这是一条测试评论"));

        then(commentService).should().getCommentById(eq(commentId));
    }

    @Test
    @Order(5)
    @DisplayName("测试获取评论详情 - 无效ID")
    void testGetCommentById_InvalidId() throws Exception {
        mockMvc.perform(get("/api/comments/{commentId}", -1))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(6)
    @DisplayName("测试获取文章的顶级评论列表 - 成功")
    void testGetTopLevelComments_Success() throws Exception {
        PageResponseDTO<CommentDTO> pageResponse = PageResponseDTO.<CommentDTO>builder()
                .content(Collections.singletonList(commentDTO))
                .page(0)
                .size(10)
                .totalElements(1L)
                .totalPages(1)
                .build();

        given(commentService.getTopLevelComments(eq(1L), eq(0), eq(10), eq("createdAt")))
                .willReturn(pageResponse);

        mockMvc.perform(get("/api/comments/article/{articleId}", 1L)
                        .param("page", "0")
                        .param("size", "10")
                        .param("sortBy", "createdAt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10));

        then(commentService).should().getTopLevelComments(eq(1L), eq(0), eq(10), eq("createdAt"));
    }

    @Test
    @Order(7)
    @DisplayName("测试获取文章的顶级评论列表 - 页码为负数")
    void testGetTopLevelComments_NegativePage() throws Exception {
        mockMvc.perform(get("/api/comments/article/{articleId}", 1L)
                        .param("page", "-1")
                        .param("size", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(8)
    @DisplayName("测试获取文章的顶级评论列表 - 每页大小超限")
    void testGetTopLevelComments_SizeExceedsLimit() throws Exception {
        mockMvc.perform(get("/api/comments/article/{articleId}", 1L)
                        .param("page", "0")
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(9)
    @DisplayName("测试获取评论的回复列表 - 成功")
    void testGetCommentReplies_Success() throws Exception {
        PageResponseDTO<CommentReplyDTO> pageResponse = PageResponseDTO.<CommentReplyDTO>builder()
                .content(Collections.singletonList(replyDTO))
                .page(0)
                .size(10)
                .totalElements(1L)
                .totalPages(1)
                .build();

        given(commentService.getCommentReplies(eq(1L), eq(0), eq(10), eq("createdAt")))
                .willReturn(pageResponse);

        mockMvc.perform(get("/api/comments/{commentId}/replies", 1L)
                        .param("page", "0")
                        .param("size", "10")
                        .param("sortBy", "createdAt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.page").value(0));

        then(commentService).should().getCommentReplies(eq(1L), eq(0), eq(10), eq("createdAt"));
    }

    @Test
    @Order(10)
    @DisplayName("测试获取评论的回复列表 - 无效评论ID")
    void testGetCommentReplies_InvalidCommentId() throws Exception {
        mockMvc.perform(get("/api/comments/{commentId}/replies", -1)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(11)
    @DisplayName("测试获取用户的评论列表 - 成功")
    void testGetUserComments_Success() throws Exception {
        PageResponseDTO<CommentDTO> pageResponse = PageResponseDTO.<CommentDTO>builder()
                .content(Collections.singletonList(commentDTO))
                .page(0)
                .size(10)
                .totalElements(1L)
                .totalPages(1)
                .build();

        given(commentService.getUserComments(eq(1L), eq(0), eq(10), eq("createdAt")))
                .willReturn(pageResponse);

        mockMvc.perform(get("/api/comments/user/{userId}", 1L)
                        .param("page", "0")
                        .param("size", "10")
                        .param("sortBy", "createdAt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.page").value(0));

        then(commentService).should().getUserComments(eq(1L), eq(0), eq(10), eq("createdAt"));
    }

    @Test
    @Order(12)
    @DisplayName("测试获取用户的评论列表 - 无效用户ID")
    void testGetUserComments_InvalidUserId() throws Exception {
        mockMvc.perform(get("/api/comments/user/{userId}", 0)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(13)
    @DisplayName("测试删除评论 - 成功")
    void testDeleteComment_Success() throws Exception {
        Long commentId = 1L;
        Long userId = 1L;
        doNothing().when(commentService).deleteComment(eq(commentId));

        mockMvc.perform(delete("/api/comments/{commentId}", commentId)
                        .param("userId", String.valueOf(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("评论删除成功"));

        then(commentService).should().deleteComment(eq(commentId));
    }

    @Test
    @Order(14)
    @DisplayName("测试删除评论 - 无效评论ID")
    void testDeleteComment_InvalidCommentId() throws Exception {
        mockMvc.perform(delete("/api/comments/{commentId}", -1)
                        .param("userId", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(15)
    @DisplayName("测试统计文章评论数 - 成功")
    void testCountCommentsByArticle_Success() throws Exception {
        Long articleId = 1L;
        long count = 10L;
        given(commentService.countCommentsByArticle(eq(articleId))).willReturn(count);

        mockMvc.perform(get("/api/comments/article/{articleId}/count", articleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(10));

        then(commentService).should().countCommentsByArticle(eq(articleId));
    }

    @Test
    @Order(16)
    @DisplayName("测试统计文章评论数 - 无效文章ID")
    void testCountCommentsByArticle_InvalidArticleId() throws Exception {
        mockMvc.perform(get("/api/comments/article/{articleId}/count", -1))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(17)
    @DisplayName("测试统计用户评论数 - 成功")
    void testCountCommentsByUser_Success() throws Exception {
        Long userId = 1L;
        long count = 5L;
        given(commentService.countCommentsByUser(eq(userId))).willReturn(count);

        mockMvc.perform(get("/api/comments/user/{userId}/count", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(5));

        then(commentService).should().countCommentsByUser(eq(userId));
    }

    @Test
    @Order(18)
    @DisplayName("测试统计用户评论数 - 无效用户ID")
    void testCountCommentsByUser_InvalidUserId() throws Exception {
        mockMvc.perform(get("/api/comments/user/{userId}/count", 0))
                .andExpect(status().isBadRequest());
    }

    // ==================== 批量操作测试 ====================

    @Test
    @Order(19)
    @DisplayName("测试批量删除评论 - 成功")
    void testBatchDeleteComments_Success() throws Exception {
        // Given
        BatchIdRequest request = new BatchIdRequest(java.util.List.of(1L, 2L, 3L));
        given(commentService.batchDeleteComments(anyList()))
                .willReturn(3);

        // When & Then
        mockMvc.perform(post("/api/comments/batch/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(3));

        then(commentService).should().batchDeleteComments(anyList());
    }

    @Test
    @Order(20)
    @DisplayName("测试批量删除评论 - ID列表为空")
    void testBatchDeleteComments_EmptyIds() throws Exception {
        // Given
        BatchIdRequest request = new BatchIdRequest(java.util.List.of());

        // When & Then
        mockMvc.perform(post("/api/comments/batch/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(21)
    @DisplayName("测试批量审核评论 - 通过")
    void testBatchApproveComments_Success() throws Exception {
        // Given
        CommentBatchApprovalRequest request = new CommentBatchApprovalRequest(
                java.util.List.of(1L, 2L), true);
        given(commentService.batchApproveComments(anyList(), eq(true)))
                .willReturn(2);

        // When & Then
        mockMvc.perform(post("/api/comments/batch/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(2));

        then(commentService).should().batchApproveComments(anyList(), eq(true));
    }

    @Test
    @Order(22)
    @DisplayName("测试批量审核评论 - 不通过")
    void testBatchApproveComments_Disapprove() throws Exception {
        // Given
        CommentBatchApprovalRequest request = new CommentBatchApprovalRequest(
                java.util.List.of(1L), false);
        given(commentService.batchApproveComments(anyList(), eq(false)))
                .willReturn(1);

        // When & Then
        mockMvc.perform(post("/api/comments/batch/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(1));

        then(commentService).should().batchApproveComments(anyList(), eq(false));
    }

    @Test
    @Order(23)
    @DisplayName("测试批量审核评论 - ID列表为空")
    void testBatchApproveComments_EmptyIds() throws Exception {
        // Given
        CommentBatchApprovalRequest request = new CommentBatchApprovalRequest(
                java.util.List.of(), true);

        // When & Then
        mockMvc.perform(post("/api/comments/batch/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

}
