package csulzc.My_Personal_Blogger.domain.entity;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserEntityTest
{

    private User user;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp()
    {
        user = new User();
        passwordEncoder = new BCryptPasswordEncoder();
    }

    @AfterEach
    void tearDown()
    {
        user = null;
        passwordEncoder = null;
    }

    @Test
    @Order(1)
    @DisplayName("测试对象创建")
    void testObjectCreation()
    {
        assertNotNull(user);
        assertInstanceOf(User.class, user);
    }

    @Test
    @Order(2)
    @DisplayName("测试ID属性")
    void testIdProperty()
    {
        // 测试null
        assertNull(user.getId());

        // 测试设置值
        user.setId(100L);
        assertEquals(100L, user.getId());

        // 测试更新
        user.setId(200L);
        assertEquals(200L, user.getId());

        // 测试null
        user.setId(null);
        assertNull(user.getId());
    }

    @Test
    @Order(3)
    @DisplayName("测试用户名属性")
    void testUsernameProperty()
    {
        // 测试边界值
        String[] testValues = {"john", "a", "", "   ", "very_long_username_123"};

        for (String value : testValues)
        {
            user.setUsername(value);
            assertEquals(value, user.getUsername());
        }

        // 测试null
        user.setUsername(null);
        assertNull(user.getUsername());
    }

    @Test
    @Order(4)
    @DisplayName("测试密码哈希值属性")
    void testPasswordHashProperty()
    {
        user.setPlainPassword("LamZising888+++");
        String encodedPassword = passwordEncoder.encode(user.getPlainPassword());

        user.setPasswordHash(encodedPassword);

        assertNotNull(user.getPasswordHash());
        assertNotEquals(user.getPlainPassword(), user.getPasswordHash());
        assertTrue(passwordEncoder.matches(user.getPlainPassword(), user.getPasswordHash()));
    }

    @Test
    @Order(5)
    @DisplayName("测试邮箱属性")
    void testEmailProperty()
    {
        user.setEmail("test@example.com");
        assertEquals("test@example.com", user.getEmail());

        // 测试邮箱格式（如果业务需要）
        String email = user.getEmail();
        assertTrue(email.contains("@"), "邮箱应包含@符号");
        assertTrue(email.contains("."), "邮箱应包含点号");
    }

    @Test
    @Order(6)
    @DisplayName("测试展示名属性")
    void testDisplayNameProperty()
    {
        user.setDisplayName("John Doe");
        assertEquals("John Doe", user.getDisplayName());

        user.setDisplayName("张三");
        assertEquals("张三", user.getDisplayName());
    }

    @Test
    @Order(7)
    @DisplayName("测试所有属性组合")
    void testAllPropertiesTogether()
    {
        User expectedUser = new User();
        expectedUser.setId(1L);
        expectedUser.setUsername("johndoe");
        expectedUser.setEmail("john@example.com");
        expectedUser.setDisplayName("John Doe");

        user.setId(1L);
        user.setUsername("johndoe");
        user.setEmail("john@example.com");
        user.setDisplayName("John Doe");

        assertAll("完整User对象验证",
                () -> assertEquals(expectedUser.getId(), user.getId()),
                () -> assertEquals(expectedUser.getUsername(), user.getUsername()),
                () -> assertEquals(expectedUser.getEmail(), user.getEmail()),
                () -> assertEquals(expectedUser.getDisplayName(), user.getDisplayName())
        );
    }

    @Nested
    @DisplayName("异常情况测试")
    class ExceptionTests {

        @Test
        @DisplayName("测试超长字符串")
        void testVeryLongStrings()
        {
            String veryLong = "a".repeat(1000);  // 1000个字符
            user.setUsername(veryLong);
            assertEquals(veryLong, user.getUsername());

            // 假设数据库有长度限制，这里只是测试Java对象
            assumeTrue(user.getUsername().length() <= 255,
                    "注意：用户名可能需要考虑数据库长度限制");
        }

        @Test
        @DisplayName("测试特殊字符")
        void testSpecialCharacters()
        {
            String specialChars = "!@#$%^&*()_+{}[]|\\:;\"'<>,.?/~`";
            user.setUsername(specialChars);
            assertEquals(specialChars, user.getUsername());
        }
    }

    @Nested
    @DisplayName("性能测试")
    class PerformanceTests
    {

        @Test
        @DisplayName("测试大量set/get操作")
        void testMassOperations() {
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < 10000000; i++)
            {
                user.setId((long) i);
                user.getId();
                user.setUsername("user" + i);
                user.getUsername();
            }

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            assertTrue(duration < 2000, "10000000次操作应该在2000ms内完成");
        }
    }
}
