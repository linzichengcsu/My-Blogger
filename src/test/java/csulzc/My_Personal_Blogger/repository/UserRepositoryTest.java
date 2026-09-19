package csulzc.My_Personal_Blogger.repository;

import csulzc.My_Personal_Blogger.domain.entity.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("UserRepository 测试")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User testUser;

    @BeforeEach
    void setUp() {
        // 准备测试用户
        testUser = User.builder()
                .username("testuser")
                .email("test@example.com")
                .passwordHash("password123")
                .displayName("测试用户")
                .status(User.UserStatus.ACTIVE)
                .build();
        entityManager.persist(testUser);
        entityManager.flush();
        entityManager.clear();

        // 重新获取Managed实体
        testUser = entityManager.find(User.class, testUser.getId());
    }

    @Test
    @DisplayName("根据用户名查询用户 - 成功")
    void findByUsername_Success() {
        // 修复：Pageable参数问题
        Pageable pageable = PageRequest.of(0, 10);
        Optional<User> found = userRepository.findByUsername("testuser");

        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("testuser");
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("根据用户名查询用户 - 用户不存在")
    void findByUsername_NotFound() {
        Pageable pageable = PageRequest.of(0, 10);
        Optional<User> found = userRepository.findByUsername("nonexistent");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("根据邮箱查询用户 - 成功")
    void findByEmail_Success() {
        Optional<User> found = userRepository.findByEmail("test@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
        assertThat(found.get().getUsername()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("根据邮箱查询用户 - 邮箱不存在")
    void findByEmail_NotFound() {
        Optional<User> found = userRepository.findByEmail("nonexistent@example.com");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("检查用户名是否存在 - 存在")
    void existsByUsername_True() {
        boolean exists = userRepository.existsByUsername("testuser");

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("检查用户名是否存在 - 不存在")
    void existsByUsername_False() {
        boolean exists = userRepository.existsByUsername("nonexistent");

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("根据用户名查询用户并预加载文章 - 成功")
    void findByUsernameWithArticles_Success() {
        // 准备：给用户添加一篇文章
        createTestArticleForUser(testUser);

        Optional<User> found = userRepository.findByUsernameWithArticles("testuser");

        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("testuser");
        assertThat(found.get().getArticles())
                .isNotNull()
                .hasSize(1);
        assertThat(found.get().getArticles().get(0).getTitle()).isEqualTo("测试文章");
    }

    @Test
    @DisplayName("统计不同状态的用户数量")
    void countByStatus() {
        // 准备：创建不同状态的用户
        createUserWithStatus("inactive", "inactive@test.com", User.UserStatus.INACTIVE);
        createUserWithStatus("locked", "locked@test.com", User.UserStatus.LOCKED);

        long activeCount = userRepository.countByStatus(User.UserStatus.ACTIVE);
        long inactiveCount = userRepository.countByStatus(User.UserStatus.INACTIVE);
        long lockedCount = userRepository.countByStatus(User.UserStatus.LOCKED);

        assertThat(activeCount).isEqualTo(1);  // testUser
        assertThat(inactiveCount).isEqualTo(1);
        assertThat(lockedCount).isEqualTo(1);
    }

    @Test
    @DisplayName("唯一约束测试 - 用户名不能重复")
    void usernameUniqueConstraint() {
        User duplicateUser = User.builder()
                .username("testuser")  // 和testUser相同的用户名
                .email("another@example.com")
                .passwordHash("password123")
                .build();

        assertThatThrownBy(() -> {
            entityManager.persistAndFlush(duplicateUser);
        }).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("唯一约束测试 - 邮箱不能重复")
    void emailUniqueConstraint() {
        User duplicateUser = User.builder()
                .username("another")
                .email("test@example.com")  // 和testUser相同的邮箱
                .passwordHash("password123")
                .build();

        assertThatThrownBy(() -> {
            entityManager.persistAndFlush(duplicateUser);
        }).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("非空约束测试 - 必需字段不能为null")
    void notNullConstraint() {
        User invalidUser = User.builder()
                .username(null)  // 用户名为null
                .email("invalid@test.com")
                .passwordHash("password")
                .build();

        assertThatThrownBy(() -> {
            entityManager.persistAndFlush(invalidUser);
        }).isInstanceOf(Exception.class);
    }

    // 辅助方法
    private void createUserWithStatus(String username, String email, User.UserStatus status) {
        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash("password")
                .status(status)
                .build();
        entityManager.persist(user);
        entityManager.flush();
    }

    private void createTestArticleForUser(User user) {
        Article article = Article.builder()
                .title("测试文章")
                .content("测试内容")
                .author(user)
                .build();
        entityManager.persist(article);
        entityManager.flush();
        entityManager.clear();
    }
}