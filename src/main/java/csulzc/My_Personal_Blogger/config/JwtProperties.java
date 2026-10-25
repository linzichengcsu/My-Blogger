package csulzc.My_Personal_Blogger.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;

    private long expiration = 86400000;

    private long refreshExpiration = 604800000;

    /** 「7天免登录」token 的有效期：默认 168h（7 天），登出不拉黑、等自然过期 */
    private long rememberMeExpiration = 604800000;

    private String tokenPrefix = "Bearer ";

    private String header = "Authorization";

    public String getSecret() {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalStateException("JWT密钥未配置，请在application.yml中设置jwt.secret");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException("JWT密钥长度至少为32字符，当前长度: " + secret.length());
        }
        return secret;
    }
}
