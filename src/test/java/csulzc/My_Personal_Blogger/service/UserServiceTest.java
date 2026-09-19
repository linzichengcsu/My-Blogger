package csulzc.My_Personal_Blogger.service;

import csulzc.My_Personal_Blogger.config.JwtProperties;
import csulzc.My_Personal_Blogger.security.JwtAuthenticationFilter;
import csulzc.My_Personal_Blogger.api.dto.user.*;
import csulzc.My_Personal_Blogger.domain.entity.*;
import csulzc.My_Personal_Blogger.repository.*;
import csulzc.My_Personal_Blogger.config.SecurityConfig;
import csulzc.My_Personal_Blogger.security.JwtTokenProvider;
import csulzc.My_Personal_Blogger.security.PasswordValidator;
import csulzc.My_Personal_Blogger.security.SecurityContextUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import({UserService.class, SecurityConfig.class, JwtTokenProvider.class, JwtProperties.class, PasswordValidator.class, SecurityContextUtil.class})
@DisplayName("UserService 测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordValidator passwordValidator;

    @Autowired
    private SecurityContextUtil securityContextUtil;

    private UserRegisterRequest registerRequest;
    private UserLoginRequest loginRequest;
    private UserUpdateRequest updateRequest;

    private User testUser;
    private Long testUserId;

    private static final String VALID_PASSWORD = "Test@123456";
    private static final String ANOTHER_VALID_PASSWORD = "NewPass@789";


    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .username("testuser")
                .email("test@example.com")
                .passwordHash(passwordEncoder.encode(VALID_PASSWORD))
                .displayName("测试用户")
                .bio("这是测试用户的简介")
                .avatar("https://example.com/avatar.jpg")
                .status(User.UserStatus.ACTIVE)
                .role(User.UserRole.USER)
                .build();
        entityManager.persist(testUser);
        testUserId = testUser.getId();

        registerRequest = UserRegisterRequest.builder()
                .username("newuser")
                .email("newuser@example.com")
                .password(VALID_PASSWORD)
                .displayName("新用户")
                .build();

        loginRequest = UserLoginRequest.builder()
                .username("testuser")
                .password(VALID_PASSWORD)
                .build();

        updateRequest = UserUpdateRequest.builder()
                .displayName("更新后的显示名称")
                .bio("更新后的个人简介")
                .avatar("https://example.com/new-avatar.jpg")
                .build();

        entityManager.flush();
        entityManager.clear();

        setupSecurityContext();
    }

    @AfterEach
    void tearDown() {
        entityManager.clear();
        SecurityContextHolder.clearContext();
    }

    private void setupSecurityContext() {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(testUserId, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void setupAdminSecurityContext() {
        var authorities = List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(testUserId, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void setCurrentUser(Long userId) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    @Order(1)
    @DisplayName("测试用户注册 - 成功")
    void testRegister_Success() {
        // Given - 准备数据（已在 setUp 中准备）

        // When - 执行注册操作
        UserDetailDTO result = userService.register(registerRequest, User.UserRole.USER);

        // Then - 验证结果
        assertNotNull(result);
        assertEquals("newuser", result.getUsername());
        assertEquals("newuser@example.com", result.getEmail());
        assertEquals("新用户", result.getDisplayName());
        assertEquals(User.UserStatus.ACTIVE, result.getStatus());
        assertNotNull(result.getId());
    }

    @Test
    @Order(2)
    @DisplayName("测试用户注册 - 用户名已存在")
    void testRegister_UsernameExists() {
        UserRegisterRequest duplicateRequest = UserRegisterRequest.builder()
                .username("testuser")
                .email("different@example.com")
                .password(VALID_PASSWORD)
                .build();

        assertThrows(IllegalArgumentException.class, () -> {
            userService.register(duplicateRequest, User.UserRole.USER);
        });
    }

    @Test
    @Order(3)
    @DisplayName("测试用户注册 - 邮箱已被注册")
    void testRegister_EmailExists() {
        UserRegisterRequest duplicateRequest = UserRegisterRequest.builder()
                .username("differentuser")
                .email("test@example.com")
                .password(VALID_PASSWORD)
                .build();

        assertThrows(IllegalArgumentException.class, () -> {
            userService.register(duplicateRequest, User.UserRole.USER);
        });
    }

    @Test
    @Order(4)
    @DisplayName("测试用户注册 - 密码过于简单")
    void testRegister_WeakPassword() {
        UserRegisterRequest weakPasswordRequest = UserRegisterRequest.builder()
                .username("weakuser")
                .email("weak@example.com")
                .password("123456")
                .displayName("弱密码用户")
                .build();

        assertThrows(IllegalArgumentException.class, () -> {
            userService.register(weakPasswordRequest, User.UserRole.USER);
        });
    }

    @Test
    @Order(5)
    @DisplayName("测试用户注册 - 缺少大写字母")
    void testRegister_NoUpperCase() {
        UserRegisterRequest request = UserRegisterRequest.builder()
                .username("nouppercase")
                .email("nouppercase@example.com")
                .password("password123!")
                .build();

        assertThrows(IllegalArgumentException.class, () -> {
            userService.register(request, User.UserRole.USER);
        });
    }

    @Test
    @Order(6)
    @DisplayName("测试用户注册 - 缺少特殊字符")
    void testRegister_NoSpecialChar() {
        UserRegisterRequest request = UserRegisterRequest.builder()
                .username("nospecial")
                .email("nospecial@example.com")
                .password("Password123")
                .build();

        assertThrows(IllegalArgumentException.class, () -> {
            userService.register(request, User.UserRole.USER);
        });
    }


    @Test
    @Order(7)
    @DisplayName("测试用户登录(新方法-带Token) - 成功")
    void testLoginWithToken_Success() {
        // Given - 准备数据（已在 setUp 中准备）

        // When - 执行登录操作
        LoginResponseDTO result = userService.loginWithToken(loginRequest);

        // Then - 验证结果
        assertNotNull(result);
        assertNotNull(result.getAccessToken());
        assertNotNull(result.getRefreshToken());
        assertEquals("Bearer", result.getTokenType());
        assertNotNull(result.getExpiresIn());
        assertNotNull(result.getUser());
        assertEquals("testuser", result.getUser().getUsername());
        assertEquals("test@example.com", result.getUser().getEmail());
    }

    @Test
    @Order(8)
    @DisplayName("测试用户登录(新方法-带Token) - 密码错误")
    void testLoginWithToken_WrongPassword() {
        // Given - 使用错误的密码
        UserLoginRequest wrongPasswordRequest = UserLoginRequest.builder()
                .username("testuser")
                .password("wrongpassword")
                .build();

        // When & Then - 应该抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            userService.loginWithToken(wrongPasswordRequest);
        });
    }

    @Test
    @Order(9)
    @DisplayName("测试刷新Token - 成功")
    void testRefreshToken_Success() {
        // Given - 先登录获取刷新令牌
        LoginResponseDTO loginResult = userService.loginWithToken(loginRequest);
        String refreshToken = loginResult.getRefreshToken();

        // When - 刷新Token
        LoginResponseDTO refreshResult = userService.refreshToken(refreshToken);

        // Then - 验证结果
        assertNotNull(refreshResult);
        assertNotNull(refreshResult.getAccessToken());
        assertNotNull(refreshResult.getRefreshToken());
        assertEquals("testuser", refreshResult.getUser().getUsername());
    }

    @Test
    @Order(10)
    @DisplayName("测试刷新Token - 无效令牌")
    void testRefreshToken_InvalidToken() {
        // Given - 使用无效的刷新令牌
        String invalidToken = "invalid.token.here";

        // When & Then - 应该抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            userService.refreshToken(invalidToken);
        });
    }

    @Test
    @Order(11)
    @DisplayName("测试根据 ID 获取用户详情")
    void testGetUserDetail_ByUserId() {
        // Given
        Long userId = testUserId;

        // When - 获取用户详情
        UserDetailDTO result = userService.getUserDetail(userId);

        // Then - 验证结果
        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
        assertEquals("test@example.com", result.getEmail());
        assertEquals("测试用户", result.getDisplayName());
    }

    @Test
    @Order(12)
    @DisplayName("测试根据 ID 获取用户详情 - 用户不存在")
    void testGetUserDetail_NotFound() {
        // Given
        Long nonExistentId = 999L;

        // When & Then - 应该抛出异常
        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> {
            userService.getUserDetail(nonExistentId);
        });
    }

    @Test
    @Order(13)
    @DisplayName("测试根据用户名获取用户详情")
    void testGetUserDetail_ByUsername() {
        // Given
        String username = "testuser";

        // When - 获取用户详情
        UserDetailDTO result = userService.getUserDetailByUsername(username);

        // Then - 验证结果
        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
        assertEquals("测试用户", result.getDisplayName());
    }

    @Test
    @Order(14)
    @DisplayName("测试根据用户名获取用户详情 - 用户不存在")
    void testGetUserDetailByUsername_NotFound() {
        // Given
        String nonExistentUsername = "nonexistent";

        // When & Then - 应该抛出异常
        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> {
            userService.getUserDetailByUsername(nonExistentUsername);
        });
    }

    @Test
    @Order(15)
    @DisplayName("测试获取用户公开资料")
    void testGetUserProfile() {
        // Given
        Long userId = testUserId;

        // When - 获取用户资料
        UserProfileDTO result = userService.getUserProfile(userId);

        // Then - 验证结果
        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
        assertEquals("测试用户", result.getDisplayName());
        assertEquals("这是测试用户的简介", result.getBio());
    }

    @Test
    @Order(16)
    @DisplayName("测试通过用户名获取用户公开资料")
    void testGetUserProfileByUsername() {
        // Given
        String username = "testuser";

        // When - 获取用户资料
        UserProfileDTO result = userService.getUserProfileByUsername(username);

        // Then - 验证结果
        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
        assertEquals("测试用户", result.getDisplayName());
    }

    @Test
    @Order(17)
    @DisplayName("测试更新用户信息 - 成功")
    void testUpdateUser_Success() {
        // Given
        Long userId = testUserId;

        // When - 执行更新操作
        UserDetailDTO result = userService.updateUser(userId, updateRequest);

        // Then - 验证结果
        assertNotNull(result);
        assertEquals("更新后的显示名称", result.getDisplayName());
        assertEquals("更新后的个人简介", result.getBio());
        assertEquals("https://example.com/new-avatar.jpg", result.getAvatar());
    }

    @Test
    @Order(18)
    @DisplayName("测试更新用户信息 - 用户不存在")
    void testUpdateUser_NotFound() {
        // Given
        Long nonExistentId = 999L;

        // When & Then - 应该抛出异常
        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> {
            userService.updateUser(nonExistentId, updateRequest);
        });
    }

    @Test
    @Order(19)
    @DisplayName("测试更新用户信息 - 部分字段更新")
    void testUpdateUser_PartialFields() {
        // Given - 只更新显示名称
        UserUpdateRequest partialUpdate = UserUpdateRequest.builder()
                .displayName("仅更新名称")
                .build();
        Long userId = testUserId;

        // When
        UserDetailDTO result = userService.updateUser(userId, partialUpdate);

        // Then - 验证只有显示名称被更新
        assertEquals("仅更新名称", result.getDisplayName());
        assertEquals("这是测试用户的简介", result.getBio());  // 简介未变
    }

    @Test
    @Order(20)
    @DisplayName("测试修改密码 - 成功")
    void testChangePassword_Success() {
        Long userId = testUserId;
        String oldPassword = VALID_PASSWORD;
        String newPassword = ANOTHER_VALID_PASSWORD;

        userService.changePassword(userId, oldPassword, newPassword);

        UserLoginRequest newLoginRequest = UserLoginRequest.builder()
                .username("testuser")
                .password(newPassword)
                .build();
        LoginResponseDTO result = userService.loginWithToken(newLoginRequest);
        assertNotNull(result);
    }

    @Test
    @Order(21)
    @DisplayName("测试修改密码 - 原密码错误")
    void testChangePassword_WrongOldPassword() {
        Long userId = testUserId;
        String wrongOldPassword = "Wrong@123456";
        String newPassword = ANOTHER_VALID_PASSWORD;

        assertThrows(IllegalArgumentException.class, () -> {
            userService.changePassword(userId, wrongOldPassword, newPassword);
        });
    }

    @Test
    @Order(22)
    @DisplayName("测试修改密码 - 新密码不符合策略")
    void testChangePassword_WeakNewPassword() {
        Long userId = testUserId;
        String oldPassword = VALID_PASSWORD;
        String weakNewPassword = "weak";

        assertThrows(IllegalArgumentException.class, () -> {
            userService.changePassword(userId, oldPassword, weakNewPassword);
        });
    }

    @Test
    @Order(23)
    @DisplayName("测试修改密码 - 新旧密码相同")
    void testChangePassword_SamePassword() {
        Long userId = testUserId;
        String samePassword = VALID_PASSWORD;

        assertThrows(IllegalArgumentException.class, () -> {
            userService.changePassword(userId, samePassword, samePassword);
        });
    }

    @Test
    @Order(24)
    @DisplayName("测试重置密码（管理员功能）")
    void testResetPassword() {
        // Given
        Long userId = testUserId;
        String newPassword = "YuanZiqi222+++";

        // When - 执行重置密码操作
        userService.resetPassword(userId, newPassword);

        // Then - 验证可以使用新密码登录
        UserLoginRequest newLoginRequest = UserLoginRequest.builder()
                .username("testuser")
                .password(newPassword)
                .build();
        LoginResponseDTO result = userService.loginWithToken(newLoginRequest);
        assertNotNull(result);
    }

    @Test
    @Order(25)
    @DisplayName("测试重置密码 - 密码不符合策略")
    void testResetPassword_WeakPassword() {
        Long userId = testUserId;
        String weakPassword = "123";

        assertThrows(IllegalArgumentException.class, () -> {
            userService.resetPassword(userId, weakPassword);
        });
    }

    @Test
    @Order(26)
    @DisplayName("测试获取用户活动统计")
    void testGetUserActivity() {
        // Given - 为用户创建文章和评论
        Article article = Article.builder()
                .title("测试文章")
                .content("这是测试文章的内容，长度足够")
                .author(testUser)
                .likeCount(5)
                .build();
        entityManager.persist(article);

        Comment comment = Comment.builder()
                .content("这是测试评论")
                .commenter(testUser)
                .article(article)
                .build();
        entityManager.persist(comment);
        entityManager.flush();

        // When - 获取用户活动统计
        UserActivityDTO result = userService.getUserActivity(testUserId);

        // Then - 验证结果
        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
        assertThat(result.getArticleCount()).isEqualTo(1);
        assertThat(result.getCommentCount()).isEqualTo(1);
        assertThat(result.getLikeReceived()).isEqualTo(5);
    }

    @Test
    @Order(27)
    @DisplayName("测试统计用户文章数")
    void testCountUserArticles() {
        // Given - 为用户创建文章
        for (int i = 1; i <= 3; i++) {
            Article article = Article.builder()
                    .title("测试文章" + i)
                    .content("这是测试文章的内容，长度足够")
                    .author(testUser)
                    .build();
            entityManager.persist(article);
        }
        entityManager.flush();

        // When - 统计文章数
        long count = userService.countUserArticles(testUserId);

        // Then - 验证结果
        assertThat(count).isEqualTo(3);
    }

    @Test
    @Order(28)
    @DisplayName("测试统计用户评论数")
    void testCountUserComments() {
        // Given - 为用户创建评论
        Article article = Article.builder()
                .title("测试文章")
                .content("这是测试文章的内容，长度足够")
                .author(testUser)
                .build();
        entityManager.persist(article);

        for (int i = 1; i <= 5; i++) {
            Comment comment = Comment.builder()
                    .content("这是测试评论" + i)
                    .commenter(testUser)
                    .article(article)
                    .build();
            entityManager.persist(comment);
        }
        entityManager.flush();

        // When - 统计评论数
        long count = userService.countUserComments(testUserId);

        // Then - 验证结果
        assertThat(count).isEqualTo(5);
    }

    @Test
    @Order(29)
    @DisplayName("测试启用用户")
    void testActivateUser() {
        setupAdminSecurityContext();
        // Given - 创建一个禁用的用户
        User inactiveUser = User.builder()
                .username("inactiveuser")
                .email("inactive@example.com")
                .passwordHash(passwordEncoder.encode("YuanZiqi222+++"))
                .status(User.UserStatus.INACTIVE)
                .build();
        entityManager.persist(inactiveUser);
        entityManager.flush();
        Long userId = inactiveUser.getId();

        // When - 启用用户
        userService.activateUser(userId);

        // Then - 验证用户状态已变更
        User updatedUser = entityManager.find(User.class, userId);
        assertThat(updatedUser.getStatus()).isEqualTo(User.UserStatus.ACTIVE);
    }

    @Test
    @Order(30)
    @DisplayName("测试禁用用户")
    void testDeactivateUser() {
        setupAdminSecurityContext();
        // Given
        Long userId = testUserId;

        // When - 禁用用户
        userService.deactivateUser(userId);

        // Then - 验证用户状态已变更
        User updatedUser = entityManager.find(User.class, userId);
        assertThat(updatedUser.getStatus()).isEqualTo(User.UserStatus.INACTIVE);
    }

    @Test
    @Order(31)
    @DisplayName("测试锁定用户")
    void testLockUser() {
        setupAdminSecurityContext();
        // Given
        Long userId = testUserId;

        // When - 锁定用户
        userService.lockUser(userId);

        // Then - 验证用户状态已变更
        User updatedUser = entityManager.find(User.class, userId);
        assertThat(updatedUser.getStatus()).isEqualTo(User.UserStatus.LOCKED);
    }

    @Test
    @Order(32)
    @DisplayName("测试解锁用户")
    void testUnlockUser() {
        setupAdminSecurityContext();
        // Given - 创建一个锁定的用户
        User lockedUser = User.builder()
                .username("lockeduser")
                .email("locked@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .status(User.UserStatus.LOCKED)
                .build();
        entityManager.persist(lockedUser);
        entityManager.flush();
        Long userId = lockedUser.getId();

        // When - 解锁用户
        userService.unlockUser(userId);

        // Then - 验证用户状态已变更
        User updatedUser = entityManager.find(User.class, userId);
        assertThat(updatedUser.getStatus()).isEqualTo(User.UserStatus.ACTIVE);
    }

    @Test
    @Order(33)
    @DisplayName("测试删除用户 - 软删除")
    void testDeleteUser_SoftDelete() {
        setupAdminSecurityContext();
        // Given
        Long userId = testUserId;

        // When - 软删除用户
        userService.deleteUser(userId, true);

        // Then - 验证用户被禁用但未被删除
        User updatedUser = entityManager.find(User.class, userId);
        assertThat(updatedUser).isNotNull();
        assertThat(updatedUser.getStatus()).isEqualTo(User.UserStatus.INACTIVE);
    }

    @Test
    @Order(34)
    @DisplayName("测试分页查询所有用户")
    void testGetAllUsers() {
        // Given - 创建更多用户
        for (int i = 1; i <= 5; i++) {
            User user = User.builder()
                    .username("user" + i)
                    .email("user" + i + "@example.com")
                    .passwordHash(passwordEncoder.encode("password123"))
                    .displayName("用户" + i)
                    .status(User.UserStatus.ACTIVE)
                    .build();
            entityManager.persist(user);
        }
        entityManager.flush();

        // When - 分页查询
        var result = userService.getAllUsers(0, 3, "createdAt");

        // Then - 验证结果
        assertNotNull(result);
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getTotalElements()).isEqualTo(6); // setUp 中的 1 个 + 新加的 5 个
        assertThat(result.getTotalPages()).isEqualTo(2);
    }

    @Test
    @Order(35)
    @DisplayName("测试根据状态查询用户")
    void testGetUsersByStatus() {
        // Given - 创建不同状态的用户
        User inactiveUser = User.builder()
                .username("inactiveuser")
                .email("inactive@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .status(User.UserStatus.INACTIVE)
                .build();
        entityManager.persist(inactiveUser);

        User lockedUser = User.builder()
                .username("lockeduser")
                .email("locked@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .status(User.UserStatus.LOCKED)
                .build();
        entityManager.persist(lockedUser);
        entityManager.flush();

        // When - 查询活跃用户
        List<UserDetailDTO> activeUsers = userService.getUsersByStatus(User.UserStatus.ACTIVE);

        // Then - 验证结果
        assertThat(activeUsers).hasSize(1); // 只有 setUp 中的 testUser
        assertThat(activeUsers.get(0).getUsername()).isEqualTo("testuser");
    }

    @Test
    @Order(36)
    @DisplayName("测试搜索用户")
    void testSearchUsers() {
        // Given - 创建多个用户
        User user1 = User.builder()
                .username("john_doe")
                .email("john@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .displayName("John Doe")
                .status(User.UserStatus.ACTIVE)
                .build();
        entityManager.persist(user1);

        User user2 = User.builder()
                .username("jane_smith")
                .email("jane@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .displayName("Jane Smith")
                .status(User.UserStatus.ACTIVE)
                .build();
        entityManager.persist(user2);
        entityManager.flush();

        // When - 搜索包含"John"的用户
        var result = userService.searchUsers("John", 0, 10);

        // Then - 验证结果
        assertNotNull(result);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getDisplayName()).contains("John");
    }

    @Test
    @Order(37)
    @DisplayName("测试统计活跃用户数")
    void testCountActiveUsers() {
        // When - 统计活跃用户数
        long count = userService.countActiveUsers();

        // Then - 验证结果
        assertThat(count).isEqualTo(1); // 只有 setUp 中的 testUser
    }

    @Test
    @Order(38)
    @DisplayName("测试统计禁用用户数")
    void testCountInactiveUsers() {
        // Given - 创建禁用用户
        User inactiveUser = User.builder()
                .username("inactiveuser")
                .email("inactive@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .status(User.UserStatus.INACTIVE)
                .build();
        entityManager.persist(inactiveUser);
        entityManager.flush();

        // When - 统计禁用用户数
        long count = userService.countInactiveUsers();

        // Then - 验证结果
        assertThat(count).isEqualTo(1);
    }

    @Test
    @Order(39)
    @DisplayName("测试统计锁定用户数")
    void testCountLockedUsers() {
        // Given - 创建锁定用户
        User lockedUser = User.builder()
                .username("lockeduser")
                .email("locked@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .status(User.UserStatus.LOCKED)
                .build();
        entityManager.persist(lockedUser);
        entityManager.flush();

        // When - 统计锁定用户数
        long count = userService.countLockedUsers();

        // Then - 验证结果
        assertThat(count).isEqualTo(1);
    }

    @Test
    @Order(40)
    @DisplayName("测试获取总用户数")
    void testGetTotalUserCount() {
        // Given - 创建更多用户
        for (int i = 1; i <= 4; i++) {
            User user = User.builder()
                    .username("user" + i)
                    .email("user" + i + "@example.com")
                    .passwordHash(passwordEncoder.encode("password123"))
                    .status(User.UserStatus.ACTIVE)
                    .build();
            entityManager.persist(user);
        }
        entityManager.flush();

        // When - 获取总用户数
        long count = userService.getTotalUserCount();

        // Then - 验证结果
        assertThat(count).isEqualTo(5); // setUp 中的 1 个 + 新加的 4 个
    }

    @Test
    @Order(41)
    @DisplayName("测试获取最近登录的用户列表")
    void testGetRecentlyActiveUsers() {
        // Given - 创建用户并设置最后登录时间
        User user1 = User.builder()
                .username("user1")
                .email("user1@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .lastLoginAt(LocalDateTime.now().minusDays(1))
                .status(User.UserStatus.ACTIVE)
                .build();
        entityManager.persist(user1);

        User user2 = User.builder()
                .username("user2")
                .email("user2@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .lastLoginAt(LocalDateTime.now())
                .status(User.UserStatus.ACTIVE)
                .build();
        entityManager.persist(user2);
        entityManager.flush();

        // When - 获取最近活跃的 2 个用户
        List<UserActivityDTO> result = userService.getRecentlyActiveUsers(2);

        // Then - 验证结果
        assertNotNull(result);
        assertThat(result).hasSize(2);
        // 验证按最后登录时间降序排列
        assertThat(result.get(0).getLastActiveAt()).isAfterOrEqualTo(result.get(1).getLastActiveAt());
    }

    @Test
    @Order(42)
    @DisplayName("测试检查用户名是否存在")
    void testExistsByUsername() {
        // When & Then
        assertTrue(userService.existsByUsername("testuser"));
        assertFalse(userService.existsByUsername("nonexistent"));
    }

    @Test
    @Order(43)
    @DisplayName("测试检查邮箱是否已注册")
    void testExistsByEmail() {
        // When & Then
        assertTrue(userService.existsByEmail("test@example.com"));
        assertFalse(userService.existsByEmail("nonexistent@example.com"));
    }

    @Test
    @Order(44)
    @DisplayName("测试删除用户 - 用户不存在")
    void testDeleteUser_NotFound() {
        setupAdminSecurityContext();
        // Given
        Long nonExistentId = 999L;

        // When & Then - 应该抛出异常
        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> {
            userService.deleteUser(nonExistentId, false);
        });
    }

    @Test
    @Order(45)
    @DisplayName("测试用户注册 - 显式指定ADMIN权限成功")
    void testRegister_WithAdminRole_Success() {
        UserRegisterRequest adminRequest = UserRegisterRequest.builder()
                .username("adminuser")
                .email("adminuser@example.com")
                .password(VALID_PASSWORD)
                .displayName("管理员用户")
                .role(User.UserRole.ADMIN)
                .build();

        UserDetailDTO result = userService.register(adminRequest, User.UserRole.ADMIN);

        assertNotNull(result);
        assertEquals(User.UserRole.ADMIN, result.getRole());

        User saved = userRepository.findByUsername("adminuser").orElse(null);
        assertNotNull(saved);
        assertEquals(User.UserRole.ADMIN, saved.getRole());
    }

    // ... existing code ...
    @Test
    @Order(46)
    @DisplayName("测试用户注册 - 禁止创建SUPER_ADMIN")
    void testRegister_SuperAdminRejected() {
        UserRegisterRequest superAdminRequest = UserRegisterRequest.builder()
                .username("superadminuser")
                .email("superadmin@example.com")
                .password(VALID_PASSWORD)
                .role(User.UserRole.SUPER_ADMIN)
                .build();

        assertThrows(IllegalArgumentException.class, () -> {
            userService.register(superAdminRequest, User.UserRole.SUPER_ADMIN);
        });
    }

    @Test
    @Order(47)
    @DisplayName("测试根据 ID 获取用户详情 - 无权限（非本人）")
    void testGetUserDetail_NoPermission() {
        // Given - 当前登录用户不是目标用户
        setCurrentUser(999L);

        // When & Then - 应该抛出安全异常
        assertThrows(SecurityException.class, () -> {
            userService.getUserDetail(testUserId);
        });
    }

    @Test
    @Order(48)
    @DisplayName("测试更新用户信息 - 无权限（非本人）")
    void testUpdateUser_NoPermission() {
        // Given - 当前登录用户不是目标用户
        setCurrentUser(999L);

        // When & Then - 应该抛出安全异常
        assertThrows(SecurityException.class, () -> {
            userService.updateUser(testUserId, updateRequest);
        });
    }

    @Test
    @Order(49)
    @DisplayName("测试根据 ID 获取用户详情 - 未登录")
    void testGetUserDetail_NotLoggedIn() {
        // Given - 清空安全上下文模拟未登录
        SecurityContextHolder.clearContext();

        // When & Then - 应该抛出安全异常
        assertThrows(SecurityException.class, () -> {
            userService.getUserDetail(testUserId);
        });
    }

    @Test
    @Order(50)
    @DisplayName("测试根据 ID 获取用户详情 - 管理员可查看其他用户")
    void testGetUserDetail_AdminCanViewOther() {
        // Given - 创建其他用户并以管理员身份登录
        User otherUser = User.builder()
                .username("otheruser")
                .email("other@example.com")
                .passwordHash(passwordEncoder.encode(VALID_PASSWORD))
                .displayName("其他用户")
                .status(User.UserStatus.ACTIVE)
                .role(User.UserRole.USER)
                .build();
        entityManager.persist(otherUser);
        entityManager.flush();
        setupAdminSecurityContext();

        // When
        UserDetailDTO result = userService.getUserDetail(otherUser.getId());

        // Then
        assertNotNull(result);
        assertEquals("otheruser", result.getUsername());
    }

    @Test
    @Order(51)
    @DisplayName("测试更新用户信息 - 管理员可修改其他用户")
    void testUpdateUser_AdminCanUpdateOther() {
        // Given - 创建其他用户并以管理员身份登录
        User otherUser = User.builder()
                .username("otheruser")
                .email("other@example.com")
                .passwordHash(passwordEncoder.encode(VALID_PASSWORD))
                .displayName("其他用户")
                .status(User.UserStatus.ACTIVE)
                .role(User.UserRole.USER)
                .build();
        entityManager.persist(otherUser);
        entityManager.flush();
        setupAdminSecurityContext();

        // When
        UserDetailDTO result = userService.updateUser(otherUser.getId(), updateRequest);

        // Then
        assertNotNull(result);
        assertEquals("更新后的显示名称", result.getDisplayName());
    }
}
