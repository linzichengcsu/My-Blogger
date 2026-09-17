package csulzc.My_Personal_Blogger.controller;

import csulzc.My_Personal_Blogger.api.dto.category.*;
import csulzc.My_Personal_Blogger.api.dto.common.PageResponseDTO;
import csulzc.My_Personal_Blogger.api.response.Result;
import csulzc.My_Personal_Blogger.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "收藏夹管理", description = "收藏夹CRUD及父子类关系相关接口")
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * 创建收藏夹
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "创建收藏夹", description = "创建一个新的收藏夹")
    public ResponseEntity<Result<CategoryDTO>> createCategory(
            @Valid @RequestBody CategoryRequest request) {
        CategoryDTO category = categoryService.createCategory(request);
        return ResponseEntity.ok(Result.success(category, "收藏夹创建成功"));
    }

    /**
     * 更新收藏夹
     */
    @PutMapping("/{categoryId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "更新收藏夹", description = "更新指定ID的收藏夹")
    public ResponseEntity<Result<CategoryDTO>> updateCategory(
            @PathVariable Long categoryId,
            @Valid @RequestBody CategoryRequest request) {
        if (categoryId == null || categoryId <= 0) {
            throw new IllegalArgumentException("收藏夹ID无效");
        }
        CategoryDTO category = categoryService.updateCategory(categoryId, request);
        return ResponseEntity.ok(Result.success(category, "收藏夹更新成功"));
    }

    /**
     * 获取收藏夹详情（通过ID）
     */
    @GetMapping("/{categoryId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "获取收藏夹详情", description = "获取指定ID的收藏夹详情")
    public ResponseEntity<Result<CategoryDTO>> getCategoryById(@PathVariable Long categoryId) {
        if (categoryId == null || categoryId <= 0) {
            throw new IllegalArgumentException("收藏夹ID无效");
        }
        CategoryDTO category = categoryService.getCategoryById(categoryId);
        return ResponseEntity.ok(Result.success(category));
    }

    /**
     * 获取收藏夹详情（通过名称）
     */
    @GetMapping("/name/{name}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "获取收藏夹详情", description = "获取指定名称的收藏夹详情")
    public ResponseEntity<Result<CategoryDTO>> getCategoryByName(@PathVariable String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("收藏夹名称不能为空");
        }
        CategoryDTO category = categoryService.getCategoryByName(name);
        return ResponseEntity.ok(Result.success(category));
    }

    /**
     * 获取所有顶级收藏夹
     */
    @GetMapping("/top-level")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "获取所有顶级收藏夹", description = "获取所有顶级收藏夹列表")
    public ResponseEntity<Result<List<CategoryDTO>>> getAllTopLevelCategories() {
        List<CategoryDTO> categories = categoryService.getAllTopLevelCategories();
        return ResponseEntity.ok(Result.success(categories));
    }

    /**
     * 获取某个收藏夹的所有子收藏夹
     */
    @GetMapping("/{categoryId}/subcategories")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "获取子收藏夹", description = "获取指定收藏夹的所有子收藏夹")
    public ResponseEntity<Result<List<CategoryDTO>>> getSubCategories(@PathVariable Long categoryId) {
        if (categoryId == null || categoryId <= 0) {
            throw new IllegalArgumentException("收藏夹ID无效");
        }
        List<CategoryDTO> subCategories = categoryService.getSubCategories(categoryId);
        return ResponseEntity.ok(Result.success(subCategories));
    }

    /**
     * 获取所有收藏夹（分页）
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "获取所有收藏夹", description = "获取所有收藏夹列表，分页显示")
    public ResponseEntity<Result<PageResponseDTO<CategoryDTO>>> getAllCategories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy) {
        if (page < 0) {
            throw new IllegalArgumentException("页码不能为负数");
        }
        if (size <= 0 || size > 100) {
            throw new IllegalArgumentException("每页大小必须在1-100之间");
        }
        PageResponseDTO<CategoryDTO> categories = categoryService.getAllCategories(page, size, sortBy);
        return ResponseEntity.ok(Result.success(categories));
    }

    /**
     * 获取收藏夹树（用于前端下拉选择器）
     */
    @GetMapping("/tree")
    @Operation(summary = "获取收藏夹树", description = "获取收藏夹树结构，用于前端下拉选择器")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Result<List<CategoryTreeDTO>>> buildCategoryTree() {
        List<CategoryTreeDTO> tree = categoryService.buildCategoryTree();
        return ResponseEntity.ok(Result.success(tree));
    }

    /**
     * 获取收藏夹的完整路径（从根到当前收藏夹）
     */
    @GetMapping("/{categoryId}/path")
    @Operation(summary = "获取收藏夹路径", description = "获取指定收藏夹的完整路径，从根到当前收藏夹")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Result<List<CategoryDTO>>> getCategoryPath(@PathVariable Long categoryId) {
        if (categoryId == null || categoryId <= 0) {
            throw new IllegalArgumentException("收藏夹ID无效");
        }
        List<CategoryDTO> path = categoryService.getCategoryPath(categoryId);
        return ResponseEntity.ok(Result.success(path));
    }

    /**
     * 获取所有收藏夹及其文章数量统计
     */
    @Operation(summary = "获取收藏夹统计", description = "获取所有收藏夹及其文章数量统计")
    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Result<List<CategoryStatDTO>>> getCategoryStatistics() {
        List<CategoryStatDTO> stats = categoryService.getCategoryStatistics();
        return ResponseEntity.ok(Result.success(stats));
    }

    /**
     * 获取收藏夹的文章占比统计
     */
    @Operation(summary = "获取收藏夹占比统计", description = "获取所有收藏夹的文章占比统计")
    @GetMapping("/statistics/percentage")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Result<List<CategoryStatDTO>>> getCategoryPercentageStats() {
        List<CategoryStatDTO> stats = categoryService.getCategoryPercentageStats();
        return ResponseEntity.ok(Result.success(stats));
    }

    /**
     * 计算收藏夹的文章数量（包含子收藏夹）
     */
    @Operation(summary = "获取收藏夹文章数量", description = "获取指定收藏夹的文章数量，包含子收藏夹")
    @GetMapping("/{categoryId}/article-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<Long>> countArticlesInCategoryIncludingSubCategories(
            @PathVariable Long categoryId) {
        if (categoryId == null || categoryId <= 0) {
            throw new IllegalArgumentException("收藏夹ID无效");
        }
        long count = categoryService.countArticlesInCategoryIncludingSubCategories(categoryId);
        return ResponseEntity.ok(Result.success(count));
    }

    /**
     * 搜索收藏夹
     */
    @Operation(summary = "搜索收藏夹", description = "根据关键词搜索收藏夹")
    @GetMapping("/search")
    public ResponseEntity<Result<List<CategoryDTO>>> searchCategories(
            @RequestParam String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("搜索关键词不能为空");
        }
        List<CategoryDTO> categories = categoryService.searchCategories(keyword);
        return ResponseEntity.ok(Result.success(categories));
    }

    /**
     * 获取有文章的收藏夹列表
     */
    @Operation(summary = "获取有文章的收藏夹", description = "获取所有有文章的收藏夹列表")
    @GetMapping("/with-articles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Result<List<CategoryDTO>>> getCategoriesWithArticles() {
        List<CategoryDTO> categories = categoryService.getCategoriesWithArticles();
        return ResponseEntity.ok(Result.success(categories));
    }

    /**
     * 删除收藏夹
     */
    @Operation(summary = "删除收藏夹", description = "删除指定ID的收藏夹")
    @DeleteMapping("/{categoryId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<Void>> deleteCategory(@PathVariable Long categoryId) {
        if (categoryId == null || categoryId <= 0) {
            throw new IllegalArgumentException("收藏夹ID无效");
        }
        categoryService.deleteCategory(categoryId);
        return ResponseEntity.ok(Result.success(null, "收藏夹删除成功"));
    }

    /**
     * 删除收藏夹并转移文章
     */
    @Operation(summary = "删除收藏夹并转移文章", description = "删除指定ID的收藏夹，并将文章转移至目标收藏夹")
    @DeleteMapping("/{categoryId}/transfer")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<Void>> deleteCategoryAndTransferArticles(
            @PathVariable Long categoryId,
            @RequestParam(required = false) Long targetCategoryId) {
        if (categoryId == null || categoryId <= 0) {
            throw new IllegalArgumentException("收藏夹ID无效");
        }
        categoryService.deleteCategoryAndTransferArticles(categoryId, targetCategoryId);
        return ResponseEntity.ok(Result.success(null, "收藏夹删除成功，文章已转移"));
    }

    /**
     * 获取收藏夹总数
     */
    @Operation(summary = "获取收藏夹总数", description = "获取收藏夹的总数")
    @GetMapping("/stats/total")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Result<Long>> getTotalCategoryCount() {
        long count = categoryService.getTotalCategoryCount();
        return ResponseEntity.ok(Result.success(count));
    }

    /**
     * 获取顶级收藏夹数量
     */
    @Operation(summary = "获取顶级收藏夹数量", description = "获取顶级收藏夹的数量")
    @GetMapping("/stats/top-level")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Result<Long>> getTopLevelCategoryCount() {
        long count = categoryService.getTopLevelCategoryCount();
        return ResponseEntity.ok(Result.success(count));
    }
}
