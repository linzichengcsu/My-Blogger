package csulzc.My_Personal_Blogger.security;

import csulzc.My_Personal_Blogger.config.JwtProperties;
import csulzc.My_Personal_Blogger.domain.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    /**
     * 生成访问令牌
     */
    public String generateAccessToken(Long userId, String username, User.UserRole role, long expiration) {
        return generateAccessToken(userId, username, role, expiration, false);
    }

    /**
     * 生成访问令牌
     *
     * @param rememberMe 「7天免登录」：true 时写入 rememberMe claim，
     *                   登出时不会被拉黑，等 token 自然过期（168h）
     */
    public String generateAccessToken(Long userId, String username, User.UserRole role, long expiration, boolean rememberMe) {
        return generateToken(userId, username, role.name(), expiration, rememberMe);
    }

    /**
     * 生成刷新令牌
     */
    public String generateRefreshToken(Long userId, String username, User.UserRole role, long expiration) {
        return generateToken(userId, username, role.name(), expiration);
    }

    /**
     * 生成令牌（携带角色）
     */
    public String generateToken(Long userId, String username, String role, long expiration) {
        return generateToken(userId, username, role, expiration, false);
    }

    /**
     * 生成令牌（携带角色 + 可选免登录标记）
     */
    public String generateToken(Long userId, String username, String role, long expiration, boolean rememberMe)
    {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("role", role);
        claims.put("type", "access");
        if (rememberMe) {
            claims.put("rememberMe", true);
        }

        SecretKey key = getSigningKey();

        return Jwts.builder()
                .claims(claims)
                .id(UUID.randomUUID().toString())
                .subject(username)
                .issuedAt(now)
                .expiration(expiryDate)
                .notBefore(now)
                .signWith(key)
                .compact();
    }

    /**
     * 从令牌中获取用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        Long userId = claims.get("userId", Long.class);
        if (userId == null) {
            throw new JwtException("Token中缺少用户ID");
        }
        return userId;

    }

    /**
     * 从令牌中获取用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.getSubject();
    }

    /**
     * 从令牌中获取用户角色
     */
    public String getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("role", String.class);
    }

    /**
     * 从令牌中获取 jti（令牌唯一标识，用于黑名单）
     */
    public String getJtiFromToken(String token) {
        return parseToken(token).getId();
    }

    /**
     * 从令牌中获取签发时间
     */
    public Date getIssuedAtFromToken(String token) {
        return parseToken(token).getIssuedAt();
    }

    /**
     * 从令牌中获取过期时间
     */
    public Date getExpirationFromToken(String token) {
        return parseToken(token).getExpiration();
    }

    /**
     * 判断是否为「7天免登录」token（是则登出时不拉黑，等自然过期）
     */
    public boolean isRememberMeToken(String token) {
        return Boolean.TRUE.equals(parseToken(token).get("rememberMe", Boolean.class));
    }

    /**
     * 验证令牌是否有效
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);

            Date expiration = claims.getExpiration();
            Date notBefore = claims.getNotBefore();
            Date now = new Date();

            if (now.before(notBefore)) {
                log.error("Token尚未生效");
                return false;
            }

            if (now.after(expiration)) {
                log.error("Token已过期");
                return false;
            }

            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("JWT验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 解析令牌
     */
    private Claims parseToken(String token) {
        SecretKey key = getSigningKey();

        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 获取签名密钥
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 从请求头中提取令牌
     */
    public String extractTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith(jwtProperties.getTokenPrefix())) {
            return authHeader.substring(jwtProperties.getTokenPrefix().length());
        }
        return null;
    }

}
