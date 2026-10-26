package csulzc.My_Personal_Blogger.service.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * 轻量级 SMTP 邮件服务：不依赖 Spring Boot JavaMailSender 自动配置，直接通过 Socket 与 SMTP 服务器通信。
 *
 * <p>设计目标：在 Docker 等极简环境中，即使未引入 spring-boot-starter-mail，
 * 也能通过纯 Java Socket 发送验证码邮件。支持明文 / SSL / STARTTLS 三种模式。</p>
 *
 * <p>配置方式（application.yml 或环境变量）：</p>
 * <pre>
 * app.smtp.host: smtp.example.com
 * app.smtp.port: 465               # SSL=465, STARTTLS=587, 明文=25
 * app.smtp.username: user@example.com
 * app.smtp.password: secret
 * app.smtp.from: noreply@example.com
 * app.smtp.ssl-enable: true        # 465 端口设为 true
 * app.smtp.starttls-enable: false  # 587 端口设为 true
 * </pre>
 */
@Slf4j
@Service
public class LightweightMailService {

    @Value("${app.smtp.host:}")
    private String host;

    @Value("${app.smtp.port:25}")
    private int port;

    @Value("${app.smtp.username:}")
    private String username;

    @Value("${app.smtp.password:}")
    private String password;

    @Value("${app.smtp.from:noreply@example.com}")
    private String from;

    @Value("${app.smtp.ssl-enable:false}")
    private boolean sslEnable;

    @Value("${app.smtp.starttls-enable:false}")
    private boolean starttlsEnable;

    @Value("${app.smtp.timeout-ms:10000}")
    private int timeoutMs;

    /**
     * 发送忘记密码验证码邮件。
     *
     * @param to         收件人邮箱
     * @param code       验证码
     * @param ttlMinutes 有效期（分钟）
     * @throws IllegalStateException SMTP 未配置或发送失败
     */
    public void sendVerificationCode(String to, String code, int ttlMinutes) {
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("SMTP 服务未配置，请联系管理员");
        }

        String subject = "【行吟博客】密码重置验证码";
        String body = "您正在重置登录密码。\n\n验证码：" + code
                + "\n\n有效期 " + ttlMinutes + " 分钟，验证一次后即失效。"
                + "\n若非本人操作，请忽略本邮件并注意账号安全。";

        try {
            sendEmail(to, subject, body);
            log.info("验证码邮件已发送: to={}", maskEmail(to));
        } catch (Exception e) {
            log.error("验证码邮件发送失败: to={}, error={}", maskEmail(to), e.getMessage());
            throw new IllegalStateException("验证码邮件发送失败，请稍后重试", e);
        }
    }

    /**
     * 通用文本邮件发送。
     */
    public void sendEmail(String to, String subject, String body) throws IOException {
        try (SmtpConnection conn = new SmtpConnection()) {
            conn.connect();
            conn.ehlo();

            if (starttlsEnable) {
                conn.startTls();
                conn.ehlo(); // STARTTLS 后需重新 EHLO
            }

            if (username != null && !username.isBlank()) {
                conn.authLogin(username, password);
            }

            conn.mailFrom(from);
            conn.rcptTo(to);
            conn.data(buildMimeMessage(to, subject, body));
            conn.quit();
        }
    }

    /**
     * 构建标准 MIME 邮件内容。
     */
    private String buildMimeMessage(String to, String subject, String body) {
        return "From: " + from + "\r\n"
                + "To: " + to + "\r\n"
                + "Subject: =?UTF-8?B?" + Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8)) + "?=\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: 8bit\r\n"
                + "\r\n"
                + body + "\r\n";
    }

    /**
     * SMTP 连接封装：负责 Socket 建立、命令发送与响应读取。
     */
    private class SmtpConnection implements Closeable {
        private Socket socket;
        private BufferedReader reader;
        private PrintWriter writer;

        void connect() throws IOException {
            socket = new Socket();
            socket.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);

            if (sslEnable) {
                socket = ((javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault())
                        .createSocket(socket, host, port, true);
                ((javax.net.ssl.SSLSocket) socket).startHandshake();
            }

            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            expect(220); // 服务器欢迎语
        }

        void ehlo() throws IOException {
            send("EHLO " + host);
            expect(250);
        }

        void startTls() throws IOException {
            send("STARTTLS");
            expect(220);
            socket = ((javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault())
                    .createSocket(socket, host, port, true);
            ((javax.net.ssl.SSLSocket) socket).startHandshake();
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        }

        void authLogin(String user, String pass) throws IOException {
            send("AUTH LOGIN");
            expect(334);
            send(Base64.getEncoder().encodeToString(user.getBytes(StandardCharsets.UTF_8)));
            expect(334);
            send(Base64.getEncoder().encodeToString(pass.getBytes(StandardCharsets.UTF_8)));
            expect(235);
        }

        void mailFrom(String from) throws IOException {
            send("MAIL FROM:<" + from + ">");
            expect(250);
        }

        void rcptTo(String to) throws IOException {
            send("RCPT TO:<" + to + ">");
            expect(List.of(250, 251));
        }

        void data(String content) throws IOException {
            send("DATA");
            expect(354);
            // 防止内容中误含 \r\n. 终止符
            String safeContent = content.replace("\r\n.", "\r\n..");
            writer.print(safeContent + "\r\n.\r\n");
            writer.flush();
            expect(250);
        }

        void quit() throws IOException {
            send("QUIT");
            expect(221);
        }

        private void send(String command) {
            writer.print(command + "\r\n");
            writer.flush();
        }

        private void expect(int expectedCode) throws IOException {
            expect(List.of(expectedCode));
        }

        private void expect(List<Integer> expectedCodes) throws IOException {
            String response = readResponse();
            int code = Integer.parseInt(response.substring(0, 3));
            if (!expectedCodes.contains(code)) {
                throw new IOException("SMTP 响应异常: " + response);
            }
        }

        private String readResponse() throws IOException {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
                if (line.length() >= 4 && line.charAt(3) == ' ') {
                    break; // 最后一行
                }
                sb.append("\n");
            }
            return sb.toString();
        }

        @Override
        public void close() throws IOException {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    /** 日志中脱敏邮箱 */
    private String maskEmail(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
