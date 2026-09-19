package csulzc.My_Personal_Blogger.service;

import csulzc.My_Personal_Blogger.api.dto.category.*;
import csulzc.My_Personal_Blogger.domain.entity.*;
import csulzc.My_Personal_Blogger.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(CategoryService.class)
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

        // 创建顶级分类 - 技术
        techCategory = Category.builder()
                .name("技术")
                .description("技术相关分类")
                .build();
        entityManager.persist(techCategory);
        techCategoryId = techCategory.getId();

        // 创建子分类 - Java
        javaCategory = Category.builder()
                .name("Java")
                .description("Java 编程语言")
                .parentCategory(techCategory)
                .build();
        entityManager.persist(javaCategory);
        javaCategoryId = javaCategory.getId();

        // 创建子分类 - Spring
        springCategory = Category.builder()
                .name("Spring")
                .description("Spring 框架")
                .parentCategory(techCategory)
                .build();
        entityManager.persist(springCategory);

        // 准备 DTO 对象
        categoryRequest = CategoryRequest.builder()
                .name("Python")
                .description("Python 编程语言")
                .parentCategoryId(techCategoryId)
                .build();

        updateRequest = CategoryRequest.builder()
                .name("Java 编程")
                .description("更新后的 Java 描述")
                .parentCategoryId(techCategoryId)
                .build();

        entityManager.flush();
        entityManager.clear();
    }

    @AfterEach
    void tearDown() {
        entityManager.clear();
    }

    @Test
    @Order(1)
    @DisplayName("测试创建分类 - 成功")
    void testCreateCategory_Success() {
        // Given - 准备数据（已在 setUp 中准备）

        // When - 执行创建操作
        CategoryDTO result = categoryService.createCategory(categoryRequest);

        // Then - 验证结果
        assertNotNull(result);
        assertEquals("Python", result.getName());
        assertEquals("Python 编程语言", result.getDescription());
        assertEquals(0, result.getArticleCount());
        assertNotNull(result.getParentCategoryId());
        assertEquals("技术", result.getParentCategoryName());
    }

    @Test
    @Order(2)
    @DisplayName("测试创建分类 - 名称重复")
    void testCreateCategory_DuplicateName() {
        // Given - 使用已存在的分类名称
        CategoryRequest duplicateRequest = CategoryRequest.builder()
                .name("技术")
                .description("重复的分类")
                .build();

        // When & Then - 应该抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            categoryService.createCategory(duplicateRequest);
        });
    }

    @Test
    @Order(3)
    @DisplayName("测试创建分类 - 父分类不存在")
    void testCreateCategory_ParentNotFound() {
        // Given - 使用不存在的父分类 ID
        CategoryRequest invalidRequest = CategoryRequest.builder()
                .name("新分类")
                .description("描述")
                .parentCategoryId(999L)
                .build();

        // When & Then - 应该抛出异常
        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> {
            categoryService.createCategory(invalidRequest);
        });
    }

    @Test
    @Order(4)
    @DisplayName("测试更新分类 - 成功")
    void testUpdateCategory_Success() {
        // Given
        Long categoryId = javaCategoryId;

        // When - 执行更新操作
        CategoryDTO result = categoryService.updateCategory(categoryId, updateRequest);

        // Then - 验证结果
        assertNotNull(result);
        assertEquals("Java 编程", result.getName());
        assertEquals("更新后的 Java 描述", result.getDescription());
    }

    @Test
    @Order(5)
    @DisplayName("测试更新分类 - 分类不存在")
    void testUpdateCategory_NotFound() {
        // Given
        Long nonExistentId = 999L;

        // When & Then - 应该抛出异常
        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> {
            categoryService.updateCategory(nonExistentId, updateRequest);
        });
    }

    @Test
    @Order(6)
    @DisplayName("测试更新分类 - 名称与其他分类重复")
    void testUpdateCategory_NameConflict() {
        // Given
        Long categoryId = javaCategoryId;
        CategoryRequest conflictRequest = CategoryRequest.builder()
                .name("技术")  // 与现有分类重名
                .description("描述")
                .build();

        // When & Then - 应该抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            categoryService.updateCategory(categoryId, conflictRequest);
        });
    }

    @Test
    @Order(7)
    @DisplayName("测试更新分类 - 循环引用检查")
    void testUpdateCategory_CircularReference() {
        // Given - 尝试将父分类设置为自己的子分类
        CategoryRequest circularRequest = CategoryRequest.builder()
                .name("技术")
                .description("描述")
                .parentCategoryId(javaCategoryId)  // 尝试将技术设为 Java 的子分类
                .build();

        // When & Then - 应该抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            categoryService.updateCategory(techCategoryId, circularRequest);
        });
    }

    @Test
    @Order(8)
    @DisplayName("测试更新分类 - 将自己设置为父分类")
    void testUpdateCategory_SelfAsParent() {
        // Given
        CategoryRequest selfParentRequest = CategoryRequest.builder()
                .name("Java")
                .description("描述")
                .parentCategoryId(javaCategoryId)  // 将自己设为父分类
                .build();

        // When & Then - 应该抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            categoryService.updateCategory(javaCategoryId, selfParentRequest);
        });
    }

    @Test
    @Order(9)
    @DisplayName("测试根据 ID 获取分类详情")
    void testGetCategoryById() {
        // Given
        Long categoryId = techCategoryId;

        // When - 获取分类详情
        CategoryDTO result = categoryService.getCategoryById(categoryId);

        // Then - 验证结果
        assertNotNull(result);
        assertEquals("技术", result.getName());
        assertEquals("技术相关分类", result.getDescription());
    }

    @Test
    @Order(10)
    @DisplayName("测试根据 ID 获取分类 - 不存在")
    void testGetCategoryById_NotFound() {
        // Given
        Long nonExistentId = 999L;

        // When & Then - 应该抛出异常
        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> {
            categoryService.getCategoryById(nonExistentId);
        });
    }

    @Test
    @Order(11)
    @DisplayName("测试根据名称获取分类")
    void testGetCategoryByName() {
        // Given
        String categoryName = "Java";

        // When - 获取分类
        CategoryDTO result = categoryService.getCategoryByName(categoryName);

        // Then - 验证结果
        assertNotNull(result);
        assertEquals("Java", result.getName());
        assertEquals("Java 编程语言", result.getDescription());
    }

    @Test
    @Order(12)
    @DisplayName("测试获取所有顶级分类")
    void testGetAllTopLevelCategories() {
        // When - 获取顶级分类
        List<CategoryDTO> result = categoryService.getAllTopLevelCategories();

        // Then - 验证结果
        assertNotNull(result);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("技术");
        assertThat(result.get(0).getParentCategoryId()).isNull();
    }

    @Test
    @Order(13)
    @DisplayName("测试获取子分类列表")
    void testGetSubCategories() {
        // Given
        Long parentCategoryId = techCategoryId;

        // When - 获取子分类
        List<CategoryDTO> result = categoryService.getSubCategories(parentCategoryId);

        // Then - 验证结果
        assertNotNull(result);
        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(CategoryDTO::getName)
                .containsExactlyInAnyOrder("Java", "Spring");
    }

    @Test
    @Order(14)
    @DisplayName("测试分页查询所有分类")
    void testGetAllCategories() {
        // Given - 创建更多分类
        for (int i = 1; i <= 5; i++) {
            Category category = Category.builder()
                    .name("分类" + i)
                    .description("描述" + i)
                    .build();
            entityManager.persist(category);
        }
        entityManager.flush();

        // When - 分页查询
        var result = categoryService.getAllCategories(0, 3, "createdAt");

        // Then - 验证结果
        assertNotNull(result);
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getTotalElements()).isEqualTo(8); // setUp 中的 3 个 + 新加的 5 个
        assertThat(result.getTotalPages()).isEqualTo(3);
    }

    @Test
    @Order(15)
    @DisplayName("测试构建分类树")
    void testBuildCategoryTree() {
        // When - 构建分类树
        List<CategoryTreeDTO> tree = categoryService.buildCategoryTree();

        // Then - 验证结果
        assertNotNull(tree);
        assertThat(tree).hasSize(1); // 只有一个顶级分类

        CategoryTreeDTO techNode = tree.get(0);
        assertEquals("技术", techNode.getName());
        assertThat(techNode.getChildren()).hasSize(2);
        assertThat(techNode.getChildren())
                .extracting(CategoryTreeDTO::getName)
                .containsExactlyInAnyOrder("Java", "Spring");
    }

    @Test
    @Order(16)
    @DisplayName("测试获取分类路径")
    void testGetCategoryPath() {
        // Given
        Long categoryId = javaCategoryId;

        // When - 获取从根到当前分类的路径
        List<CategoryDTO> path = categoryService.getCategoryPath(categoryId);

        // Then - 验证结果
        assertNotNull(path);
        assertThat(path).hasSize(2);
        assertEquals("技术", path.get(0).getName());
        assertEquals("Java", path.get(1).getName());
    }

    @Test
    @Order(17)
    @DisplayName("测试获取分类统计信息")
    void testGetCategoryStatistics() {
        // When - 获取统计信息
        List<CategoryStatDTO> stats = categoryService.getCategoryStatistics();

        // Then - 验证结果
        assertNotNull(stats);
        assertThat(stats).hasSize(3); // 技术、Java、Spring
        stats.forEach(stat -> {
            assertThat(stat.getCategoryName()).isNotNull();
            assertThat(stat.getArticleCount()).isNotNull();
        });
    }

    @Test
    @Order(18)
    @DisplayName("测试计算包含子分类的文章数量")
    void testCountArticlesInCategoryIncludingSubCategories() {
        // Given - 为 Java 分类添加文章
        Article article1 = Article.builder()
                .title("Java 文章1")
                .content("内容1")
                .author(testUser)
                .build();
        article1.addCategory(javaCategory);
        entityManager.persist(article1);

        Article article2 = Article.builder()
                .title("Spring 文章1")
                .content("内容2")
                .author(testUser)
                .build();
        article2.addCategory(springCategory);
        entityManager.persist(article2);
        entityManager.flush();

        // When - 计算技术分类的文章数（应包含子分类的文章）
        long count = categoryService.countArticlesInCategoryIncludingSubCategories(techCategoryId);

        // Then - 验证结果
        assertThat(count).isEqualTo(2);
    }

    @Test
    @Order(19)
    @DisplayName("测试获取分类占比统计")
    void testGetCategoryPercentageStats() {
        // Given - 为分类添加文章
        Article article = Article.builder()
                .title("测试文章")
                .content("内容")
                .author(testUser)
                .build();
        article.addCategory(javaCategory);
        entityManager.persist(article);
        entityManager.flush();

        // When - 获取占比统计
        List<CategoryStatDTO> stats = categoryService.getCategoryPercentageStats();

        // Then - 验证结果
        assertNotNull(stats);
        assertThat(stats).hasSize(3);

        // 验证百分比计算
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
        // Given - 创建一个没有文章的分类
        Category tempCategory = Category.builder()
                .name("临时分类")
                .description("待删除")
                .build();
        entityManager.persist(tempCategory);
        entityManager.flush();
        Long categoryId = tempCategory.getId();

        // When - 执行删除操作
        categoryService.deleteCategory(categoryId);

        // Then - 验证分类已被删除
        Optional<Category> deleted = categoryRepository.findById(categoryId);
        assertThat(deleted).isEmpty();
    }

    @Test
    @Order(21)
    @DisplayName("测试删除分类 - 分类不存在")
    void testDeleteCategory_NotFound() {
        // Given
        Long nonExistentId = 999L;

        // When & Then - 应该抛出异常
        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> {
            categoryService.deleteCategory(nonExistentId);
        });
    }

    @Test
    @Order(22)
    @DisplayName("测试删除分类 - 有文章关联")
    void testDeleteCategory_WithArticles() {
        // Given - 创建有关联文章的分类
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

        // When & Then - 应该抛出异常
        assertThrows(IllegalStateException.class, () -> {
            categoryService.deleteCategory(categoryId);
        });
    }

    @Test
    @Order(23)
    @DisplayName("测试删除分类 - 有子分类")
    void testDeleteCategory_WithSubCategories() {
        // Given
        Long categoryId = techCategoryId;

        // When & Then - 应该抛出异常
        assertThrows(IllegalStateException.class, () -> {
            categoryService.deleteCategory(categoryId);
        });
    }

    @Test
    @Order(24)
    @DisplayName("测试删除分类并转移文章")
    void testDeleteCategoryAndTransferArticles() {
        // Given - 创建源分类和目标分类
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

        // 创建文章并关联到源分类
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

        // When - 删除源分类并转移文章
        categoryService.deleteCategoryAndTransferArticles(sourceId, targetId);

        // Then - 验证源分类已删除，文章转移到目标分类
        assertThat(categoryRepository.findById(sourceId)).isEmpty();

        Category updatedTarget = entityManager.find(Category.class, targetId);
        assertThat(updatedTarget.getArticles()).hasSize(1);
    }

    @Test
    @Order(25)
    @DisplayName("测试删除分类并移除文章关联")
    void testDeleteCategoryAndRemoveAssociation() {
        // Given - 创建有文章的分类
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

        // When - 删除分类但不转移文章（targetCategoryId = null）
        categoryService.deleteCategoryAndTransferArticles(sourceId, null);

        // Then - 验证分类已删除，文章不再有关联
        assertThat(categoryRepository.findById(sourceId)).isEmpty();

        Article updatedArticle = entityManager.find(Article.class, articleId);
        assertThat(updatedArticle.getCategories()).isEmpty();
    }

    @Test
    @Order(26)
    @DisplayName("测试搜索分类")
    void testSearchCategories() {
        // Given - 创建多个分类
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

        // When - 搜索包含"语言"的分类
        List<CategoryDTO> result = categoryService.searchCategories("语言");

        // Then - 验证结果
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
        // When - 使用空关键字搜索
        List<CategoryDTO> result = categoryService.searchCategories("");

        // Then - 返回空列表
        assertThat(result).isEmpty();
    }

    @Test
    @Order(28)
    @DisplayName("测试获取有文章的分类列表")
    void testGetCategoriesWithArticles() {
        // Given - 为 Java 分类添加文章
        Article article = Article.builder()
                .title("Java 文章")
                .content("内容")
                .author(testUser)
                .build();
        article.addCategory(javaCategory);
        entityManager.persist(article);
        entityManager.flush();

        // When - 获取有文章的分类
        List<CategoryDTO> result = categoryService.getCategoriesWithArticles();

        // Then - 验证结果
        assertNotNull(result);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Java");
    }

    @Test
    @Order(29)
    @DisplayName("测试检查分类名称是否存在")
    void testExistsByName() {
        // When & Then
        assertTrue(categoryService.existsByName("技术"));
        assertFalse(categoryService.existsByName("不存在的分类"));
    }

    @Test
    @Order(30)
    @DisplayName("测试检查分类名称是否存在 - 排除指定 ID")
    void testExistsByName_ExcludeId() {
        // When & Then - 排除自身 ID 后，名称不应存在
        assertFalse(categoryService.existsByName("技术", techCategoryId));

        // 其他分类使用该名称时，应返回 true
        assertTrue(categoryService.existsByName("技术", javaCategoryId));
    }

    @Test
    @Order(31)
    @DisplayName("测试获取分类总数")
    void testGetTotalCategoryCount() {
        // When - 获取总数
        long count = categoryService.getTotalCategoryCount();

        // Then - 验证结果
        assertThat(count).isEqualTo(3); // 技术、Java、Spring
    }

    @Test
    @Order(32)
    @DisplayName("测试获取顶级分类数量")
    void testGetTopLevelCategoryCount() {
        // When - 获取顶级分类数量
        long count = categoryService.getTopLevelCategoryCount();

        // Then - 验证结果
        assertThat(count).isEqualTo(1); // 只有"技术"是顶级分类
    }
}
