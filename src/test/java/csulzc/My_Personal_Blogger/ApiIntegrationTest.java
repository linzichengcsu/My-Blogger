package csulzc.My_Personal_Blogger;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 接口自动化集成测试脚本（端到端 HTTP 层）。
 *
 * <p>说明：</p>
 * <ul>
 *   <li>使用真实的安全过滤器链（JWT 鉴权），并通过随机端口启一个内嵌 Web 容器，
 *       通过 {@link TestRestTemplate} 以 HTTP 请求的方式调用接口，覆盖
 *       用户注册/登录/鉴权、管理员权限、分类/文章/评论 CRUD 等核心业务流程。</li>
 *   <li>使用 {@code test} Profile（H2 内存库），无需依赖本地 MySQL / Redis，
 *       可通过 Maven 一键运行：{@code mvn -Dtest=ApiIntegrationTest test}，便于接入 CI。</li>
 *   <li>测试方法按 {@link Order} 顺序执行并共享状态（token、生成资源的 id 等）。</li>
 * </ul>
 *
 * <p>运行前提：JDK 23 + Maven（项目自身要求）。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("接口自动化集成测试")
class ApiIntegrationTest {

    // 统一账号（H2 为 create-drop，每次运行库都是全新的，固定账号安全）
    private static final String USERNAME   = "apitestuser";
    private static final String USER_EMAIL = "apitestuser@example.com";
    private static final String ADMIN_NAME   = "apitestadmin";
    private static final String ADMIN_EMAIL = "apitestadmin@example.com";
    // 满足 PasswordValidator 的复杂密码
    private static final String PASSWORD = "TestPassw0rd!";

    // 跨测试方法共享的状态
    private static String userToken;
    private static String userRefreshToken;
    private static String adminToken;
    private static String adminRefreshToken;
    private static Long userId;
    private static Long adminId;
    private static Long categoryId;
    private static Long articleId;
    private static Long commentId;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @LocalServerPort
    private int port;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    // ====================================================================
    // 1. 公共端点与健康检查
    // ====================================================================

    @Test
    @Order(1)
    @DisplayName("1.1 Actuator 健康检查（公开）")
    void actuatorHealth_IsPublic() {
        ResponseEntity<String> resp = get("/actuator/health", null);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode tree = json(resp.getBody());
        assertThat(tree.path("status").asText()).isEqualTo("UP");
    }

    @Test
    @Order(2)
    @DisplayName("1.2 根路径可访问（公开）")
    void root_IsPublic() {
        assertThat(get("/", null).getStatusCode().value()).isEqualTo(200);
    }

    // ====================================================================
    // 2. 用户：注册 / 登录 / 鉴权 / 刷新
    // ====================================================================

    @Test
    @Order(10)
    @DisplayName("2.1 注册普通用户成功")
    void registerUser_Success() {
        String body = toJson(Map.of(
                "username", USERNAME,
                "email", USER_EMAIL,
                "password", PASSWORD,
                "displayName", "接口测试用户",
                "role", "USER"
        ));
        ResponseEntity<String> resp = postJson("/api/users/register", body, null);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode tree = json(resp.getBody());
        assertThat(tree.path("code").asInt()).isEqualTo(200);
        assertThat(tree.path("data").path("username").asText()).isEqualTo(USERNAME);
        userId = tree.path("data").path("id").asLong();
    }

    @Test
    @Order(11)
    @DisplayName("2.2 重复注册同名用户 -> 400")
    void registerUser_DuplicateUsername() {
        String body = toJson(Map.of(
                "username", USERNAME,
                "email", "another@example.com",
                "password", PASSWORD
        ));
        assertThat(postJson("/api/users/register", body, null).getStatusCode().value())
                .isEqualTo(400);
    }

    @Test
    @Order(12)
    @DisplayName("2.3 注册弱密码 -> 400")
    void registerUser_WeakPassword() {
        String body = toJson(Map.of(
                "username", "weakuser",
                "email", "weak@example.com",
                "password", "12345678"
        ));
        assertThat(postJson("/api/users/register", body, null).getStatusCode().value())
                .isEqualTo(400);
    }

    @Test
    @Order(13)
    @DisplayName("2.4 注册超级管理员 -> 400（接口不允许）")
    void registerUser_SuperAdminBlocked() {
        String body = toJson(Map.of(
                "username", "superadmin",
                "email", "super@example.com",
                "password", PASSWORD,
                "role", "SUPER_ADMIN"
        ));
        assertThat(postJson("/api/users/register", body, null).getStatusCode().value())
                .isEqualTo(400);
    }

    @Test
    @Order(14)
    @DisplayName("2.5 普通用户登录成功")
    void loginUser_Success() {
        ResponseEntity<String> resp = postJson("/api/users/login",
                toJson(Map.of("username", USERNAME, "password", PASSWORD)), null);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode tree = json(resp.getBody());
        assertThat(tree.path("code").asInt()).isEqualTo(200);
        assertThat(tree.path("data").path("tokenType").asText()).isEqualTo("Bearer");
        userToken = tree.path("data").path("accessToken").asText();
        userRefreshToken = tree.path("data").path("refreshToken").asText();
        assertThat(userToken).isNotBlank();
    }

    @Test
    @Order(15)
    @DisplayName("2.6 密码错误登录 -> 400")
    void loginUser_WrongPassword() {
        ResponseEntity<String> resp = postJson("/api/users/login",
                toJson(Map.of("username", USERNAME, "password", "WrongPass1!")), null);
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @Order(16)
    @DisplayName("2.7 携带 Token 获取当前用户信息")
    void getMyInfo_WithToken() {
        ResponseEntity<String> resp = get("/api/users/me", userToken);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode tree = json(resp.getBody());
        assertThat(tree.path("code").asInt()).isEqualTo(200);
        assertThat(tree.path("data").path("username").asText()).isEqualTo(USERNAME);
    }

    @Test
    @Order(17)
    @DisplayName("2.8 未携带 Token 访问受保护接口 -> 4xx")
    void getMyInfo_WithoutToken() {
        assertThat(get("/api/users/me", null).getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    @Order(18)
    @DisplayName("2.9 刷新 Token 成功")
    void refreshToken_Success() {
        ResponseEntity<String> resp = postJson(
                "/api/users/refresh?refreshToken=" + userRefreshToken, null, null);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode tree = json(resp.getBody());
        assertThat(tree.path("code").asInt()).isEqualTo(200);
        assertThat(tree.path("data").path("accessToken").asText()).isNotBlank();
    }

    // ====================================================================
    // 3. 管理员：注册 / 登录 / 权限校验
    // ====================================================================

    @Test
    @Order(20)
    @DisplayName("3.1 注册管理员（API 客户端允许创建 ADMIN）")
    void registerAdmin_Success() {
        String body = toJson(Map.of(
                "username", ADMIN_NAME,
                "email", ADMIN_EMAIL,
                "password", PASSWORD,
                "role", "ADMIN"
        ));
        ResponseEntity<String> resp = postJson("/api/users/register", body, null);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        adminId = json(resp.getBody()).path("data").path("id").asLong();
    }

    @Test
    @Order(21)
    @DisplayName("3.2 管理员登录成功")
    void loginAdmin_Success() {
        ResponseEntity<String> resp = postJson("/api/users/login",
                toJson(Map.of("username", ADMIN_NAME, "password", PASSWORD)), null);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode tree = json(resp.getBody());
        adminToken = tree.path("data").path("accessToken").asText();
        adminRefreshToken = tree.path("data").path("refreshToken").asText();
        assertThat(adminToken).isNotBlank();
    }

    @Test
    @Order(22)
    @DisplayName("3.3 管理员可访问看板统计（Admin 接口）")
    void adminDashboard_WithAdmin() {
        ResponseEntity<String> resp = get("/api/admin/dashboard/stats", adminToken);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode tree = json(resp.getBody());
        assertThat(tree.path("success").asBoolean()).isTrue();
        assertThat(tree.path("data").isMissingNode()).isFalse();
    }

    @Test
    @Order(23)
    @DisplayName("3.4 普通用户访问管理员接口 -> 403")
    void adminDashboard_WithUserDenied() {
        assertThat(get("/api/admin/dashboard/stats", userToken).getStatusCode().value())
                .isEqualTo(403);
    }

    @Test
    @Order(24)
    @DisplayName("3.5 管理员分页获取所有用户（Admin 接口）")
    void getAllUsers_WithAdmin() {
        ResponseEntity<String> resp = get("/api/users?page=0&size=10&sortBy=createdAt", adminToken);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(json(resp.getBody()).path("code").asInt()).isEqualTo(200);
    }

    // ====================================================================
    // 4. 分类管理
    // ====================================================================

    @Test
    @Order(30)
    @DisplayName("4.1 创建分类（登录用户）")
    void createCategory_Success() {
        ResponseEntity<String> resp = postJson("/api/categories",
                toJson(Map.of("name", "接口测试分类", "description", "由自动化脚本创建")), userToken);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode tree = json(resp.getBody());
        assertThat(tree.path("code").asInt()).isEqualTo(200);
        categoryId = tree.path("data").path("id").asLong();
        assertThat(categoryId).isPositive();
    }

    @Test
    @Order(31)
    @DisplayName("4.2 获取分类详情")
    void getCategoryById_Success() {
        ResponseEntity<String> resp = get("/api/categories/" + categoryId, userToken);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode tree = json(resp.getBody());
        assertThat(tree.path("code").asInt()).isEqualTo(200);
        assertThat(tree.path("data").path("id").asLong()).isEqualTo(categoryId);
    }

    @Test
    @Order(32)
    @DisplayName("4.3 更新分类")
    void updateCategory_Success() {
        ResponseEntity<String> resp = putJson("/api/categories/" + categoryId,
                toJson(Map.of("name", "接口测试分类-改", "description", "已更新")), userToken);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(json(resp.getBody()).path("code").asInt()).isEqualTo(200);
    }

    @Test
    @Order(33)
    @DisplayName("4.4 搜索分类（登录即可）")
    void searchCategory_Success() {
        ResponseEntity<String> resp = get("/api/categories/search?keyword=接口测试分类", userToken);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(json(resp.getBody()).path("code").asInt()).isEqualTo(200);
    }

    @Test
    @Order(34)
    @DisplayName("4.5 普通用户访问管理员专属的分类接口 -> 403（分类总数）")
    void categoryAdminOnly_WithUserDenied() {
        assertThat(get("/api/categories/stats/total", userToken).getStatusCode().value())
                .isEqualTo(403);
    }

    // ====================================================================
    // 5. 文章管理
    // ====================================================================

    @Test
    @Order(40)
    @DisplayName("5.1 创建文章（登录用户，默认草稿）")
    void createArticle_Success() {
        Map<String, Object> body = Map.of(
                "title", "接口测试文章标题",
                "content", "这是一段用于接口自动化测试的文章正文内容，长度超过二十个字符。",
                "summary", "自动化脚本创建的文章",
                "categoryIds", List.of(categoryId)
        );
        ResponseEntity<String> resp = postJson("/api/articles", toJson(body), userToken);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode tree = json(resp.getBody());
        assertThat(tree.path("code").asInt()).isEqualTo(200);
        articleId = tree.path("data").path("id").asLong();
        assertThat(articleId).isPositive();
    }

    @Test
    @Order(41)
    @DisplayName("5.2 发布文章")
    void publishArticle_Success() {
        ResponseEntity<String> resp = postJson("/api/articles/" + articleId + "/publish", null, userToken);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(json(resp.getBody()).path("code").asInt()).isEqualTo(200);
    }

    @Test
    @Order(42)
    @DisplayName("5.3 获取文章详情（公开访问）")
    void getArticleById_Success() {
        ResponseEntity<String> resp = get("/api/articles/" + articleId, null);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode tree = json(resp.getBody());
        assertThat(tree.path("code").asInt()).isEqualTo(200);
        assertThat(tree.path("data").path("id").asLong()).isEqualTo(articleId);
    }

    @Test
    @Order(43)
    @DisplayName("5.4 文章列表（公开，分页）")
    void getArticleList_Success() {
        ResponseEntity<String> resp = get(
                "/api/articles?page=0&size=5&sortBy=createdAt&sortDirection=desc", null);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(json(resp.getBody()).path("code").asInt()).isEqualTo(200);
    }

    @Test
    @Order(44)
    @DisplayName("5.5 搜索文章（公开）")
    void searchArticle_Success() {
        ResponseEntity<String> resp = get(
                "/api/articles/search?keyword=接口测试文章&page=0&size=5&sortBy=createdAt&sortDirection=desc",
                null);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(json(resp.getBody()).path("code").asInt()).isEqualTo(200);
    }

    // ====================================================================
    // 6. 评论管理
    // ====================================================================

    @Test
    @Order(50)
    @DisplayName("6.1 发表评论（登录用户）")
    void createComment_Success() {
        ResponseEntity<String> resp = postJson("/api/comments/article/" + articleId,
                toJson(Map.of("content", "自动化脚本发表的评论内容")), userToken);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode tree = json(resp.getBody());
        assertThat(tree.path("code").asInt()).isEqualTo(200);
        commentId = tree.path("data").path("id").asLong();
        assertThat(commentId).isPositive();
    }

    @Test
    @Order(51)
    @DisplayName("6.2 获取文章评论列表（需登录）")
    void getArticleComments_Success() {
        ResponseEntity<String> resp = get(
                "/api/comments/article/" + articleId + "?page=0&size=10&sortBy=createdAt", userToken);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(json(resp.getBody()).path("code").asInt()).isEqualTo(200);
    }

    @Test
    @Order(52)
    @DisplayName("6.3 统计文章评论数（需登录）")
    void countCommentsByArticle_Success() {
        ResponseEntity<String> resp = get("/api/comments/article/" + articleId + "/count", userToken);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode tree = json(resp.getBody());
        assertThat(tree.path("code").asInt()).isEqualTo(200);
        assertThat(tree.path("data").asLong()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Order(53)
    @DisplayName("6.4 删除评论（作者本人）")
    void deleteComment_Success() {
        ResponseEntity<String> resp = delete("/api/comments/" + commentId, userToken);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(json(resp.getBody()).path("code").asInt()).isEqualTo(200);
    }

    // ====================================================================
    // 7. 清理（降低对后续数据的影响）
    // ====================================================================

    @Test
    @Order(60)
    @DisplayName("7.1 删除文章（作者本人）")
    void deleteArticle_Success() {
        ResponseEntity<String> resp = delete("/api/articles/" + articleId, userToken);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(json(resp.getBody()).path("code").asInt()).isEqualTo(200);
    }

    @Test
    @Order(61)
    @DisplayName("7.2 删除分类（登录用户）")
    void deleteCategory_Success() {
        ResponseEntity<String> resp = delete("/api/categories/" + categoryId, userToken);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(json(resp.getBody()).path("code").asInt()).isEqualTo(200);
    }

    // ====================================================================
    // 工具方法
    // ====================================================================

    private ResponseEntity<String> get(String path, String token) {
        return request(HttpMethod.GET, path, token, null);
    }

    private ResponseEntity<String> postJson(String path, String jsonBody, String token) {
        return request(HttpMethod.POST, path, token, jsonBody);
    }

    private ResponseEntity<String> putJson(String path, String jsonBody, String token) {
        return request(HttpMethod.PUT, path, token, jsonBody);
    }

    private ResponseEntity<String> delete(String path, String token) {
        return request(HttpMethod.DELETE, path, token, null);
    }

    private ResponseEntity<String> request(HttpMethod method, String path, String token, String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null && !token.isEmpty()) {
            headers.setBearerAuth(token);
        }
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        return restTemplate.exchange(baseUrl() + path, method, entity, String.class);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new IllegalStateException("序列化请求体失败", e);
        }
    }

    private JsonNode json(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("解析响应体失败: " + body, e);
        }
    }
}
