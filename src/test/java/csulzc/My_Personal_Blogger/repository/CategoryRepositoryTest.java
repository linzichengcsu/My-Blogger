package csulzc.My_Personal_Blogger.repository;

import csulzc.My_Personal_Blogger.domain.entity.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("CategoryRepository 测试")
class CategoryRepositoryTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Category techCategory;
    private Category javaCategory;
    private Category springCategory;
    private User testAuthor;

    @BeforeEach
    void setUp() {
        // 准备测试作者
        testAuthor = User.builder()
                .username("author")
                .email("author@test.com")
                .passwordHash("password")
                .build();
        entityManager.persist(testAuthor);

        // 准备分类数据
        techCategory = Category.builder()
                .name("技术")
                .build();
        entityManager.persist(techCategory);

        javaCategory = Category.builder()
                .name("Java")
                .parentCategory(techCategory)
                .build();
        entityManager.persist(javaCategory);

        springCategory = Category.builder()
                .name("Spring")
                .parentCategory(techCategory)
                .build();
        entityManager.persist(springCategory);

        entityManager.flush();
        entityManager.clear();

        // 重新获取Managed实体
        techCategory = entityManager.find(Category.class, techCategory.getId());
        javaCategory = entityManager.find(Category.class, javaCategory.getId());
        springCategory = entityManager.find(Category.class, springCategory.getId());
    }

    @Test
    @DisplayName("根据名称查询分类 - 成功")
    void findByName_Success() {
        Optional<Category> found = categoryRepository.findByName("Java");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Java");
        assertThat(found.get().getParentCategory().getName()).isEqualTo("技术");
    }

    @Test
    @DisplayName("根据名称查询分类 - 不存在")
    void findByName_NotFound() {
        Optional<Category> found = categoryRepository.findByName("Python");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("查询顶级分类 - 没有父分类")
    void findByParentCategoryIsNull() {
        List<Category> topCategories = categoryRepository.findByParentCategoryIsNull();

        assertThat(topCategories).hasSize(1);
        assertThat(topCategories.get(0).getName()).isEqualTo("技术");
    }

    @Test
    @DisplayName("查询某个分类的所有子分类")
    void findByParentCategory() {
        List<Category> subCategories = categoryRepository.findByParentCategory(techCategory);

        assertThat(subCategories).hasSize(2);
        assertThat(subCategories)
                .extracting(Category::getName)
                .containsExactlyInAnyOrder("Java", "Spring");
    }

    @Test
    @DisplayName("查询分类及其文章数量 - 有文章的分类")
    void findAllWithArticleCount_WithArticles() {
        // 准备：创建一些文章并关联到分类
        createTestArticleWithCategories("Spring Boot入门", List.of(javaCategory, springCategory));
        createTestArticleWithCategories("Spring Cloud实战", List.of(springCategory));
        createTestArticleWithCategories("Java并发编程", List.of(javaCategory));

        List<Object[]> results = categoryRepository.findAllWithArticleCount();

        assertThat(results).hasSize(3);  // 技术、Java、Spring

        // 验证文章数量
        results.forEach(row -> {
            Category category = (Category) row[0];
            Long count = (Long) row[1];

            if (category.getName().equals("技术")) {
                assertThat(count).isEqualTo(0);  // 所有文章都属于技术
            } else if (category.getName().equals("Java")) {
                assertThat(count).isEqualTo(2);  // 两篇Java文章
            } else if (category.getName().equals("Spring")) {
                assertThat(count).isEqualTo(2);  // 两篇Spring文章
            }
        });
    }

    @Test
    @DisplayName("查询分类及其文章数量 - 无文章的分类")
    void findAllWithArticleCount_IncludingSubCategories_NoArticles() {
        List<Object[]> results = categoryRepository.findAllWithArticleCount();

        assertThat(results).hasSize(3);
        results.forEach(row -> {
            Long count = (Long) row[1];
            assertThat(count).isEqualTo(0);  // 所有分类文章数都是0
        });
    }

    @Test
    @DisplayName("查询某篇文章的所有分类")
    void findByArticle() {
        // 准备：创建一篇文章关联多个分类
        Article article = createTestArticleWithCategories(
                "多分类文章",
                List.of(javaCategory, springCategory)
        );

        List<Category> categories = categoryRepository.findByArticle(article);

        assertThat(categories).hasSize(2);
        assertThat(categories)
                .extracting(Category::getName)
                .containsExactlyInAnyOrder("Java", "Spring");
    }

    @Test
    @DisplayName("分类父子关系完整性测试")
    void categoryParentChildIntegrity() {
        // 验证父子关系
        assertThat(javaCategory.getParentCategory()).isEqualTo(techCategory);
        assertThat(springCategory.getParentCategory()).isEqualTo(techCategory);

        // 验证父分类能获取到子分类
        Category foundTech = entityManager.find(Category.class, techCategory.getId());
        assertThat(foundTech.getSubCategories())
                .hasSize(2)
                .extracting(Category::getName)
                .containsExactlyInAnyOrder("Java", "Spring");
    }

    @Test
    @DisplayName("分类名称唯一性测试（如果业务要求）")
    void categoryNameUniqueConstraint() {
        Category duplicateCategory = Category.builder()
                .name("Java")  // 同名分类
                .parentCategory(techCategory)
                .build();

        // 如果数据库有唯一约束，这里会抛异常
        assertThatThrownBy(() -> {
            entityManager.persistAndFlush(duplicateCategory);
        }).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("删除父分类时的行为测试（需要根据业务决定）")
    void deleteParentCategory() {
        // 注意：这个测试需要根据你的级联策略来调整
        // 这里只是演示如何测试删除行为

        Long techId = techCategory.getId();
        Long javaId = javaCategory.getId();

        // 删除父分类
        entityManager.remove(techCategory);
        entityManager.flush();

        // 验证父分类已删除
        assertThat(entityManager.find(Category.class, techId)).isNull();

        // 验证子分类的行为（根据CascadeType决定）
        // 如果设置了cascade，子分类也会被删除
        // 如果没设置，子分类的parent变为null
        Category foundJava = entityManager.find(Category.class, javaId);
        if (foundJava != null) {
            assertThat(foundJava.getParentCategory()).isNull();
        }
    }

    // 辅助方法
    private Article createTestArticleWithCategories(String title, List<Category> categories) {
        Article article = Article.builder()
                .title(title)
                .content(title + "的内容")
                .author(testAuthor)
                .build();

        // 使用辅助方法建立双向关系
        categories.forEach(article::addCategory);

        entityManager.persist(article);
        entityManager.flush();

        return entityManager.find(Article.class, article.getId());
    }

    @Test
    @DisplayName("批量查询分类的文章数")
    void batchCategoryStats() {
        // 创建多篇文章
        createTestArticleWithCategories("文章1", List.of(javaCategory));
        createTestArticleWithCategories("文章2", List.of(javaCategory, springCategory));
        createTestArticleWithCategories("文章3", List.of(springCategory));

        List<Object[]> results = categoryRepository.findAllWithArticleCount();

        // 打印统计结果
        System.out.println("\n=== 分类文章统计 ===");
        results.forEach(row -> {
            Category cat = (Category) row[0];
            Long count = (Long) row[1];
            System.out.printf("分类: %-10s 文章数: %d%n", cat.getName(), count);
        });

        // 验证统计正确性
        results.forEach(row -> {
            Category cat = (Category) row[0];
            Long count = (Long) row[1];

            if (cat.getName().equals("技术")) {
                assertThat(count).isEqualTo(0);
            } else if (cat.getName().equals("Java")) {
                assertThat(count).isEqualTo(2);
            } else if (cat.getName().equals("Spring")) {
                assertThat(count).isEqualTo(2);
            }
        });
    }
}