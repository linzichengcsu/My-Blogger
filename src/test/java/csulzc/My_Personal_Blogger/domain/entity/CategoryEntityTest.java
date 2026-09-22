package csulzc.My_Personal_Blogger.domain.entity;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CategoryEntityTest {

    private Category category;

    @BeforeEach
    void setUp()
    {
        category = new Category();
    }

    @AfterEach
    void tearDown()
    {
        category = null;
    }

    @Test
    @Order(1)
    @DisplayName("测试对象创建")
    void testObjectCreation()
    {
        assertNotNull(category);
        assertInstanceOf(Category.class, category);
    }

    @Test
    @Order(2)
    @DisplayName("测试ID属性")
    void testIdProperty()
    {
        // 测试null
        assertNull(category.getId());

        // 测试设置值
        category.setId(100L);
        assertEquals(100L, category.getId());

        // 测试更新
        category.setId(200L);
        assertEquals(200L, category.getId());

        // 测试null
        category.setId(null);
        assertNull(category.getId());
    }

    @Test
    @Order(4)
    @DisplayName("测试名称属性")
    void testNameProperty()
    {
        String[] testValues = {
                "人猿相揖别。只几个石头磨过，小儿时节。铜铁炉中翻火焰，为问何时猜得？",
                "不过几千寒热。人世难逢开口笑，上疆场彼此弯弓月。流遍了，郊原血。",
                "一篇读罢头飞雪，但记得斑斑点点，几行陈迹。五帝三皇神圣事，骗了无涯过客。",
                "有多少风流人物？盗跖庄蹻流誉后，更陈王奋起挥黄钺。歌未竟，东方白。",
                "自由寻觅快乐，别人从无法感受。跳进似箭快车，通宵通街通处闯。",
                "速度从没界限，黑夜弥漫了冲动。纵有挫折困苦，不可不可将我缚。",
                "室内长夜困着，不愿人渐变痴騃。远处听有叫声，仿佛仿佛跟你讲。",
                "音量无限震动，声浪停顿了心脏。跳跃节奏炸开，一堆一堆的叹息。",
                "拨着大雾默默地在觅我的去路，但愿路上幸运遇着是你的脚步。我要再见你，只想将心声透露：爱慕。",
                "独自望着路上密密画满的记号，像是混乱又像特别为了指我路。到处去碰，到处去看，堕入陷阱，方知太糊涂。",
                "Warrior monks that take combat as an art form where perfection is just the beginning.",
                "Deal 16 Damage. Increase its Damage by the number of Mantra you gain this combat.",
                "Innate. Shuffle a Beta into your draw pile.",
                "If the enemy intends to attack, gain 3 Strength.",
                "Deal 17 Damage to ALL enemies. Exhaust.",
                "Gain 5 Focus. Lose 1 Focus each turn.",
                "Apply 2 Vulnerable to ALL enemies.",
                "", "  ", "    "
        };

        for (String value : testValues)
        {
            category.setName(value);
            assertEquals(value, category.getName());
        }

        category.setName(null);
        assertNull(null, category.getName());
    }

    @Test
    @Order(5)
    @DisplayName("测试所有属性组合")
    void testAllPropertiesTogether()
    {
        Category expectedCategory = new Category();
        expectedCategory.setId(3L);
        expectedCategory.setName("Hello World!");

        category.setId(3L);
        category.setName("Hello World!");

        assertAll("完整Article对象验证",
                () -> assertEquals(expectedCategory.getId(), category.getId()),
                () -> assertEquals(expectedCategory.getName(), category.getName())
        );
    }
}