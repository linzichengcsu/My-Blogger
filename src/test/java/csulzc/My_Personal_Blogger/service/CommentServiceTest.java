package csulzc.My_Personal_Blogger.service;

import csulzc.My_Personal_Blogger.api.dto.comment.CommentCreateRequest;
import csulzc.My_Personal_Blogger.api.dto.comment.CommentDTO;
import csulzc.My_Personal_Blogger.domain.entity.*;
import csulzc.My_Personal_Blogger.repository.*;
import csulzc.My_Personal_Blogger.security.SecurityContextUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import({CommentService.class, SecurityContextUtil.class})
@DisplayName("CommentService 测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CommentServiceTest {

    @Autowired
    private CommentService commentService;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private SecurityContextUtil securityContextUtil;

    @Autowired
    private UserRepository userRepository;

    private User testUser1;
    private User testUser2;
    private Article testArticle;
    private Comment topLevelComment;
    private Comment replyComment;
    private Long testUserId1;
    private Long testUserId2;
    private Long testArticleId;
    private Long topLevelCommentId;
    private Long replyCommentId;

    @BeforeEach
    void setUp() {
        // 创建测试用户1
        testUser1 = User.builder()
                .username("user1")
                .email("user1@example.com")
                .passwordHash("password123")
                .displayName("测试用户1")
                .status(User.UserStatus.ACTIVE)
                .build();
        entityManager.persist(testUser1);
        testUserId1 = testUser1.getId();

        // 创建测试用户2
        testUser2 = User.builder()
                .username("user2")
                .email("user2@example.com")
                .passwordHash("password123")
                .displayName("测试用户2")
                .status(User.UserStatus.ACTIVE)
                .build();
        entityManager.persist(testUser2);
        testUserId2 = testUser2.getId();

        // 创建测试文章
        testArticle = Article.builder()
                .title("测试文章")
                .content("这是测试文章的内容")
                .summary("这是测试文章的摘要")
                .author(testUser1)
                .status(Article.ArticleStatus.RELEASE)
                .build();
        entityManager.persist(testArticle);
        testArticleId = testArticle.getId();

        // 创建顶级评论
        topLevelComment = Comment.builder()
                .content("这是一条顶级评论")
                .article(testArticle)
                .commenter(testUser1)
                .likeCount(0)
                .build();
        entityManager.persist(topLevelComment);
        topLevelCommentId = topLevelComment.getId();

        // 创建回复评论
        replyComment = Comment.builder()
                .content("这是对顶级评论的回复")
                .article(testArticle)
                .commenter(testUser2)
                .parentComment(topLevelComment)
                .likeCount(0)
                .build();
        entityManager.persist(replyComment);
        replyCommentId = replyComment.getId();

        entityManager.flush();
        entityManager.clear();

        setCurrentUser(testUserId1);
    }

    @AfterEach
    void tearDown() {
        entityManager.clear();
        SecurityContextHolder.clearContext();
    }

    private void setCurrentUser(Long userId) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    @Order(1)
    @DisplayName("测试创建评论 - 成功")
    void testCreateComment_Success() {
        // Given - 准备评论请求
        CommentCreateRequest request = CommentCreateRequest.builder()
                .content("这是一条新评论")
                .build();

        // When - 执行创建操作
        CommentDTO result = commentService.createComment(testArticleId, request);

        // Then - 验证结果
        assertNotNull(result);
        assertEquals("这是一条新评论", result.getContent());
        assertEquals(testArticleId, result.getArticleId());
        assertEquals(testUserId1, result.getCommenter().getId());
        assertNull(result.getParentCommentId());
    }

    @Test
    @Order(2)
    @DisplayName("测试创建回复评论 - 成功")
    void testCreateReplyComment_Success() {
        // Given - 准备回复请求
        setCurrentUser(testUserId2);

        CommentCreateRequest request = CommentCreateRequest.builder()
                .content("这是对评论的回复")
                .parentCommentId(topLevelCommentId)
                .build();

        // When - 执行创建操作
        CommentDTO result = commentService.createComment(testArticleId, request);

        // Then - 验证结果
        assertNotNull(result);
        assertEquals("这是对评论的回复", result.getContent());
        assertEquals(topLevelCommentId, result.getParentCommentId());
        assertEquals(testUserId2, result.getCommenter().getId());
    }

    @Test
    @Order(3)
    @DisplayName("测试创建评论 - 文章不存在")
    void testCreateComment_ArticleNotFound() {
        // Given
        Long nonExistentArticleId = 999L;
        CommentCreateRequest request = CommentCreateRequest.builder()
                .content("测试评论")
                .build();

        // When & Then - 应该抛出异常
        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> {
            commentService.createComment(nonExistentArticleId, request);
        });
    }

    @Test
    @Order(4)
    @DisplayName("测试创建评论 - 用户不存在")
    void testCreateComment_UserNotFound() {
        // Given
        Long nonExistentUserId = 999L;
        CommentCreateRequest request = CommentCreateRequest.builder()
                .content("测试评论")
                .build();

        setCurrentUser(nonExistentUserId);

        // When & Then - 应该抛出异常
        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> {
            commentService.createComment(testArticleId, request);
        });
    }

    @Test
    @Order(5)
    @DisplayName("测试创建回复评论 - 父评论不存在")
    void testCreateReplyComment_ParentNotFound() {
        // Given
        CommentCreateRequest request = CommentCreateRequest.builder()
                .content("测试回复")
                .parentCommentId(999L)
                .build();

        // When & Then - 应该抛出异常
        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> {
            commentService.createComment(testArticleId, request);
        });
    }

    @Test
    @Order(6)
    @DisplayName("测试创建回复评论 - 父评论不属于此文章")
    void testCreateReplyComment_ParentNotBelongToArticle() {
        // Given - 创建另一篇文章和评论
        Article anotherArticle = Article.builder()
                .title("另一篇文章")
                .content("内容")
                .author(testUser1)
                .build();
        entityManager.persist(anotherArticle);

        Comment anotherComment = Comment.builder()
                .content("另一篇文章的评论")
                .article(anotherArticle)
                .commenter(testUser1)
                .build();
        entityManager.persist(anotherComment);
        entityManager.flush();

        CommentCreateRequest request = CommentCreateRequest.builder()
                .content("测试回复")
                .parentCommentId(anotherComment.getId())
                .build();

        // When & Then - 应该抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            commentService.createComment(testArticleId, request);
        });
    }

    @Test
    @Order(7)
    @DisplayName("测试删除评论 - 成功")
    void testDeleteComment_Success() {
        // Given
        Long commentId = topLevelCommentId;

        // When - 执行删除操作
        commentService.deleteComment(commentId);

        // Then - 验证评论已被删除
        Optional<Comment> deleted = commentRepository.findById(commentId);
        assertThat(deleted).isEmpty();
    }

    @Test
    @Order(8)
    @DisplayName("测试删除评论 - 评论不存在")
    void testDeleteComment_NotFound() {
        // Given
        Long nonExistentId = 999L;

        // When & Then - 应该抛出异常
        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> {
            commentService.deleteComment(nonExistentId);
        });
    }

    @Test
    @Order(9)
    @DisplayName("测试删除评论 - 无权限")
    void testDeleteComment_NoPermission() {
        // Given
        Long commentId = topLevelCommentId;
        Long anotherUserId = 999L;

        setCurrentUser(anotherUserId);

        // When & Then - 应该抛出权限异常
        assertThrows(SecurityException.class, () -> {
            commentService.deleteComment(commentId);
        });
    }

    @Test
    @Order(10)
    @DisplayName("测试批量删除文章的所有评论")
    void testDeleteCommentsByArticle() {
        // Given - 创建更多评论
        Comment comment2 = Comment.builder()
                .content("评论2")
                .article(testArticle)
                .commenter(testUser1)
                .build();
        entityManager.persist(comment2);

        Comment comment3 = Comment.builder()
                .content("评论3")
                .article(testArticle)
                .commenter(testUser2)
                .build();
        entityManager.persist(comment3);
        entityManager.flush();

        // When - 删除文章的所有评论
        int deletedCount = commentService.deleteCommentsByArticle(testArticleId);

        // Then - 验证删除数量
        assertThat(deletedCount).isEqualTo(4); // setUp 中的 2 个 + 新加的 1 个
    }

    @Test
    @Order(11)
    @DisplayName("测试根据 ID 获取评论详情")
    void testGetCommentById() {
        // Given
        Long commentId = topLevelCommentId;

        // When - 获取评论详情
        CommentDTO result = commentService.getCommentById(commentId);

        // Then - 验证结果
        assertNotNull(result);
        assertEquals("这是一条顶级评论", result.getContent());
        assertEquals(testArticleId, result.getArticleId());
        assertEquals(testUserId1, result.getCommenter().getId());
    }

    @Test
    @Order(12)
    @DisplayName("测试根据 ID 获取评论 - 不存在")
    void testGetCommentById_NotFound() {
        // Given
        Long nonExistentId = 999L;

        // When & Then - 应该抛出异常
        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> {
            commentService.getCommentById(nonExistentId);
        });
    }

    @Test
    @Order(13)
    @DisplayName("测试获取文章的顶级评论列表")
    void testGetTopLevelComments() {
        // Given - 创建更多顶级评论
        Comment comment2 = Comment.builder()
                .content("顶级评论2")
                .article(testArticle)
                .commenter(testUser2)
                .build();
        entityManager.persist(comment2);

        Comment comment3 = Comment.builder()
                .content("顶级评论3")
                .article(testArticle)
                .commenter(testUser1)
                .build();
        entityManager.persist(comment3);
        entityManager.flush();

        // When - 分页查询
        var result = commentService.getTopLevelComments(testArticleId, 0, 10, "createdAt");

        // Then - 验证结果
        assertNotNull(result);
        assertThat(result.getContent()).hasSize(3); // setUp 中的 1 个 + 新加的 2 个
        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    @Test
    @Order(14)
    @DisplayName("测试获取某个评论的所有回复")
    void testGetCommentReplies() {
        // Given - 创建更多回复
        Comment reply2 = Comment.builder()
                .content("回复2")
                .article(testArticle)
                .commenter(testUser1)
                .parentComment(topLevelComment)
                .build();
        entityManager.persist(reply2);

        Comment reply3 = Comment.builder()
                .content("回复3")
                .article(testArticle)
                .commenter(testUser2)
                .parentComment(topLevelComment)
                .build();
        entityManager.persist(reply3);
        entityManager.flush();

        // When - 获取回复列表
        var result = commentService.getCommentReplies(topLevelCommentId, 0, 10, "createdAt");

        // Then - 验证结果
        assertNotNull(result);
        assertThat(result.getContent()).hasSize(3); // setUp 中的 1 个 + 新加的 2 个
        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    @Test
    @Order(15)
    @DisplayName("测试获取用户的评论列表")
    void testGetUserComments() {
        // Given - 为用户创建更多评论
        Comment comment2 = Comment.builder()
                .content("用户1的评论2")
                .article(testArticle)
                .commenter(testUser1)
                .build();
        entityManager.persist(comment2);
        entityManager.flush();

        // When - 获取用户评论
        var result = commentService.getUserComments(testUserId1, 0, 10, "createdAt");

        // Then - 验证结果
        assertNotNull(result);
        assertThat(result.getContent()).hasSize(2); // setUp 中的 1 个 + 新加的 1 个
    }

    @Test
    @Order(16)
    @DisplayName("测试统计文章的评论数")
    void testCountCommentsByArticle() {
        // Given - 创建更多评论
        Comment comment2 = Comment.builder()
                .content("评论2")
                .article(testArticle)
                .commenter(testUser1)
                .build();
        entityManager.persist(comment2);

        Comment reply = Comment.builder()
                .content("回复")
                .article(testArticle)
                .commenter(testUser2)
                .parentComment(topLevelComment)
                .build();
        entityManager.persist(reply);
        entityManager.flush();

        // When - 统计评论数
        long count = commentService.countCommentsByArticle(testArticleId);

        // Then - 验证结果（包括顶级评论和回复）
        assertThat(count).isEqualTo(4); // setUp 中的 2 个 + 新加的 2 个
    }

    @Test
    @Order(17)
    @DisplayName("测试统计用户的评论数")
    void testCountCommentsByUser() {
        // Given - 为用户创建更多评论
        Comment comment2 = Comment.builder()
                .content("用户1的评论2")
                .article(testArticle)
                .commenter(testUser1)
                .build();
        entityManager.persist(comment2);
        entityManager.flush();

        // When - 统计用户评论数
        long count = commentService.countCommentsByUser(testUserId1);

        // Then - 验证结果
        assertThat(count).isEqualTo(2); // setUp 中的 1 个 + 新加的 1 个
    }

    @Test
    @Order(18)
    @DisplayName("测试获取评论回复的分页")
    void testGetCommentRepliesWithPagination() {
        // Given - 创建 10 个回复
        for (int i = 1; i <= 10; i++) {
            Comment reply = Comment.builder()
                    .content("回复" + i)
                    .article(testArticle)
                    .commenter(i % 2 == 0 ? testUser1 : testUser2)
                    .parentComment(topLevelComment)
                    .build();
            entityManager.persist(reply);
        }
        entityManager.flush();

        // When - 分页查询第 2 页
        var result = commentService.getCommentReplies(topLevelCommentId, 1, 5, "createdAt");

        // Then - 验证结果
        assertThat(result.getContent()).hasSize(5);
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(5);
        assertThat(result.getTotalElements()).isEqualTo(11); // setUp 中的 1 个 + 新加的 10 个
    }

    @Test
    @Order(19)
    @DisplayName("测试获取空的文章评论列表")
    void testGetEmptyArticleComments() {
        // Given - 创建没有评论的新文章
        Article newArticle = Article.builder()
                .title("新文章")
                .content("内容")
                .author(testUser1)
                .build();
        entityManager.persist(newArticle);
        entityManager.flush();

        // When - 获取评论列表
        var result = commentService.getTopLevelComments(newArticle.getId(), 0, 10, "createdAt");

        // Then - 验证结果为空
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    @Test
    @Order(20)
    @DisplayName("测试获取空的评论回复列表")
    void testGetEmptyCommentReplies() {
        // Given - 创建一个没有回复的顶级评论
        Comment isolatedComment = Comment.builder()
                .content("孤立评论")
                .article(testArticle)
                .commenter(testUser1)
                .build();
        entityManager.persist(isolatedComment);
        entityManager.flush();

        // When - 获取回复列表
        var result = commentService.getCommentReplies(isolatedComment.getId(), 0, 10, "createdAt");

        // Then - 验证结果为空
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    @Test
    @Order(21)
    @DisplayName("测试批量删除评论 - 按ID列表删除")
    void testBatchDeleteComments_Success() {
        // Given - 创建多条评论
        Comment comment1 = Comment.builder()
                .content("待删除评论1")
                .article(testArticle)
                .commenter(testUser1)
                .build();
        entityManager.persist(comment1);

        Comment comment2 = Comment.builder()
                .content("待删除评论2")
                .article(testArticle)
                .commenter(testUser2)
                .build();
        entityManager.persist(comment2);
        entityManager.flush();

        List<Long> deleteIds = java.util.List.of(comment1.getId(), comment2.getId());

        // When - 批量删除
        int deletedCount = commentService.batchDeleteComments(deleteIds);

        // Then - 验证结果
        assertThat(deletedCount).isEqualTo(2);

        entityManager.clear();
        assertThat(entityManager.find(Comment.class, comment1.getId())).isNull();
        assertThat(entityManager.find(Comment.class, comment2.getId())).isNull();
    }

    @Test
    @Order(22)
    @DisplayName("测试批量删除评论 - 空列表")
    void testBatchDeleteComments_EmptyList() {
        // When - 传入空列表
        int deletedCount = commentService.batchDeleteComments(java.util.List.of());

        // Then - 返回 0
        assertThat(deletedCount).isEqualTo(0);
    }

    @Test
    @Order(23)
    @DisplayName("测试批量审核评论 - 通过")
    void testBatchApproveComments_Success() {
        // Given - 创建多条待审核评论
        Comment pending1 = Comment.builder()
                .content("待审核评论1")
                .article(testArticle)
                .commenter(testUser1)
                .isApproved(false)
                .build();
        entityManager.persist(pending1);

        Comment pending2 = Comment.builder()
                .content("待审核评论2")
                .article(testArticle)
                .commenter(testUser2)
                .isApproved(false)
                .build();
        entityManager.persist(pending2);
        entityManager.flush();

        List<Long> approveIds = java.util.List.of(pending1.getId(), pending2.getId());

        // When - 批量审核通过
        int updatedCount = commentService.batchApproveComments(approveIds, true);

        // Then - 验证结果
        assertThat(updatedCount).isEqualTo(2);

        entityManager.clear();
        Comment approved1 = entityManager.find(Comment.class, pending1.getId());
        Comment approved2 = entityManager.find(Comment.class, pending2.getId());
        assertThat(approved1.getIsApproved()).isTrue();
        assertThat(approved2.getIsApproved()).isTrue();
    }

    @Test
    @Order(24)
    @DisplayName("测试批量审核评论 - 不通过")
    void testBatchApproveComments_Disapprove() {
        // Given - 创建多条已审核评论
        Comment approved1 = Comment.builder()
                .content("已审核评论1")
                .article(testArticle)
                .commenter(testUser1)
                .isApproved(true)
                .build();
        entityManager.persist(approved1);

        Comment approved2 = Comment.builder()
                .content("已审核评论2")
                .article(testArticle)
                .commenter(testUser2)
                .isApproved(true)
                .build();
        entityManager.persist(approved2);
        entityManager.flush();

        List<Long> disapproveIds = java.util.List.of(approved1.getId(), approved2.getId());

        // When - 批量审核不通过
        int updatedCount = commentService.batchApproveComments(disapproveIds, false);

        // Then - 验证结果
        assertThat(updatedCount).isEqualTo(2);

        entityManager.clear();
        Comment disapproved1 = entityManager.find(Comment.class, approved1.getId());
        Comment disapproved2 = entityManager.find(Comment.class, approved2.getId());
        assertThat(disapproved1.getIsApproved()).isFalse();
        assertThat(disapproved2.getIsApproved()).isFalse();
    }

    @Test
    @Order(25)
    @DisplayName("测试批量审核评论 - 空列表")
    void testBatchApproveComments_EmptyList() {
        // When - 传入空列表
        int updatedCount = commentService.batchApproveComments(java.util.List.of(), true);

        // Then - 返回 0
        assertThat(updatedCount).isEqualTo(0);
    }

}
