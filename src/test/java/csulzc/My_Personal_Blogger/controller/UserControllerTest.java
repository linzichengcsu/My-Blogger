package csulzc.My_Personal_Blogger.controller;

import tools.jackson.databind.ObjectMapper;
import csulzc.My_Personal_Blogger.api.dto.common.PageResponseDTO;
import csulzc.My_Personal_Blogger.api.dto.user.*;
import csulzc.My_Personal_Blogger.config.JwtProperties;
import csulzc.My_Personal_Blogger.domain.entity.User;
import csulzc.My_Personal_Blogger.security.JwtTokenProvider;
import csulzc.My_Personal_Blogger.security.RequestSourceResolver;
import csulzc.My_Personal_Blogger.security.SecurityContextUtil;
import csulzc.My_Personal_Blogger.service.UserService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
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

@WebMvcTest(UserController.class)
@DisplayName("UserController 测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import({UserControllerTest.TestSecurityConfig.class, JwtTokenProvider.class, JwtProperties.class, RequestSourceResolver.class})
class UserControllerTest {

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
    private UserService userService;

    @MockitoBean
    private SecurityContextUtil securityContextUtil;

    private UserRegisterRequest registerRequest;
    private UserLoginRequest loginRequest;
    private UserUpdateRequest updateRequest;
    private UserPasswordChangeRequest passwordChangeRequest;
    private UserDetailDTO userDetailDTO;
    private UserProfileDTO userProfileDTO;
    private UserActivityDTO userActivityDTO;
    private LoginResponseDTO loginResponseDTO;

    @BeforeEach
    void setUp() {
        registerRequest = UserRegisterRequest.builder()
                .username("testuser")
                .email("test@example.com")
                .password("password123")
                .displayName("测试用户")
                .build();

        loginRequest = UserLoginRequest.builder()
                .username("testuser")
                .password("password123")
                .build();

        updateRequest = UserUpdateRequest.builder()
                .displayName("更新后的名称")
                .bio("更新后的简介")
                .avatar("https://example.com/new-avatar.jpg")
                .build();

        passwordChangeRequest = UserPasswordChangeRequest.builder()
                .oldPassword("password123")
                .newPassword("newpassword")
                .build();

        userDetailDTO = UserDetailDTO.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .displayName("测试用户")
                .bio("这是测试用户的简介")
                .avatar("https://example.com/avatar.jpg")
                .status(csulzc.My_Personal_Blogger.domain.entity.User.UserStatus.ACTIVE)
                .lastLoginAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .articleCount(5L)
                .commentCount(10L)
                .favoriteCount(3L)
                .build();

        userProfileDTO = UserProfileDTO.builder()
                .id(1L)
                .username("testuser")
                .displayName("测试用户")
                .bio("这是测试用户的简介")
                .avatar("https://example.com/avatar.jpg")
                .createdAt(LocalDateTime.now())
                .articleCount(5L)
                .commentCount(10L)
                .build();

        userActivityDTO = UserActivityDTO.builder()
                .userId(1L)
                .username("testuser")
                .articleCount(5L)
                .commentCount(10L)
                .likeReceived(20L)
                .lastActiveAt(LocalDateTime.now())
                .build();

        loginResponseDTO = LoginResponseDTO.builder()
                .accessToken("eyJhbGciOiJIUzI1NiJ9.test.access.token")
                .refreshToken("eyJhbGciOiJIUzI1NiJ9.test.refresh.token")
                .tokenType("Bearer")
                .expiresIn(86400000L)
                .user(userDetailDTO)
                .build();
    }

    @Test
    @Order(1)
    @DisplayName("测试用户注册 - 成功")
    void testRegister_Success() throws Exception {
        given(userService.register(any(UserRegisterRequest.class), eq(User.UserRole.USER)))
                .willReturn(userDetailDTO);

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("注册成功"))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"));

        then(userService).should().register(any(UserRegisterRequest.class), eq(User.UserRole.USER));
    }

    @Test
    @Order(2)
    @DisplayName("测试用户注册 - 参数验证失败（用户名过短）")
    void testRegister_ValidationFailed_ShortUsername() throws Exception {
        UserRegisterRequest invalidRequest = UserRegisterRequest.builder()
                .username("ab")
                .email("test@example.com")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(3)
    @DisplayName("测试用户注册 - 参数验证失败（邮箱格式错误）")
    void testRegister_ValidationFailed_InvalidEmail() throws Exception {
        UserRegisterRequest invalidRequest = UserRegisterRequest.builder()
                .username("testuser")
                .email("invalid-email")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(4)
    @DisplayName("测试用户登录(带Token) - 成功")
    void testLogin_Success() throws Exception {
        given(userService.loginWithToken(any(UserLoginRequest.class)))
                .willReturn(loginResponseDTO);

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("登录成功"))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.username").value("testuser"));

        then(userService).should().loginWithToken(any(UserLoginRequest.class));
    }

    @Test
    @Order(5)
    @DisplayName("测试用户登录 - 参数验证失败")
    void testLogin_ValidationFailed() throws Exception {
        UserLoginRequest invalidRequest = UserLoginRequest.builder()
                .username("")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(6)
    @DisplayName("测试刷新Token - 成功")
    void testRefreshToken_Success() throws Exception {
        String refreshToken = "eyJhbGciOiJIUzI1NiJ9.test.refresh.token";
        given(userService.refreshToken(eq(refreshToken)))
                .willReturn(loginResponseDTO);

        mockMvc.perform(post("/api/users/refresh")
                        .param("refreshToken", refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Token刷新成功"))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists());

        then(userService).should().refreshToken(eq(refreshToken));
    }

    @Test
    @Order(7)
    @DisplayName("测试获取用户详情 - 通过ID成功")
    void testGetUserDetail_ByUserId_Success() throws Exception {
        Long userId = 1L;
        given(userService.getUserDetail(eq(userId)))
                .willReturn(userDetailDTO);

        mockMvc.perform(get("/api/users/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(userId))
                .andExpect(jsonPath("$.data.username").value("testuser"));

        then(userService).should().getUserDetail(eq(userId));
    }

    @Test
    @Order(8)
    @DisplayName("测试获取用户详情 - 无效ID")
    void testGetUserDetail_InvalidUserId() throws Exception {
        mockMvc.perform(get("/api/users/{userId}", -1))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(9)
    @DisplayName("测试获取用户详情 - 通过用户名成功")
    void testGetUserDetail_ByUsername_Success() throws Exception {
        String username = "testuser";
        given(userService.getUserDetailByUsername(eq(username)))
                .willReturn(userDetailDTO);

        mockMvc.perform(get("/api/users/username/{username}", username))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("testuser"));

        then(userService).should().getUserDetailByUsername(eq(username));
    }

    @Test
    @Order(10)
    @DisplayName("测试获取用户详情 - 用户不存在")
    void testGetUserDetailByUsername_NotFound() throws Exception {
        String username = "nonexistent";
        given(userService.getUserDetailByUsername(eq(username)))
                .willThrow(new jakarta.persistence.EntityNotFoundException("用户不存在"));

        mockMvc.perform(get("/api/users/username/{username}", username))
                .andExpect(status().isNotFound());

        then(userService).should().getUserDetailByUsername(eq(username));
    }


    @Test
    @Order(11)
    @DisplayName("测试获取用户公开资料 - 成功")
    void testGetUserProfile_Success() throws Exception {
        Long userId = 1L;
        given(userService.getUserProfile(eq(userId)))
                .willReturn(userProfileDTO);

        mockMvc.perform(get("/api/users/{userId}/profile", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.displayName").value("测试用户"));

        then(userService).should().getUserProfile(eq(userId));
    }

    @Test
    @Order(12)
    @DisplayName("测试获取用户公开资料 - 无效ID")
    void testGetUserProfile_InvalidUserId() throws Exception {
        mockMvc.perform(get("/api/users/{userId}/profile", 0))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(13)
    @DisplayName("测试更新用户信息 - 成功")
    void testUpdateUser_Success() throws Exception {
        Long userId = 1L;
        UserDetailDTO updatedUser = UserDetailDTO.builder()
                .id(userId)
                .username("testuser")
                .displayName("更新后的名称")
                .bio("更新后的简介")
                .avatar("https://example.com/new-avatar.jpg")
                .status(csulzc.My_Personal_Blogger.domain.entity.User.UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        given(userService.updateUser(eq(userId), any(UserUpdateRequest.class)))
                .willReturn(updatedUser);

        mockMvc.perform(put("/api/users/{userId}", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("更新成功"))
                .andExpect(jsonPath("$.data.displayName").value("更新后的名称"));

        then(userService).should().updateUser(eq(userId), any(UserUpdateRequest.class));
    }

    @Test
    @Order(14)
    @DisplayName("测试更新用户信息 - 无效ID")
    void testUpdateUser_InvalidUserId() throws Exception {
        mockMvc.perform(put("/api/users/{userId}", -1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());
    }


    @Test
    @Order(15)
    @DisplayName("测试修改密码 - 成功")
    void testChangePassword_Success() throws Exception {
        Long userId = 1L;

        doNothing().when(userService).changePassword(eq(userId), eq(passwordChangeRequest.getOldPassword()), eq(passwordChangeRequest.getNewPassword()));

        mockMvc.perform(post("/api/users/{userId}/change-password", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(passwordChangeRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("密码修改成功"));

        then(userService).should().changePassword(eq(userId), eq(passwordChangeRequest.getOldPassword()), eq(passwordChangeRequest.getNewPassword()));
    }

    @Test
    @Order(16)
    @DisplayName("测试修改密码 - 原密码为空")
    void testChangePassword_EmptyOldPassword() throws Exception {
        Long userId = 1L;

        UserPasswordChangeRequest invalidRequest = UserPasswordChangeRequest.builder()
                .oldPassword("")
                .newPassword("newpassword456")
                .build();

        mockMvc.perform(post("/api/users/{userId}/change-password", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(17)
    @DisplayName("测试修改密码 - 新密码长度不符合要求")
    void testChangePassword_NewPasswordTooShort() throws Exception {
        Long userId = 1L;

        UserPasswordChangeRequest invalidRequest = UserPasswordChangeRequest.builder()
                .oldPassword("password123")
                .newPassword("123")
                .build();

        mockMvc.perform(post("/api/users/{userId}/change-password", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(18)
    @DisplayName("测试修改密码 - 新密码为空")
    void testChangePassword_EmptyNewPassword() throws Exception {
        Long userId = 1L;

        UserPasswordChangeRequest invalidRequest = UserPasswordChangeRequest.builder()
                .oldPassword("password123")
                .newPassword("")
                .build();

        mockMvc.perform(post("/api/users/{userId}/change-password", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }


    @Test
    @Order(19)
    @DisplayName("测试获取用户活动统计 - 成功")
    void testGetUserActivity_Success() throws Exception {
        Long userId = 1L;
        given(userService.getUserActivity(eq(userId)))
                .willReturn(userActivityDTO);

        mockMvc.perform(get("/api/users/{userId}/activity", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.articleCount").value(5))
                .andExpect(jsonPath("$.data.commentCount").value(10));

        then(userService).should().getUserActivity(eq(userId));
    }

    @Test
    @Order(20)
    @DisplayName("测试获取用户活动统计 - 无效ID")
    void testGetUserActivity_InvalidUserId() throws Exception {
        mockMvc.perform(get("/api/users/{userId}/activity", 0))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(21)
    @DisplayName("测试获取所有用户列表 - 成功")
    void testGetAllUsers_Success() throws Exception {
        PageResponseDTO<UserProfileDTO> pageResponse = PageResponseDTO.<UserProfileDTO>builder()
                .content(Collections.singletonList(userProfileDTO))
                .page(0)
                .size(10)
                .totalElements(1L)
                .totalPages(1)
                .build();

        given(userService.getAllUsers(eq(0), eq(10), eq("createdAt")))
                .willReturn(pageResponse);

        mockMvc.perform(get("/api/users")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sortBy", "createdAt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10));

        then(userService).should().getAllUsers(eq(0), eq(10), eq("createdAt"));
    }

    @Test
    @Order(22)
    @DisplayName("测试获取所有用户列表 - 页码为负数")
    void testGetAllUsers_NegativePage() throws Exception {
        mockMvc.perform(get("/api/users")
                        .param("page", "-1")
                        .param("size", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(23)
    @DisplayName("测试获取所有用户列表 - 每页大小超限")
    void testGetAllUsers_SizeExceedsLimit() throws Exception {
        mockMvc.perform(get("/api/users")
                        .param("page", "0")
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(24)
    @DisplayName("测试搜索用户 - 成功")
    void testSearchUsers_Success() throws Exception {
        String keyword = "test";
        PageResponseDTO<UserProfileDTO> pageResponse = PageResponseDTO.<UserProfileDTO>builder()
                .content(Collections.singletonList(userProfileDTO))
                .page(0)
                .size(10)
                .totalElements(1L)
                .totalPages(1)
                .build();

        given(userService.searchUsers(eq(keyword), eq(0), eq(10)))
                .willReturn(pageResponse);

        mockMvc.perform(get("/api/users/search")
                        .param("keyword", keyword)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        then(userService).should().searchUsers(eq(keyword), eq(0), eq(10));
    }

    @Test
    @Order(25)
    @DisplayName("测试搜索用户 - 关键词为空")
    void testSearchUsers_EmptyKeyword() throws Exception {
        mockMvc.perform(get("/api/users/search")
                        .param("keyword", "")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(26)
    @DisplayName("测试启用用户 - 成功")
    void testActivateUser_Success() throws Exception {
        Long userId = 1L;
        doNothing().when(userService).activateUser(eq(userId));

        mockMvc.perform(post("/api/users/{userId}/activate", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("用户已启用"));

        then(userService).should().activateUser(eq(userId));
    }

    @Test
    @Order(27)
    @DisplayName("测试启用用户 - 无效ID")
    void testActivateUser_InvalidUserId() throws Exception {
        mockMvc.perform(post("/api/users/{userId}/activate", -1))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(28)
    @DisplayName("测试禁用用户 - 成功")
    void testDeactivateUser_Success() throws Exception {
        Long userId = 1L;
        doNothing().when(userService).deactivateUser(eq(userId));

        mockMvc.perform(post("/api/users/{userId}/deactivate", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("用户已禁用"));

        then(userService).should().deactivateUser(eq(userId));
    }

    @Test
    @Order(29)
    @DisplayName("测试锁定用户 - 成功")
    void testLockUser_Success() throws Exception {
        Long userId = 1L;
        doNothing().when(userService).lockUser(eq(userId));

        mockMvc.perform(post("/api/users/{userId}/lock", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("用户已锁定"));

        then(userService).should().lockUser(eq(userId));
    }

    @Test
    @Order(30)
    @DisplayName("测试解锁用户 - 成功")
    void testUnlockUser_Success() throws Exception {
        Long userId = 1L;
        doNothing().when(userService).unlockUser(eq(userId));

        mockMvc.perform(post("/api/users/{userId}/unlock", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("用户已解锁"));

        then(userService).should().unlockUser(eq(userId));
    }

    @Test
    @Order(31)
    @DisplayName("测试删除用户 - 软删除成功")
    void testDeleteUser_SoftDelete_Success() throws Exception {
        Long userId = 1L;
        doNothing().when(userService).deleteUser(eq(userId), eq(true));

        mockMvc.perform(delete("/api/users/{userId}", userId)
                        .param("softDelete", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("用户已禁用"));

        then(userService).should().deleteUser(eq(userId), eq(true));
    }

    @Test
    @Order(32)
    @DisplayName("测试删除用户 - 硬删除成功")
    void testDeleteUser_HardDelete_Success() throws Exception {
        Long userId = 1L;
        doNothing().when(userService).deleteUser(eq(userId), eq(false));

        mockMvc.perform(delete("/api/users/{userId}", userId)
                        .param("softDelete", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("用户已删除"));

        then(userService).should().deleteUser(eq(userId), eq(false));
    }

    @Test
    @Order(33)
    @DisplayName("测试删除用户 - 无效ID")
    void testDeleteUser_InvalidUserId() throws Exception {
        mockMvc.perform(delete("/api/users/{userId}", 0))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(34)
    @DisplayName("测试获取活跃用户数 - 成功")
    void testCountActiveUsers_Success() throws Exception {
        long count = 100L;
        given(userService.countActiveUsers()).willReturn(count);

        mockMvc.perform(get("/api/users/stats/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(100));

        then(userService).should().countActiveUsers();
    }

    @Test
    @Order(35)
    @DisplayName("测试获取总用户数 - 成功")
    void testGetTotalUserCount_Success() throws Exception {
        long count = 150L;
        given(userService.getTotalUserCount()).willReturn(count);

        mockMvc.perform(get("/api/users/stats/total"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(150));

        then(userService).should().getTotalUserCount();
    }

    @Test
    @Order(36)
    @DisplayName("测试获取最近活跃用户列表 - 成功")
    void testGetRecentlyActiveUsers_Success() throws Exception {
        int limit = 10;
        List<UserActivityDTO> activeUsers = Collections.singletonList(userActivityDTO);
        given(userService.getRecentlyActiveUsers(eq(limit))).willReturn(activeUsers);

        mockMvc.perform(get("/api/users/stats/recently-active")
                        .param("limit", String.valueOf(limit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].username").value("testuser"));

        then(userService).should().getRecentlyActiveUsers(eq(limit));
    }

    @Test
    @Order(37)
    @DisplayName("测试获取最近活跃用户列表 - 限制数量超限")
    void testGetRecentlyActiveUsers_LimitExceeds() throws Exception {
        mockMvc.perform(get("/api/users/stats/recently-active")
                        .param("limit", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(38)
    @DisplayName("测试获取最近活跃用户列表 - 限制数量为负数")
    void testGetRecentlyActiveUsers_NegativeLimit() throws Exception {
        mockMvc.perform(get("/api/users/stats/recently-active")
                        .param("limit", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(39)
    @DisplayName("测试用户注册 - 前端请求强制USER权限（即使请求体指定ADMIN）")
    void testRegister_FromFrontend_ForcesUserRole() throws Exception {
        UserRegisterRequest adminRequest = UserRegisterRequest.builder()
                .username("adminrequest")
                .email("adminrequest@example.com")
                .password("password123")
                .role(User.UserRole.ADMIN)
                .build();

        mockMvc.perform(post("/api/users/register")
                        .header("Origin", "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminRequest)))
                .andExpect(status().isOk());

        then(userService).should().register(any(UserRegisterRequest.class), eq(User.UserRole.USER));
    }

    @Test
    @Order(40)
    @DisplayName("测试用户注册 - Apifox请求可指定ADMIN权限")
    void testRegister_FromApifox_AdminRole() throws Exception {
        given(userService.register(any(UserRegisterRequest.class), eq(User.UserRole.ADMIN)))
                .willReturn(userDetailDTO);

        UserRegisterRequest adminRequest = UserRegisterRequest.builder()
                .username("adminrequest")
                .email("adminrequest@example.com")
                .password("password123")
                .role(User.UserRole.ADMIN)
                .build();

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("testuser"));

        then(userService).should().register(any(UserRegisterRequest.class), eq(User.UserRole.ADMIN));
    }

    @Test
    @Order(41)
    @DisplayName("测试用户注册 - Apifox请求指定SUPER_ADMIN被拒绝")
    void testRegister_FromApifox_SuperAdminRejected() throws Exception {
        UserRegisterRequest superAdminRequest = UserRegisterRequest.builder()
                .username("superadminrequest")
                .email("superadmin@example.com")
                .password("password123")
                .role(User.UserRole.SUPER_ADMIN)
                .build();

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(superAdminRequest)))
                .andExpect(status().isBadRequest());

        then(userService).should(never()).register(any(UserRegisterRequest.class), any(User.UserRole.class));
    }

    @Test
    @Order(42)
    @DisplayName("测试获取当前登录用户信息 - 仅凭Token成功")
    void testGetMyInfo_Success() throws Exception {
        Long currentUserId = 1L;
        given(securityContextUtil.getCurrentUserId()).willReturn(currentUserId);
        given(userService.getUserDetail(eq(currentUserId))).willReturn(userDetailDTO);

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("testuser"));

        then(userService).should().getUserDetail(eq(currentUserId));
    }

    @Test
    @Order(43)
    @DisplayName("测试获取当前登录用户信息 - 未登录")
    void testGetMyInfo_NotLoggedIn() throws Exception {
        given(securityContextUtil.getCurrentUserId())
                .willThrow(new SecurityException("请先登录"));

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isForbidden());

        then(userService).should(never()).getUserDetail(anyLong());
    }
    @Test
    @Order(44)
    @DisplayName("测试修改当前登录用户信息 - 仅凭Token成功")
    void testUpdateMyInfo_Success() throws Exception {
        Long currentUserId = 1L;
        UserDetailDTO updatedUser = UserDetailDTO.builder()
                .id(currentUserId)
                .username("testuser")
                .displayName("更新后的名称")
                .bio("更新后的简介")
                .avatar("https://example.com/new-avatar.jpg")
                .status(csulzc.My_Personal_Blogger.domain.entity.User.UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        given(securityContextUtil.getCurrentUserId()).willReturn(currentUserId);
        given(userService.updateUser(eq(currentUserId), any(UserUpdateRequest.class)))
                .willReturn(updatedUser);

        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("更新成功"))
                .andExpect(jsonPath("$.data.displayName").value("更新后的名称"));

        then(userService).should().updateUser(eq(currentUserId), any(UserUpdateRequest.class));
    }

    @Test
    @Order(45)
    @DisplayName("测试修改当前登录用户信息 - 未登录")
    void testUpdateMyInfo_NotLoggedIn() throws Exception {
        given(securityContextUtil.getCurrentUserId())
                .willThrow(new SecurityException("请先登录"));

        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());

        then(userService).should(never()).updateUser(anyLong(), any(UserUpdateRequest.class));
    }

    @Test
    @Order(46)
    @DisplayName("测试修改当前登录用户密码 - 仅凭Token成功")
    void testChangeMyPassword_Success() throws Exception {
        Long currentUserId = 1L;
        given(securityContextUtil.getCurrentUserId()).willReturn(currentUserId);
        doNothing().when(userService).changePassword(
                eq(currentUserId),
                eq(passwordChangeRequest.getOldPassword()),
                eq(passwordChangeRequest.getNewPassword()));

        mockMvc.perform(post("/api/users/me/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(passwordChangeRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("密码修改成功"));

        then(userService).should().changePassword(
                eq(currentUserId),
                eq(passwordChangeRequest.getOldPassword()),
                eq(passwordChangeRequest.getNewPassword()));
    }

    @Test
    @Order(47)
    @DisplayName("测试修改当前登录用户密码 - 未登录")
    void testChangeMyPassword_NotLoggedIn() throws Exception {
        given(securityContextUtil.getCurrentUserId())
                .willThrow(new SecurityException("请先登录"));

        mockMvc.perform(post("/api/users/me/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(passwordChangeRequest)))
                .andExpect(status().isForbidden());

        then(userService).should(never()).changePassword(anyLong(), anyString(), anyString());
    }
}