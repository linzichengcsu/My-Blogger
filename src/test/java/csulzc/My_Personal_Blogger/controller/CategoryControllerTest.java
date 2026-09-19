package csulzc.My_Personal_Blogger.controller;

import tools.jackson.databind.ObjectMapper;
import csulzc.My_Personal_Blogger.api.dto.category.*;
import csulzc.My_Personal_Blogger.api.dto.common.PageResponseDTO;
import csulzc.My_Personal_Blogger.service.CategoryService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CategoryController.class)
@DisplayName("CategoryController 测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import(CategoryControllerTest.TestSecurityConfig.class)
class CategoryControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        @Primary
        public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .anyRequest().permitAll()
                    );
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CategoryService categoryService;

    private CategoryRequest createRequest;
    private CategoryDTO categoryDTO;
    private CategoryTreeDTO categoryTreeDTO;
    private CategoryStatDTO categoryStatDTO;

    @BeforeEach
    void setUp() {
        createRequest = CategoryRequest.builder()
                .name("Java编程")
                .description("Java相关技术文章")
                .parentCategoryId(null)
                .build();

        categoryDTO = CategoryDTO.builder()
                .id(1L)
                .name("Java编程")
                .description("Java相关技术文章")
                .parentCategoryId(null)
                .parentCategoryName(null)
                .articleCount(5)
                .subCategories(Collections.emptyList())
                .build();

        categoryTreeDTO = CategoryTreeDTO.builder()
                .id(1L)
                .name("Java编程")
                .description("Java相关技术文章")
                .articleCount(5)
                .children(Collections.emptyList())
                .build();

        categoryStatDTO = CategoryStatDTO.builder()
                .categoryName("Java编程")
                .articleCount(5L)
                .percentage(25.0)
                .build();
    }

    @Test
    @Order(1)
    @DisplayName("测试创建分类 - 成功")
    void testCreateCategory_Success() throws Exception {
        given(categoryService.createCategory(any(CategoryRequest.class)))
                .willReturn(categoryDTO);

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("分类创建成功"))
                .andExpect(jsonPath("$.data.name").value("Java编程"))
                .andExpect(jsonPath("$.data.description").value("Java相关技术文章"));

        then(categoryService).should().createCategory(any(CategoryRequest.class));
    }

    @Test
    @Order(2)
    @DisplayName("测试创建分类 - 参数验证失败（名称过短）")
    void testCreateCategory_ValidationFailed_ShortName() throws Exception {
        CategoryRequest invalidRequest = CategoryRequest.builder()
                .name("J")
                .description("Java相关技术文章")
                .build();

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(3)
    @DisplayName("测试创建分类 - 参数验证失败（名称为空）")
    void testCreateCategory_ValidationFailed_EmptyName() throws Exception {
        CategoryRequest invalidRequest = CategoryRequest.builder()
                .name("")
                .description("Java相关技术文章")
                .build();

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(4)
    @DisplayName("测试更新分类 - 成功")
    void testUpdateCategory_Success() throws Exception {
        Long categoryId = 1L;
        CategoryRequest updateRequest = CategoryRequest.builder()
                .name("Java高级编程")
                .description("Java高级技术文章")
                .build();

        CategoryDTO updatedCategory = CategoryDTO.builder()
                .id(categoryId)
                .name("Java高级编程")
                .description("Java高级技术文章")
                .articleCount(5)
                .build();

        given(categoryService.updateCategory(eq(categoryId), any(CategoryRequest.class)))
                .willReturn(updatedCategory);

        mockMvc.perform(put("/api/categories/{categoryId}", categoryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("分类更新成功"))
                .andExpect(jsonPath("$.data.name").value("Java高级编程"));

        then(categoryService).should().updateCategory(eq(categoryId), any(CategoryRequest.class));
    }

    @Test
    @Order(5)
    @DisplayName("测试更新分类 - 无效ID")
    void testUpdateCategory_InvalidId() throws Exception {
        mockMvc.perform(put("/api/categories/{categoryId}", -1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(6)
    @DisplayName("测试获取分类详情 - 通过ID成功")
    void testGetCategoryById_Success() throws Exception {
        Long categoryId = 1L;
        given(categoryService.getCategoryById(eq(categoryId)))
                .willReturn(categoryDTO);

        mockMvc.perform(get("/api/categories/{categoryId}", categoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(categoryId))
                .andExpect(jsonPath("$.data.name").value("Java编程"));

        then(categoryService).should().getCategoryById(eq(categoryId));
    }

    @Test
    @Order(7)
    @DisplayName("测试获取分类详情 - 无效ID")
    void testGetCategoryById_InvalidId() throws Exception {
        mockMvc.perform(get("/api/categories/{categoryId}", -1))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(8)
    @DisplayName("测试获取分类详情 - 通过名称成功")
    void testGetCategoryByName_Success() throws Exception {
        String name = "Java编程";
        given(categoryService.getCategoryByName(eq(name)))
                .willReturn(categoryDTO);

        mockMvc.perform(get("/api/categories/name/{name}", name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("Java编程"));

        then(categoryService).should().getCategoryByName(eq(name));
    }

    @Test
    @Order(9)
    @DisplayName("测试获取分类详情 - 名称不存在")
    void testGetCategoryByName_NotFound() throws Exception {
        String name = "不存在的分类";
        given(categoryService.getCategoryByName(eq(name)))
                .willThrow(new jakarta.persistence.EntityNotFoundException("分类不存在"));

        mockMvc.perform(get("/api/categories/name/{name}", name))
                .andExpect(status().isNotFound());

        then(categoryService).should().getCategoryByName(eq(name));
    }

    @Test
    @Order(10)
    @DisplayName("测试获取所有顶级分类 - 成功")
    void testGetAllTopLevelCategories_Success() throws Exception {
        List<CategoryDTO> categories = Collections.singletonList(categoryDTO);
        given(categoryService.getAllTopLevelCategories()).willReturn(categories);

        mockMvc.perform(get("/api/categories/top-level"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("Java编程"));

        then(categoryService).should().getAllTopLevelCategories();
    }

    @Test
    @Order(11)
    @DisplayName("测试获取子分类列表 - 成功")
    void testGetSubCategories_Success() throws Exception {
        Long parentCategoryId = 1L;
        List<CategoryDTO> subCategories = Collections.singletonList(categoryDTO);
        given(categoryService.getSubCategories(eq(parentCategoryId))).willReturn(subCategories);

        mockMvc.perform(get("/api/categories/{categoryId}/subcategories", parentCategoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("Java编程"));

        then(categoryService).should().getSubCategories(eq(parentCategoryId));
    }

    @Test
    @Order(12)
    @DisplayName("测试获取子分类列表 - 无效ID")
    void testGetSubCategories_InvalidId() throws Exception {
        mockMvc.perform(get("/api/categories/{categoryId}/subcategories", -1))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(13)
    @DisplayName("测试获取所有分类（分页） - 成功")
    void testGetAllCategories_Success() throws Exception {
        PageResponseDTO<CategoryDTO> pageResponse = PageResponseDTO.<CategoryDTO>builder()
                .content(Collections.singletonList(categoryDTO))
                .page(0)
                .size(10)
                .totalElements(1L)
                .totalPages(1)
                .build();

        given(categoryService.getAllCategories(eq(0), eq(10), eq("createdAt")))
                .willReturn(pageResponse);

        mockMvc.perform(get("/api/categories")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sortBy", "createdAt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10));

        then(categoryService).should().getAllCategories(eq(0), eq(10), eq("createdAt"));
    }

    @Test
    @Order(14)
    @DisplayName("测试获取所有分类 - 页码为负数")
    void testGetAllCategories_NegativePage() throws Exception {
        mockMvc.perform(get("/api/categories")
                        .param("page", "-1")
                        .param("size", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(15)
    @DisplayName("测试获取所有分类 - 每页大小超限")
    void testGetAllCategories_SizeExceedsLimit() throws Exception {
        mockMvc.perform(get("/api/categories")
                        .param("page", "0")
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(16)
    @DisplayName("测试获取分类树 - 成功")
    void testBuildCategoryTree_Success() throws Exception {
        List<CategoryTreeDTO> tree = Collections.singletonList(categoryTreeDTO);
        given(categoryService.buildCategoryTree()).willReturn(tree);

        mockMvc.perform(get("/api/categories/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("Java编程"))
                .andExpect(jsonPath("$.data[0].articleCount").value(5));

        then(categoryService).should().buildCategoryTree();
    }

    @Test
    @Order(17)
    @DisplayName("测试获取分类路径 - 成功")
    void testGetCategoryPath_Success() throws Exception {
        Long categoryId = 1L;
        List<CategoryDTO> path = Collections.singletonList(categoryDTO);
        given(categoryService.getCategoryPath(eq(categoryId))).willReturn(path);

        mockMvc.perform(get("/api/categories/{categoryId}/path", categoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("Java编程"));

        then(categoryService).should().getCategoryPath(eq(categoryId));
    }

    @Test
    @Order(18)
    @DisplayName("测试获取分类路径 - 无效ID")
    void testGetCategoryPath_InvalidId() throws Exception {
        mockMvc.perform(get("/api/categories/{categoryId}/path", -1))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(19)
    @DisplayName("测试获取分类统计 - 成功")
    void testGetCategoryStatistics_Success() throws Exception {
        List<CategoryStatDTO> stats = Collections.singletonList(categoryStatDTO);
        given(categoryService.getCategoryStatistics()).willReturn(stats);

        mockMvc.perform(get("/api/categories/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].categoryName").value("Java编程"))
                .andExpect(jsonPath("$.data[0].articleCount").value(5));

        then(categoryService).should().getCategoryStatistics();
    }

    @Test
    @Order(20)
    @DisplayName("测试获取分类占比统计 - 成功")
    void testGetCategoryPercentageStats_Success() throws Exception {
        List<CategoryStatDTO> stats = Collections.singletonList(categoryStatDTO);
        given(categoryService.getCategoryPercentageStats()).willReturn(stats);

        mockMvc.perform(get("/api/categories/statistics/percentage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].percentage").value(25.0));

        then(categoryService).should().getCategoryPercentageStats();
    }

    @Test
    @Order(21)
    @DisplayName("测试计算分类文章数 - 成功")
    void testCountArticlesInCategoryIncludingSubCategories_Success() throws Exception {
        Long categoryId = 1L;
        long count = 10L;
        given(categoryService.countArticlesInCategoryIncludingSubCategories(eq(categoryId)))
                .willReturn(count);

        mockMvc.perform(get("/api/categories/{categoryId}/article-count", categoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(10));

        then(categoryService).should().countArticlesInCategoryIncludingSubCategories(eq(categoryId));
    }

    @Test
    @Order(22)
    @DisplayName("测试计算分类文章数 - 无效ID")
    void testCountArticlesInCategoryIncludingSubCategories_InvalidId() throws Exception {
        mockMvc.perform(get("/api/categories/{categoryId}/article-count", -1))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(23)
    @DisplayName("测试搜索分类 - 成功")
    void testSearchCategories_Success() throws Exception {
        String keyword = "Java";
        List<CategoryDTO> categories = Collections.singletonList(categoryDTO);
        given(categoryService.searchCategories(eq(keyword))).willReturn(categories);

        mockMvc.perform(get("/api/categories/search")
                        .param("keyword", keyword))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("Java编程"));

        then(categoryService).should().searchCategories(eq(keyword));
    }

    @Test
    @Order(24)
    @DisplayName("测试搜索分类 - 关键词为空")
    void testSearchCategories_EmptyKeyword() throws Exception {
        mockMvc.perform(get("/api/categories/search")
                        .param("keyword", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(25)
    @DisplayName("测试获取有文章的分类 - 成功")
    void testGetCategoriesWithArticles_Success() throws Exception {
        List<CategoryDTO> categories = Collections.singletonList(categoryDTO);
        given(categoryService.getCategoriesWithArticles()).willReturn(categories);

        mockMvc.perform(get("/api/categories/with-articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].articleCount").value(5));

        then(categoryService).should().getCategoriesWithArticles();
    }

    @Test
    @Order(26)
    @DisplayName("测试删除分类 - 成功")
    void testDeleteCategory_Success() throws Exception {
        Long categoryId = 1L;
        doNothing().when(categoryService).deleteCategory(eq(categoryId));

        mockMvc.perform(delete("/api/categories/{categoryId}", categoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("分类删除成功"));

        then(categoryService).should().deleteCategory(eq(categoryId));
    }

    @Test
    @Order(27)
    @DisplayName("测试删除分类 - 无效ID")
    void testDeleteCategory_InvalidId() throws Exception {
        mockMvc.perform(delete("/api/categories/{categoryId}", -1))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(28)
    @DisplayName("测试删除分类并转移文章 - 成功")
    void testDeleteCategoryAndTransferArticles_Success() throws Exception {
        Long categoryId = 1L;
        Long targetCategoryId = 2L;
        doNothing().when(categoryService).deleteCategoryAndTransferArticles(eq(categoryId), eq(targetCategoryId));

        mockMvc.perform(delete("/api/categories/{categoryId}/transfer", categoryId)
                        .param("targetCategoryId", String.valueOf(targetCategoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("分类删除成功，文章已转移"));

        then(categoryService).should().deleteCategoryAndTransferArticles(eq(categoryId), eq(targetCategoryId));
    }

    @Test
    @Order(29)
    @DisplayName("测试删除分类并转移文章 - 无效ID")
    void testDeleteCategoryAndTransferArticles_InvalidId() throws Exception {
        mockMvc.perform(delete("/api/categories/{categoryId}/transfer", -1)
                        .param("targetCategoryId", "2"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(30)
    @DisplayName("测试获取分类总数 - 成功")
    void testGetTotalCategoryCount_Success() throws Exception {
        long count = 20L;
        given(categoryService.getTotalCategoryCount()).willReturn(count);

        mockMvc.perform(get("/api/categories/stats/total"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(20));

        then(categoryService).should().getTotalCategoryCount();
    }

    @Test
    @Order(31)
    @DisplayName("测试获取顶级分类数量 - 成功")
    void testGetTopLevelCategoryCount_Success() throws Exception {
        long count = 5L;
        given(categoryService.getTopLevelCategoryCount()).willReturn(count);

        mockMvc.perform(get("/api/categories/stats/top-level"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(5));

        then(categoryService).should().getTopLevelCategoryCount();
    }
}
