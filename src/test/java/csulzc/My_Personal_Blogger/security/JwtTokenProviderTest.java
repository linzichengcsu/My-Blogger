package csulzc.My_Personal_Blogger.security;

import csulzc.My_Personal_Blogger.config.JwtProperties;
import csulzc.My_Personal_Blogger.domain.entity.User;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("JWT Token 测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JwtTokenProviderTest {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JwtProperties jwtProperties;

    private static String testToken;
    private static final Long TEST_USER_ID = 1L;
    private static final String TEST_USERNAME = "testuser";
    private static final User.UserRole TEST_USER_ROLE = User.UserRole.USER;
    private static final long TEST_EXPIRATION = 86400000L;

    @Test
    @Order(1)
    @DisplayName("测试生成访问令牌")
    void testGenerateAccessToken() {
        testToken = jwtTokenProvider.generateAccessToken(TEST_USER_ID, TEST_USERNAME, TEST_USER_ROLE, TEST_EXPIRATION);

        assertThat(testToken).isNotNull();
        assertThat(testToken).isNotEmpty();
        System.out.println("生成的Token: " + testToken);
    }

    @Test
    @Order(2)
    @DisplayName("测试从Token中获取用户ID")
    void testGetUserIdFromToken() {
        Long userId = jwtTokenProvider.getUserIdFromToken(testToken);
        assertThat(userId).isEqualTo(TEST_USER_ID);
    }

    @Test
    @Order(3)
    @DisplayName("测试从Token中获取用户名")
    void testGetUsernameFromToken() {
        String username = jwtTokenProvider.getUsernameFromToken(testToken);
        assertThat(username).isEqualTo(TEST_USERNAME);
    }

    @Test
    @Order(4)
    @DisplayName("测试Token验证")
    void testValidateToken() {
        boolean isValid = jwtTokenProvider.validateToken(testToken);
        assertThat(isValid).isTrue();
    }

    @Test
    @Order(5)
    @DisplayName("测试无效Token验证")
    void testValidateInvalidToken() {
        boolean isValid = jwtTokenProvider.validateToken("invalid.token.here");
        assertThat(isValid).isFalse();
    }

    @Test
    @Order(6)
    @DisplayName("测试生成刷新令牌")
    void testGenerateRefreshToken() {
        String refreshToken = jwtTokenProvider.generateRefreshToken(TEST_USER_ID, TEST_USERNAME, TEST_USER_ROLE, TEST_EXPIRATION);

        assertThat(refreshToken).isNotNull();
        assertThat(refreshToken).isNotEmpty();
        assertThat(jwtTokenProvider.validateToken(refreshToken)).isTrue();
    }

    @Test
    @Order(7)
    @DisplayName("测试从Header提取Token")
    void testExtractTokenFromHeader() {
        String authHeader = "Bearer " + testToken;
        String extracted = jwtTokenProvider.extractTokenFromHeader(authHeader);

        assertThat(extracted).isEqualTo(testToken);
    }

    @Test
    @Order(8)
    @DisplayName("测试Token过期时间配置")
    void testTokenExpiration() {
        assertThat(jwtProperties.getExpiration()).isEqualTo(86400000L);
        assertThat(jwtProperties.getRefreshExpiration()).isEqualTo(604800000L);
    }

    @Test
    @Order(9)
    @DisplayName("测试Token携带唯一 jti 及签发/过期时间")
    void testTokenContainsJti() {
        String token1 = jwtTokenProvider.generateAccessToken(TEST_USER_ID, TEST_USERNAME, TEST_USER_ROLE, TEST_EXPIRATION);
        String token2 = jwtTokenProvider.generateAccessToken(TEST_USER_ID, TEST_USERNAME, TEST_USER_ROLE, TEST_EXPIRATION);

        String jti1 = jwtTokenProvider.getJtiFromToken(token1);
        String jti2 = jwtTokenProvider.getJtiFromToken(token2);

        assertThat(jti1).isNotBlank();
        assertThat(jti2).isNotBlank();
        // 每个 token 的 jti 必须唯一，黑名单才能精确吊销单个 token
        assertThat(jti1).isNotEqualTo(jti2);
        assertThat(jwtTokenProvider.getIssuedAtFromToken(token1)).isNotNull();
        assertThat(jwtTokenProvider.getExpirationFromToken(token1))
                .isAfter(jwtTokenProvider.getIssuedAtFromToken(token1));
    }

    @Test
    @Order(10)
    @DisplayName("测试7天免登录Token：携带 rememberMe claim 且有效期为 168h")
    void testRememberMeToken() {
        String rememberToken = jwtTokenProvider.generateAccessToken(TEST_USER_ID, TEST_USERNAME, TEST_USER_ROLE, 604800000L, true);
        String normalToken = jwtTokenProvider.generateAccessToken(TEST_USER_ID, TEST_USERNAME, TEST_USER_ROLE, TEST_EXPIRATION);

        assertThat(jwtTokenProvider.isRememberMeToken(rememberToken)).isTrue();
        assertThat(jwtTokenProvider.isRememberMeToken(normalToken)).isFalse();

        // 免登录 token 有效期 = 168h
        long ttl = jwtTokenProvider.getExpirationFromToken(rememberToken).getTime()
                - jwtTokenProvider.getIssuedAtFromToken(rememberToken).getTime();
        assertThat(ttl).isEqualTo(604800000L);
    }
}
