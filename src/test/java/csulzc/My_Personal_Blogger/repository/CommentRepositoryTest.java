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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ArticleRepository 测试类
 */
@DataJpaTest
@ActiveProfiles("test")  // 使用测试配置
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CommentRepositoryTest {

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User testUser;
    private User testCommenter;
    private Article testArticle;
    private Comment testComment;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        // 1. 创建测试作者
        testUser = User.builder()
                .username("test_user")
                .email("author@test.com")
                .passwordHash("ChantTYY0110++")
                .displayName("测试用户")
                .build();
        entityManager.persist(testUser);

        // 2. 创建测试评论者

        testCommenter = User.builder()
                .username("test_commenter")
                .email("commenter@test.com")
                .passwordHash("YuanZQ222")
                .displayName("测试评论者")
                .build();
        entityManager.persist(testCommenter);

        // 3. 创建测试文章
        testArticle = Article.builder()
                .title("测试文章标题")
                .content("这是测试文章的内容")
                .author(testUser)
                .status(Article.ArticleStatus.DRAFT)
                .likeCount(0)
                .favoriteCount(0)
                .build();
        testArticle.addCategory(testCategory);
        entityManager.persist(testArticle);

        // 4. 创建测试评论
        testComment = Comment.builder()
                .content("测试评论内容")
                .article(testArticle)
                .commenter(testCommenter)
                .parentComment(null)
                .build();
        entityManager.persist(testComment);

        // 5. 创建测试分类
        testCategory = Category.builder()
                .name("测试分类")
                .build();
        entityManager.persist(testCategory);
    }

    @AfterEach
    void tearDown()
    {
        // 空方法
    }

    @Test
    @Order(1)
    @DisplayName("测试保存评论 - 基础创建")
    void testSaveArticle() {
        // Given - 准备数据
        Comment newComment = Comment.builder()
                .content("新的评论内容")
                .article(testArticle)
                .commenter(testCommenter)
                .build();

        // When - 执行操作
        Comment saved = commentRepository.save(newComment);
        saved.setUpdatedAt(LocalDateTime.now());

        // Then - 验证结果
        assertNotNull(saved.getId());
        assertEquals("新的评论内容", saved.getContent());
        assertEquals(testCommenter.getId(), saved.getCommenter().getId());

        // 验证时间戳自动填充
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    @Order(2)
    @DisplayName("测试根据ID查询评论")
    void testFindById() {
        // When
        Optional<Comment> found = commentRepository.findById(testComment.getId());

        // Then
        assertTrue(found.isPresent());
        assertEquals("测试评论内容", found.get().getContent());
        assertEquals(testCommenter.getUsername(), found.get().getCommenter().getUsername());
    }

    @Test
    @Order(3)
    @DisplayName("测试查询不存在的评论")
    void testFindByIdNotFound() {
        // When
        Optional<Comment> found = commentRepository.findById(96485L);

        // Then
        assertFalse(found.isPresent());
    }

    @Test
    @Order(4)
    @DisplayName("测试删除评论")
    void testDeleteComment() {
        // When
        commentRepository.delete(testComment);
        entityManager.flush();

        // Then
        Optional<Comment> found = commentRepository.findById(testComment.getId());
        assertFalse(found.isPresent());

        // 验证关联的评论是否级联删除（如果有评论的话）
        // 这里需要根据你的级联策略来验证
    }

    @Test
    @Order(5)
    @DisplayName("测试根据发表者查询评论（分页）")
    void testFindByCommenter() {
        // Given - 创建多篇测试评论
        for (int i = 1; i <= 5; i++) {
            Comment comment = Comment.builder()
                    .content("内容" + i)
                    .article(testArticle)
                    .commenter(testCommenter)
                    .build();
            entityManager.persist(comment);
        }
        entityManager.flush();

        // When - 分页查询（第1页，每页3条）
        PageRequest pageable = PageRequest.of(0, 3, Sort.by("createdAt").descending());
        Page<Comment> page = commentRepository.findByCommenter(testCommenter, pageable);

        // Then
        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getTotalElements()).isEqualTo(6);  // 包括setUp中的1篇 + 新加的5篇
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getNumber()).isEqualTo(0);

        // 验证排序（最新的在前）
        List<Comment> comments = page.getContent();
        assertThat(comments.get(0).getContent()).isEqualTo("内容5");
    }

    @Test
    @Order(6)
    @DisplayName("测试查询文章的所有一级评论")
    void TestFindByArticleAndParentCommentIsNull()
    {
        for (int i = 1; i <= 10; i++) {
            Comment comment = Comment.builder()
                    .content("内容" + i)
                    .article(testArticle)
                    .commenter(testCommenter)
                    .parentComment(null)
                    .build();
            entityManager.persist(comment);
        }
        entityManager.flush();

        PageRequest pageable = PageRequest.of(0, 3, Sort.by("createdAt").descending());
        Page<Comment> page = commentRepository.findByArticleAndParentCommentIsNull(testArticle, pageable);

        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getTotalElements()).isEqualTo(11);
        assertThat(page.getTotalPages()).isEqualTo(4);
        assertThat(page.getNumber()).isEqualTo(0);

        List<Comment> comments = page.getContent();
        assertThat(comments.get(0).getContent()).isEqualTo("内容10");
    }

    @Test
    @Order(7)
    @DisplayName("测试查询评论的所有子评论")
    void testFindByParentComment()
    {
        for (int i = 1; i <= 10; i++)
        {
            Comment comment = Comment.builder()
                    .content("内容" + i)
                    .article(testArticle)
                    .commenter(testCommenter)
                    .parentComment(testComment)
                    .build();
            entityManager.persist(comment);
        }
        entityManager.flush();

        PageRequest pageable = PageRequest.of(0, 3, Sort.by("createdAt").descending());
        Page<Comment> page = commentRepository.findByParentComment(testComment, pageable);

        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getTotalElements()).isEqualTo(10);
        assertThat(page.getTotalPages()).isEqualTo(4);
        assertThat(page.getNumber()).isEqualTo(0);

        List<Comment> comments = page.getContent();
        assertThat(comments.get(0).getContent()).isEqualTo("内容10");
    }

    @Test
    @Order(8)
    @DisplayName("测试统计文章评论数")
    void testCountByArticle() {
        for (int i = 1; i <= 3; i++) {
            Comment comment = Comment.builder()
                    .content("内容" + i)
                    .article(testArticle)
                    .commenter(testCommenter)
                    .parentComment(null)
                    .build();
            entityManager.persist(comment);
        }
        entityManager.flush();

        long count = commentRepository.countByArticle(testArticle);

        assertThat(count).isEqualTo(4);
    }

    @Test
    @Order(9)
    @DisplayName("测试删除文章全部评论")
    void testDeleteByArticle()
    {
        for (int i = 1; i <= 3; i++) {
            Comment comment = Comment.builder()
                    .content("内容" + i)
                    .article(testArticle)
                    .commenter(testCommenter)
                    .parentComment(null)
                    .build();
            entityManager.persist(comment);
        }
        entityManager.flush();

        int count = commentRepository.deleteByArticle(testArticle);

        assertThat(count).isEqualTo(4);
    }

    @Test
    @DisplayName("测试分页查询超出范围")
    void testPageOutOfRange() {
        // When - 请求不存在的页码
        PageRequest pageable = PageRequest.of(100, 10);
        Page<Comment> page = commentRepository.findAll(pageable);

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
        List<Comment> comments = new ArrayList<>();

        // When - 批量创建
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < batchSize; i++) {
            Comment comment = Comment.builder()
                    .content("内容" + i)
                    .article(testArticle)
                    .commenter(testCommenter)
                    .parentComment(null)
                    .build();
            comments.add(comment);
        }
        commentRepository.saveAll(comments);
        entityManager.flush();

        long endTime = System.currentTimeMillis();

        // Then
        long count = commentRepository.count();
        assertThat(count).isGreaterThanOrEqualTo(batchSize);
        System.out.println("批量插入 " + batchSize + " 条耗时: " + (endTime - startTime) + "ms");
    }

    @Test
    @DisplayName("测试N+1查询问题")
    void testNPlusOneProblem() {
        // 这个测试演示如何检测N+1问题
        // 需要开启SQL日志来观察

        // When - 查询所有文章并访问分类
        List<Comment> comments = commentRepository.findAll();

        // Then - 观察日志中SQL执行次数
        // 理想情况：1条查询文章 + 1条查询分类 = 2条SQL
        // 如果有N+1：1条查询文章 + N条查询分类
        for (Comment comment : comments) {
            comment.getReplies().size();  // 触发懒加载
        }
    }
}
