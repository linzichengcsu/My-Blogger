package csulzc.My_Personal_Blogger.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class RequestSourceResolver {

    /** 前端可信来源（与 CorsConfig 共用，避免两处维护） */
    public static final List<String> FRONTEND_ORIGINS = List.of(
            "http://localhost:3000",
            "http://localhost:3001",
            "http://localhost:5173",
            "http://localhost:8080",
            "http://127.0.0.1:3000",
            "http://127.0.0.1:5173",
            "https://your-frontend-domain.com"
    );

    /**
     * 判断请求是否来自浏览器前端
     */
    public boolean isFromFrontend(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        return StringUtils.hasText(origin) && FRONTEND_ORIGINS.contains(origin);
    }

    /**
     * 解析客户端真实 IP：优先取反向代理透传的 X-Forwarded-For 首段，
     * 其次 X-Real-IP，最后回退到直连地址。
     */
    public String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            // X-Forwarded-For: client, proxy1, proxy2 —— 首段为客户端原始 IP
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty() && !"unknown".equalsIgnoreCase(first)) {
                return first;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp) && !"unknown".equalsIgnoreCase(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}