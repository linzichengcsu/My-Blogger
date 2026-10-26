package csulzc.My_Personal_Blogger.service.business;

import csulzc.My_Personal_Blogger.api.dto.user.LoginResponseDTO;
import csulzc.My_Personal_Blogger.api.dto.user.UserLoginRequest;
import csulzc.My_Personal_Blogger.config.JwtProperties;
import csulzc.My_Personal_Blogger.domain.entity.User;
import csulzc.My_Personal_Blogger.repository.ArticleRepository;
import csulzc.My_Personal_Blogger.repository.CommentRepository;
import csulzc.My_Personal_Blogger.repository.UserRepository;
import csulzc.My_Personal_Blogger.security.JwtTokenProvider;
import csulzc.My_Personal_Blogger.security.PasswordValidator;
import csulzc.My_Personal_Blogger.security.SecurityContextUtil;
import csulzc.My_Personal_Blogger.service.infrastructure.EmailService;
import csulzc.My_Personal_Blogger.service.infrastructure.JwtBlacklistService;
import csulzc.My_Personal_Blogger.service.infrastructure.LoginAttemptService;
import csulzc.My_Personal_Blogger.service.infrastructure.PasswordResetCodeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UserService 登出与令牌续期失效测试（纯 Mockito）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 登出/续期失效测试")
class UserServiceLogoutTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ArticleRepository articleRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private SecurityContextUtil securityContextUtil;
    @Mock
    private PasswordValidator passwordValidator;
    @Mock
    private JwtBlacklistService jwtBlacklistService;
    @Mock
    private LoginAttemptService loginAttemptService;
    @Mock
    private PasswordResetCodeService passwordResetCodeService;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("登出：access 与 refresh token 均以正 TTL 拉黑")
    void logout_blacklistsBothTokens() {
        Date future = new Date(System.currentTimeMillis() + 3600_000L);
        when(jwtTokenProvider.validateToken("access-token")).thenReturn(true);
        when(jwtTokenProvider.validateToken("refresh-token")).thenReturn(true);
        when(jwtTokenProvider.getJtiFromToken("access-token")).thenReturn("jti-access");
        when(jwtTokenProvider.getJtiFromToken("refresh-token")).thenReturn("jti-refresh");
        when(jwtTokenProvider.getExpirationFromToken("access-token")).thenReturn(future);
        when(jwtTokenProvider.getExpirationFromToken("refresh-token")).thenReturn(future);

        userService.logout("access-token", "refresh-token");

        verify(jwtBlacklistService).blacklistToken(eq("jti-access"), longThat(ttl -> ttl > 0));
        verify(jwtBlacklistService).blacklistToken(eq("jti-refresh"), longThat(ttl -> ttl > 0));
    }

    @Test
    @DisplayName("登出：token 为空或已失效时静默跳过（幂等）")
    void logout_skipsInvalidTokens() {
        when(jwtTokenProvider.validateToken("bad-token")).thenReturn(false);

        userService.logout(null, "bad-token");

        verify(jwtBlacklistService, never()).blacklistToken(any(), anyLong());
    }

    @Test
    @DisplayName("续期：refresh token 在黑名单中时拒绝签发新 token")
    void refreshToken_rejectsBlacklistedToken() {
        when(jwtTokenProvider.validateToken("refresh-token")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken("refresh-token")).thenReturn(1L);
        when(jwtTokenProvider.getJtiFromToken("refresh-token")).thenReturn("jti-refresh");
        when(jwtBlacklistService.isBlacklisted("jti-refresh")).thenReturn(true);

        assertThatThrownBy(() -> userService.refreshToken("refresh-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已失效");

        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("续期：用户被封禁前签发的 refresh token 被拒绝")
    void refreshToken_rejectsTokenIssuedBeforeBan() {
        when(jwtTokenProvider.validateToken("refresh-token")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken("refresh-token")).thenReturn(1L);
        when(jwtTokenProvider.getJtiFromToken("refresh-token")).thenReturn("jti-refresh");
        when(jwtBlacklistService.isBlacklisted("jti-refresh")).thenReturn(false);
        Date issuedAt = new Date(System.currentTimeMillis() - 60_000L);
        when(jwtTokenProvider.getIssuedAtFromToken("refresh-token")).thenReturn(issuedAt);
        when(jwtBlacklistService.isIssuedBeforeBan(1L, issuedAt)).thenReturn(true);

        assertThatThrownBy(() -> userService.refreshToken("refresh-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已失效");

        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("登出：「7天免登录」token 不拉黑，等自然过期")
    void logout_skipsRememberMeToken() {
        when(jwtTokenProvider.validateToken("rm-token")).thenReturn(true);
        when(jwtTokenProvider.isRememberMeToken("rm-token")).thenReturn(true);

        userService.logout("rm-token", null);

        verify(jwtBlacklistService, never()).blacklistToken(any(), anyLong());
    }

    @Test
    @DisplayName("登录：勾选7天免登录时签发 168h 长效 token")
    void loginWithToken_rememberMeIssuesLongLivedToken() {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPasswordHash("hash");
        user.setRole(User.UserRole.USER);
        user.setStatus(User.UserStatus.ACTIVE);

        JwtProperties props = new JwtProperties();

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hash")).thenReturn(true);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenProvider.getJwtProperties()).thenReturn(props);
        when(jwtTokenProvider.generateAccessToken(eq(1L), eq("testuser"),
                eq(User.UserRole.USER), eq(props.getRememberMeExpiration()), eq(true))).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(eq(1L), eq("testuser"),
                eq(User.UserRole.USER), eq(props.getRefreshExpiration()))).thenReturn("refresh-token");

        UserLoginRequest request = UserLoginRequest.builder()
                .username("testuser").password("password").rememberMe(true).build();
        LoginResponseDTO response = userService.loginWithToken(request, null);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getExpiresIn()).isEqualTo(props.getRememberMeExpiration());
    }

    @Test
    @DisplayName("登录：未勾选时签发默认有效期 token 且不带免登录标记")
    void loginWithToken_defaultIssuesNormalToken() {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPasswordHash("hash");
        user.setRole(User.UserRole.USER);
        user.setStatus(User.UserStatus.ACTIVE);

        JwtProperties props = new JwtProperties();

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hash")).thenReturn(true);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenProvider.getJwtProperties()).thenReturn(props);
        when(jwtTokenProvider.generateAccessToken(eq(1L), eq("testuser"),
                eq(User.UserRole.USER), eq(props.getExpiration()), eq(false))).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(eq(1L), eq("testuser"),
                eq(User.UserRole.USER), eq(props.getRefreshExpiration()))).thenReturn("refresh-token");

        UserLoginRequest request = UserLoginRequest.builder()
                .username("testuser").password("password").build();
        LoginResponseDTO response = userService.loginWithToken(request, null);

        assertThat(response.getExpiresIn()).isEqualTo(props.getExpiration());
    }
}
