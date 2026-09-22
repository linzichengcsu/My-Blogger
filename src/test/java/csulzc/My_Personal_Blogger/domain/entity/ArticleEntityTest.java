package csulzc.My_Personal_Blogger.domain.entity;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ArticleEntityTest
{
    private Article article;

    @BeforeEach
    void setUp()
    {
        article = new Article();
    }

    @AfterEach
    void tearDown()
    {
        article = null;
    }

    @Test
    @Order(1)
    @DisplayName("测试对象创建")
    void testObjectCreation()
    {
        assertNotNull(article);
        assertInstanceOf(Article.class, article);
    }

    @Test
    @Order(2)
    @DisplayName("测试ID属性")
    void testIdProperty()
    {
        // 测试null
        assertNull(article.getId());

        // 测试设置值
        article.setId(100L);
        assertEquals(100L, article.getId());

        // 测试更新
        article.setId(200L);
        assertEquals(200L, article.getId());

        // 测试null
        article.setId(null);
        assertNull(article.getId());
    }

    @Test
    @Order(3)
    @DisplayName("测试标题属性")
    void testTitleProperty()
    {
        String[] testValues = {
                "Slay the Spire", "Ironclad", "Silent", "Defect", "Watcher",
                "John Doe", "On Protracted War", "Pride and Prejudice", "Little Prince",
                "No Silver Bullets: Essences and Accidents of Software Engineering",
                "The Mythical Man-Month", "Current Situations and Our Tasks",
                "目前形势和我们的任务", "冷雨夜", "左家垅行吟者", "时间吞噬者", "八体甜圈", "腐化之心",
                "", "  ", "    ", "a"
        };

        for (String value : testValues)
        {
            article.setTitle(value);
            assertEquals(value, article.getTitle());
        }

        article.setTitle(null);
        assertNull(null, article.getTitle());
    }

    @Test
    @Order(4)
    @DisplayName("测试内容属性")
    void testContentProperty()
    {
        String[] testValues = {
                "正是神都有事时，却来南国踏芳枝。",
                "久有凌云志，重上井冈山。千里来寻故地，旧貌变新颜。到处莺歌燕舞，更有潺潺流水，高路入云端。",
                "风樯动，龟蛇静，起宏图。一桥飞架南北，天堑变通途。",
                "是缘是情是童真，还是意外？有泪有罪有付出，还有忍耐。",
                "是人是墙是寒冬，藏在眼内；有日有夜有幻想，无法等待！",
                "假如你先生来自鹿港小镇，请问你是否看见我的爹娘？我家就住在妈祖庙的后面，卖着香火的那家小杂货店。",
                "噢噢噢噢————纵有创伤不退避。梦想有日达成找到心底梦想的世界，终可见。",
                "狂风猛风，怒似狂龙。巨浪乱撞乱动，雷电用疾劲暴力划破这世界，震裂天空。",
                "仄敝的沙滩里、金啡色的肌肤里，闪烁暑天的汗水。我却觉冷又寒，缩起双肩苦笑着，北风仿佛心中四吹。",
                "在雨中漫步，尝水中的味道。仿似是，情此刻的尽时。未了解GitHub，留低思忆片段，不经意。",
                "Warrior monks that take combat as an art form where perfection is just the beginning.",
                "Choose a card in your draw pile. Play the chosen card twice and exhaust it. Exhaust.",
                "Retain. Deal 10 Damage. Whenever this card is Retained, increase its damage by 5.",
                "Exhaust your hand. Deal 10 Damage for each card Exhausted. Exhaust.",
                "Put a card from your hand on top of your draw pile. It costs 0 until played.",
                "Evoke all your Orbs. Gain 1 Energy and draw 1 card for each Orb Evoked. Exhaust.",
                "Innate. Deal Damage equal to the number of cards in your draw pile.",
                "", "  ", "    "
        };

        for (String value : testValues)
        {
            article.setContent(value);
            assertEquals(value, article.getContent());
        }

        article.setContent(null);
        assertNull(null, article.getContent());
    }

    @Test
    @Order(5)
    @DisplayName("测试所有属性组合")
    void testAllPropertiesTogether()
    {
        Article expectedArticle = new Article();
        expectedArticle.setId(3L);
        expectedArticle.setTitle("Hello Java!");
        expectedArticle.setContent("Hello World!");

        article.setId(3L);
        article.setTitle("Hello Java!");
        article.setContent("Hello World!");

        assertAll("完整Article对象验证",
                () -> assertEquals(expectedArticle.getId(), article.getId()),
                () -> assertEquals(expectedArticle.getTitle(), article.getTitle()),
                () -> assertEquals(expectedArticle.getContent(), article.getContent())
        );
    }

    @Nested
    @DisplayName("异常情况测试")
    class ExceptionTests
    {
        @Test
        @DisplayName("测试超长字符串")
        void testVeryLongStrings()
        {
            String veryLong1 = "p".repeat(2000);
            String veryLong2 = "k".repeat(4000);
            article.setTitle(veryLong1);
            article.setContent(veryLong2);
            assertEquals(veryLong1, article.getTitle());
            assertEquals(veryLong2, article.getContent());

            assumeTrue(article.getTitle().length() <= 255,
                    "注意：标题可能需要考虑数据库长度限制");
            assumeTrue(article.getContent().length() <= 255,
                    "注意：内容可能需要考虑数据库长度限制");
        }

        @Test
        @DisplayName("测试特殊字符")
        void testSpecialCharacters()
        {
            String specialChars = "!@#$%^&*()_+{}[]|\\:;\"'<>,.?/~`";
            article.setTitle(specialChars);
            assertEquals(specialChars, article.getTitle());
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
                article.setId((long) i);
                article.getId();
                article.setTitle("user" + i);
                article.getTitle();
            }

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            assertTrue(duration < 5000, "10000000次操作应该在5000ms内完成");
        }
    }
}
