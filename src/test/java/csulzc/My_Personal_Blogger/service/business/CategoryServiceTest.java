package csulzc.My_Personal_Blogger.service.business;

import csulzc.My_Personal_Blogger.api.dto.category.*;
import csulzc.My_Personal_Blogger.domain.entity.*;
import csulzc.My_Personal_Blogger.repository.*;
import csulzc.My_Personal_Blogger.security.SecurityContextUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import({CategoryService.class, SecurityContextUtil.class})
@DisplayName("CategoryService 测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CategoryServiceTest {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    private CategoryRequest categoryRequest;
    private CategoryRequest updateRequest;

    private User testUser;
    private Category techCategory;
    private Category javaCategory;
    private Category springCategory;
    private Long techCategoryId;
    private Long javaCategoryId;

    @BeforeEach
    void setUp() {
        // 创建测试用户
        testUser = User.builder()
                .username("testuser")
                .email("test@example.com")
                .passwordHash("password123")
                .displayName("测试用户")
                .status(User.UserStatus.ACTIVE)
                .build();
        entityManager.persist(testUser);

        // 创建三个平级分类（无父子关系）
        techCategory = Category.builder()
                .name("技术")
                .description("技术相关分类")
                .build();
        entityManager.persist(techCategory);
        techCategoryId = techCategory.getId();

        javaCategory = Category.builder()
                .name("Java")
                .description("Java 编程语言")
                .build();
        entityManager.persist(javaCategory);
        javaCategoryId = javaCategory.getId();

        springCategory = Category.builder()
                .name("Spring")
                .description("Spring 框架")
                .build();
        entityManager.persist(springCategory);

        // 准备 DTO 对象
        categoryRequest = CategoryRequest.builder()
                .name("Python")
                .description("Python 编程语言")
                .build();

        updateRequest = CategoryRequest.builder()
                .name("Java 编程")
                .description("更新后的 Java 描述")
                .build();

        entityManager.flush();
        entityManager.clear();

        // 收藏夹创建需记录创建者，设置当前登录用户为测试用户
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(testUser.getId(), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        entityManager.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @Order(1)
    @DisplayName("测试创建分类 - 成功")
    void testCreateCategory_Success() {
        CategoryDTO result = categoryService.createCategory(categoryRequest);

        assertNotNull(result);
        assertEquals("Python", result.getName());
        assertEquals("Python 编程语言", result.getDescription());
        assertEquals(0, result.getArticleCount());
    }

    @Test
    @Order(2)
    @DisplayName("测试创建分类 - 名称重复")
    void testCreateCategory_DuplicateName() {
        CategoryRequest duplicateRequest = CategoryRequest.builder()
                .name("技术")
                .description("重复的分类")
                .build();

        assertThrows(IllegalArgumentException.class, () -> {
            categoryService.createCategory(duplicateRequest);
        });
    }

    @Test
    @Order(4)
    @DisplayName("测试更新分类 - 成功")
    void testUpdateCategory_Success() {
        Long categoryId = javaCategoryId;

        CategoryDTO result = categoryService.updateCategory(categoryId, updateRequest);

        assertNotNull(result);
        assertEquals("Java 编程", result.getName());
        assertEquals("更新后的 Java 描述", result.getDescription());
    }

    @Test
    @Order(5)
    @DisplayName("测试更新分类 - 分类不存在")
    void testUpdateCategory_NotFound() {
        Long nonExistentId = 999L;

        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> {
            categoryService.updateCategory(nonExistentId, updateRequest);
        });
    }

    @Test
    @Order(6)
    @DisplayName("测试更新分类 - 名称与其他分类重复")
    void testUpdateCategory_NameConflict() {
        Long categoryId = javaCategoryId;
        CategoryRequest conflictRequest = CategoryRequest.builder()
                .name("技术")  // 与现有分类重名
                .description("描述")
                .build();

        assertThrows(IllegalArgumentException.class, () -> {
            categoryService.updateCategory(categoryId, conflictRequest);
        });
    }

    @Test
    @Order(9)
    @DisplayName("测试根据 ID 获取分类详情")
    void testGetCategoryById() {
        Long categoryId = techCategoryId;

        CategoryDTO result = categoryService.getCategoryById(categoryId);

        assertNotNull(result);
        assertEquals("技术", result.getName());
        assertEquals("技术相关分类", result.getDescription());
    }

    @Test
    @Order(10)
    @DisplayName("测试根据 ID 获取分类 - 不存在")
    void testGetCategoryById_NotFound() {
        Long nonExistentId = 999L;

        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> {
            categoryService.getCategoryById(nonExistentId);
        });
    }

    @Test
    @Order(11)
    @DisplayName("测试根据名称获取分类")
    void testGetCategoryByName() {
        String categoryName = "Java";

        CategoryDTO result = categoryService.getCategoryByName(categoryName);

        assertNotNull(result);
        assertEquals("Java", result.getName());
        assertEquals("Java 编程语言", result.getDescription());
    }

    @Test
    @Order(14)
    @DisplayName("测试分页查询所有分类")
    void testGetAllCategories() {
        // 创建更多分类
        for (int i = 1; i <= 5; i++) {
            Category category = Category.builder()
                    .name("分类" + i)
                    .description("描述" + i)
                    .build();
            entityManager.persist(category);
        }
        entityManager.flush();

        var result = categoryService.getAllCategories(0, 3, "createdAt");

        assertNotNull(result);
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getTotalElements()).isEqualTo(8); // setUp 中的 3 个 + 新加的 5 个
        assertThat(result.getTotalPages()).isEqualTo(3);
    }

    @Test
    @Order(17)
    @DisplayName("测试获取分类统计信息")
    void testGetCategoryStatistics() {
        List<CategoryStatDTO> stats = categoryService.getCategoryStatistics();

        assertNotNull(stats);
        assertThat(stats).hasSize(3); // 技术、Java、Spring
        stats.forEach(stat -> {
            assertThat(stat.getCategoryName()).isNotNull();
            assertThat(stat.getArticleCount()).isNotNull();
        });
    }

    @Test
    @Order(19)
    @DisplayName("测试获取分类占比统计")
    void testGetCategoryPercentageStats() {
        Article article = Article.builder()
                .title("测试文章")
                .content("内容")
                .author(testUser)
                .build();
        article.addCategory(javaCategory);
        entityManager.persist(article);
        entityManager.flush();

        List<CategoryStatDTO> stats = categoryService.getCategoryPercentageStats();

        assertNotNull(stats);
        assertThat(stats).hasSize(3);

        Optional<CategoryStatDTO> javaStat = stats.stream()
                .filter(s -> s.getCategoryName().equals("Java"))
                .findFirst();
        assertThat(javaStat).isPresent();
        assertThat(javaStat.get().getArticleCount()).isEqualTo(1);
        assertThat(javaStat.get().getPercentage()).isGreaterThan(0.0);
    }

    @Test
    @Order(20)
    @DisplayName("测试删除分类 - 成功")
    void testDeleteCategory_Success() {
        Category tempCategory = Category.builder()
                .name("临时分类")
                .description("待删除")
                .build();
        entityManager.persist(tempCategory);
        entityManager.flush();
        Long categoryId = tempCategory.getId();

        categoryService.deleteCategory(categoryId);

        Optional<Category> deleted = categoryRepository.findById(categoryId);
        assertThat(deleted).isEmpty();
    }

    @Test
    @Order(21)
    @DisplayName("测试删除分类 - 分类不存在")
    void testDeleteCategory_NotFound() {
        Long nonExistentId = 999L;

        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> {
            categoryService.deleteCategory(nonExistentId);
        });
    }

    @Test
    @Order(22)
    @DisplayName("测试删除分类 - 有文章关联")
    void testDeleteCategory_WithArticles() {
        Category categoryWithArticle = Category.builder()
                .name("有文章的分类")
                .description("描述")
                .build();
        entityManager.persist(categoryWithArticle);

        Article article = Article.builder()
                .title("测试文章")
                .content("内容")
                .author(testUser)
                .build();
        article.addCategory(categoryWithArticle);
        entityManager.persist(article);
        entityManager.flush();

        entityManager.clear();
        Long categoryId = categoryWithArticle.getId();

        assertThrows(IllegalStateException.class, () -> {
            categoryService.deleteCategory(categoryId);
        });
    }

    @Test
    @Order(24)
    @DisplayName("测试删除分类并转移文章")
    void testDeleteCategoryAndTransferArticles() {
        Category sourceCategory = Category.builder()
                .name("源分类")
                .description("将被删除")
                .build();
        entityManager.persist(sourceCategory);

        Category targetCategory = Category.builder()
                .name("目标分类")
                .description("接收文章")
                .build();
        entityManager.persist(targetCategory);

        Article article = Article.builder()
                .title("测试文章")
                .content("内容")
                .author(testUser)
                .build();
        article.addCategory(sourceCategory);
        entityManager.persist(article);
        entityManager.flush();

        entityManager.clear();

        Long sourceId = sourceCategory.getId();
        Long targetId = targetCategory.getId();

        categoryService.deleteCategoryAndTransferArticles(sourceId, targetId);

        assertThat(categoryRepository.findById(sourceId)).isEmpty();

        Category updatedTarget = entityManager.find(Category.class, targetId);
        assertThat(updatedTarget.getArticles()).hasSize(1);
    }

    @Test
    @Order(25)
    @DisplayName("测试删除分类并移除文章关联")
    void testDeleteCategoryAndRemoveAssociation() {
        Category sourceCategory = Category.builder()
                .name("源分类")
                .description("将被删除")
                .build();
        entityManager.persist(sourceCategory);

        Article article = Article.builder()
                .title("测试文章")
                .content("内容")
                .author(testUser)
                .build();
        article.addCategory(sourceCategory);
        entityManager.persist(article);
        entityManager.flush();

        Long sourceId = sourceCategory.getId();
        Long articleId = article.getId();

        categoryService.deleteCategoryAndTransferArticles(sourceId, null);

        assertThat(categoryRepository.findById(sourceId)).isEmpty();

        Article updatedArticle = entityManager.find(Article.class, articleId);
        assertThat(updatedArticle.getCategories()).isEmpty();
    }

    @Test
    @Order(26)
    @DisplayName("测试搜索分类")
    void testSearchCategories() {
        Category pythonCategory = Category.builder()
                .name("Python")
                .description("Python 编程语言")
                .build();
        entityManager.persist(pythonCategory);

        Category webCategory = Category.builder()
                .name("Web 开发")
                .description("前端和后端开发")
                .build();
        entityManager.persist(webCategory);
        entityManager.flush();

        List<CategoryDTO> result = categoryService.searchCategories("语言");

        assertNotNull(result);
        assertThat(result).hasSize(2); // Java 和 Python 都包含"语言"
        assertThat(result)
                .extracting(CategoryDTO::getName)
                .containsExactlyInAnyOrder("Java", "Python");
    }

    @Test
    @Order(27)
    @DisplayName("测试搜索分类 - 空关键字")
    void testSearchCategories_EmptyKeyword() {
        List<CategoryDTO> result = categoryService.searchCategories("");

        assertThat(result).isEmpty();
    }

    @Test
    @Order(28)
    @DisplayName("测试获取有文章的分类列表")
    void testGetCategoriesWithArticles() {
        Article article = Article.builder()
                .title("Java 文章")
                .content("内容")
                .author(testUser)
                .build();
        article.addCategory(javaCategory);
        entityManager.persist(article);
        entityManager.flush();

        List<CategoryDTO> result = categoryService.getCategoriesWithArticles();

        assertNotNull(result);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Java");
    }

    @Test
    @Order(29)
    @DisplayName("测试检查分类名称是否存在")
    void testExistsByName() {
        assertTrue(categoryService.existsByName("技术"));
        assertFalse(categoryService.existsByName("不存在的分类"));
    }

    @Test
    @Order(30)
    @DisplayName("测试检查分类名称是否存在 - 排除指定 ID")
    void testExistsByName_ExcludeId() {
        assertFalse(categoryService.existsByName("技术", techCategoryId));
        assertTrue(categoryService.existsByName("技术", javaCategoryId));
    }

    @Test
    @Order(31)
    @DisplayName("测试获取分类总数")
    void testGetTotalCategoryCount() {
        long count = categoryService.getTotalCategoryCount();

        assertThat(count).isEqualTo(3); // 技术、Java、Spring
    }
}
