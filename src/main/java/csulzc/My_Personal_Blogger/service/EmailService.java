package csulzc.My_Personal_Blogger.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 邮件发送服务：当前仅用于忘记密码验证码。
 *
 * <p>spring.mail.host 未配置时 JavaMailSender Bean 不存在，发送时抛出明确异常；
 * SMTP 发送失败不静默吞掉——验证码未送达用户却提示已发送会造成功能不可用且难排查。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${spring.mail.username:${MAIL_FROM:noreply@example.com}}")
    private String from;

    /**
     * 发送忘记密码验证码邮件。
     *
     * @throws IllegalStateException 邮件服务未配置或发送失败
     */
    public void sendVerificationCode(String to, String code, int ttlMinutes) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new IllegalStateException("邮件服务未配置，请联系管理员");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("【个人博客】密码重置验证码");
        message.setText("您正在重置登录密码。\n\n验证码：" + code
                + "\n\n有效期 " + ttlMinutes + " 分钟，验证一次后即失效。"
                + "\n若非本人操作，请忽略本邮件并注意账号安全。");
        try {
            mailSender.send(message);
            log.info("忘记密码验证码邮件已发送: to={}", maskEmail(to));
        } catch (MailException e) {
            log.error("验证码邮件发送失败: to={}, error={}", maskEmail(to), e.getMessage());
            throw new IllegalStateException("验证码邮件发送失败，请稍后重试", e);
        }
    }

    /** 日志中脱敏邮箱，避免完整地址进入日志 */
    private String maskEmail(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
