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

        // 准备三个平级分类（无父子关系）
        techCategory = Category.builder()
                .name("技术")
                .build();
        entityManager.persist(techCategory);

        javaCategory = Category.builder()
                .name("Java")
                .build();
        entityManager.persist(javaCategory);

        springCategory = Category.builder()
                .name("Spring")
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
    }

    @Test
    @DisplayName("根据名称查询分类 - 不存在")
    void findByName_NotFound() {
        Optional<Category> found = categoryRepository.findByName("Python");

        assertThat(found).isEmpty();
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

        // 验证文章数量（平级：仅统计直接关联的文章）
        results.forEach(row -> {
            Category category = (Category) row[0];
            Long count = (Long) row[1];

            if (category.getName().equals("技术")) {
                assertThat(count).isEqualTo(0);
            } else if (category.getName().equals("Java")) {
                assertThat(count).isEqualTo(2);
            } else if (category.getName().equals("Spring")) {
                assertThat(count).isEqualTo(2);
            }
        });
    }

    @Test
    @DisplayName("查询分类及其文章数量 - 无文章的分类")
    void findAllWithArticleCount_NoArticles() {
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
    @DisplayName("分类名称唯一性测试")
    void categoryNameUniqueConstraint() {
        Category duplicateCategory = Category.builder()
                .name("Java")  // 同名分类
                .build();

        // 数据库有唯一约束，这里会抛异常
        assertThatThrownBy(() -> {
            entityManager.persistAndFlush(duplicateCategory);
        }).isInstanceOf(Exception.class);
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

        // 验证统计正确性（平级：仅统计直接关联）
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
