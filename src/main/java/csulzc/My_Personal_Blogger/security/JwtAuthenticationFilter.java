package csulzc.My_Personal_Blogger.security;

import csulzc.My_Personal_Blogger.service.JwtBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtBlacklistService jwtBlacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
                Long userId = jwtTokenProvider.getUserIdFromToken(jwt);

                // 主动失效检查：登出黑名单（jti）+ 用户封禁时间戳（iat 早于封禁时间的 token 作废）
                String jti = jwtTokenProvider.getJtiFromToken(jwt);
                Date issuedAt = jwtTokenProvider.getIssuedAtFromToken(jwt);
                if (jwtBlacklistService.isBlacklisted(jti)
                        || jwtBlacklistService.isIssuedBeforeBan(userId, issuedAt)) {
                    log.debug("Token 已主动失效，拒绝认证: userId={}, jti={}", userId, jti);
                    filterChain.doFilter(request, response);
                    return;
                }

                String username = jwtTokenProvider.getUsernameFromToken(jwt);
                String role = jwtTokenProvider.getRoleFromToken(jwt);

                String authority = role != null ? role : "USER";

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + authority))
                        );

                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("用户认证成功: userId={}, username={}, role={}", userId, username, authority);
            }
        } catch (Exception ex) {
            log.error("JWT认证处理失败", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
