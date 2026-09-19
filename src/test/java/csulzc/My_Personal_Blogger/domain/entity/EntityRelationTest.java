package csulzc.My_Personal_Blogger.domain.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Transactional
class EntityRelationTest {

    @Autowired
    private TestEntityManager entityManager;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = createTestUser("author", "author@test.com");
        entityManager.persist(testUser);
    }

    @Test
    void testUserArticleRelation() {
        // 使用已有的testUser
        Article article = new Article();
        article.setTitle("Test Article");
        article.setContent("Test Content");
        article.setAuthor(testUser);

        Article savedArticle = entityManager.persistAndFlush(article);
        entityManager.clear();

        Article found = entityManager.find(Article.class, savedArticle.getId());
        assertNotNull(found.getAuthor());
        assertEquals("author", found.getAuthor().getUsername());
    }

    @Test
    void testArticleCategoryRelation() {
        Article article = new Article();
        article.setTitle("Multi-category Article");
        article.setAuthor(testUser);
        entityManager.persist(article);

        Category tech = new Category();
        tech.setName("Tech");
        entityManager.persist(tech);

        Category java = new Category();
        java.setName("Java");
        java.setParentCategory(tech);  // 设置父子关系
        entityManager.persist(java);

        // 使用辅助方法保持双向关系
        article.addCategory(tech);
        article.addCategory(java);
        entityManager.persistAndFlush(article);
        entityManager.clear();

        Article found = entityManager.find(Article.class, article.getId());
        assertEquals(2, found.getCategories().size());

        // 验证分类的父子关系
        Category foundJava = entityManager.find(Category.class, java.getId());
        assertNotNull(foundJava.getParentCategory());
        assertEquals("Tech", foundJava.getParentCategory().getName());
    }

    @Test
    void testCommentSelfRelation() {
        Article article = new Article();
        article.setTitle("Test Article");
        article.setAuthor(testUser);
        entityManager.persist(article);

        User commenter = createTestUser("commenter", "commenter@test.com");
        entityManager.persist(commenter);

        Comment parentComment = Comment.builder()
                .content("Parent comment")
                .article(article)
                .commenter(commenter)
                .build();
        entityManager.persist(parentComment);

        Comment reply = Comment.builder()
                .content("Reply to parent")
                .article(article)
                .commenter(testUser)
                .parentComment(parentComment)
                .build();
        entityManager.persistAndFlush(reply);
        entityManager.clear();

        Comment foundParent = entityManager.find(Comment.class, parentComment.getId());
        assertEquals(1, foundParent.getReplies().size());

        Comment foundReply = entityManager.find(Comment.class, reply.getId());
        assertNotNull(foundReply.getParentComment());
    }

    private User createTestUser(String username, String email) {
        return User.builder()
                .username(username)
                .email(email)
                .passwordHash("password123")
                .displayName("Test User")
                .build();
    }
}