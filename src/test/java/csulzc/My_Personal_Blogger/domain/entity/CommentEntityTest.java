package csulzc.My_Personal_Blogger.domain.entity;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CommentEntityTest {

    private Comment comment;

    @BeforeEach
    void setUp()
    {
        comment = new Comment();
    }

    @AfterEach
    void tearDown()
    {
        comment = null;
    }

    @Test
    @Order(1)
    @DisplayName("测试对象创建")
    void testObjectCreation()
    {
        assertNotNull(comment);
        assertInstanceOf(Comment.class, comment);
    }

    @Test
    @Order(2)
    @DisplayName("测试ID属性")
    void testIdProperty()
    {
        // 测试null
        assertNull(comment.getId());

        // 测试设置值
        comment.setId(100L);
        assertEquals(100L, comment.getId());

        // 测试更新
        comment.setId(200L);
        assertEquals(200L, comment.getId());

        // 测试null
        comment.setId(null);
        assertNull(comment.getId());
    }

    @Test
    @Order(4)
    @DisplayName("测试内容属性")
    void testContentProperty()
    {
        String[] testValues = {
                "先打分散和孤立之敌，后打集中和强大之敌。",
                "才饮长沙水，又食武昌鱼。万里长江横渡，极目楚天舒。不管风吹浪打，胜似闲庭信步，今日得宽余。",
                "风雷动，旌旗奋，是人寰。三十八年过去，弹指一挥间。",
                "多少春秋风雨改，多少崎岖不变爱，多少唏嘘的你在人海？",
                "哦，你可知，谁甘心归去？你与我之间有谁（Spoiler: 不列颠尼亚）？",
                "假如你先生回到鹿港小镇，请问你是否告诉我的爹娘？台北不是我想象的黄金天堂，都市里没有当初我的梦想。",
                "噢噢噢噢————我有我心底故事。亲手写上心中得失乐与悲与梦儿。",
                "神秘的Lorelei，居于风眼中。活着只喜欢破坏，事后无踪。",
                "只因心中温暖都跟她消失去，今天只剩一串泪水。说爱我百万年的她，今爱着谁？",
                "在雨中漫步，蓝色街灯渐落。相对望，无声紧拥抱着。为了找往日，寻温馨的往日，消失了。",
                "Warrior monks that take combat as an art form where perfection is just the beginning.",
                "Retain. Enter Divinity. Die next turn. Exhaust.",
                "Retain. Gain 7 Block. Whenever this card is Retained, increase its Block by 3.",
                "Exhaust all non-Attack cards in your hand. Gain 7 Block for each card Exhausted.",
                "Can only be played when if there are no cards in your draw pile. Deal 60 Damage to ALL enemies.",
                "Deal 14 Damage. Put all Cost 0 cards from your discard pile to your hand.",
                "Put 4 random attack cards from your draw pile to your hand. Exhaust.",
                "", "  ", "    "
        };

        for (String value : testValues)
        {
            comment.setContent(value);
            assertEquals(value, comment.getContent());
        }

        comment.setContent(null);
        assertNull(null, comment.getContent());
    }

    @Test
    @Order(5)
    @DisplayName("测试所有属性组合")
    void testAllPropertiesTogether()
    {
        Comment expectedComment = new Comment();
        expectedComment.setId(3L);
        expectedComment.setContent("Hello World!");

        comment.setId(3L);
        comment.setContent("Hello World!");

        assertAll("完整Article对象验证",
                () -> assertEquals(expectedComment.getId(), comment.getId()),
                () -> assertEquals(expectedComment.getContent(), comment.getContent())
        );
    }
}
