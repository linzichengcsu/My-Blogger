package csulzc.My_Personal_Blogger.service.business;

import csulzc.My_Personal_Blogger.api.dto.category.*;
import csulzc.My_Personal_Blogger.api.dto.common.PageResponseDTO;
import csulzc.My_Personal_Blogger.domain.entity.Category;
import csulzc.My_Personal_Blogger.repository.CategoryRepository;
import csulzc.My_Personal_Blogger.security.SecurityContextUtil;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final SecurityContextUtil securityContextUtil;

    /** 收藏夹可排序字段白名单，避免无索引/不存在字段排序导致 filesort 或 500 */
    private static final Map<String, String> CATEGORY_SORT_WHITELIST = Map.of(
            "createdAt", "createdAt",
            "updatedAt", "updatedAt",
            "id", "id"
    );

    /**
     * 构建安全的收藏夹排序（非法字段回退到 createdAt）
     */
    private Sort safeSort(String sortBy) {
        return Sort.by(CATEGORY_SORT_WHITELIST.getOrDefault(sortBy, "createdAt")).descending();
    }

    /**
     * 收藏夹查询上下文：一次加载文章数聚合与全量收藏夹映射，
     * 供各查询方法在内存中组装 DTO，避免逐收藏夹懒加载（N+1）。
     */
    private record CategoryContext(
            Map<Long, Long> articleCounts,      // categoryId -> 文章数（含 0）
            Map<Long, Category> categoriesById) { // categoryId -> 收藏夹实体
    }

    /**
     * 加载收藏夹查询上下文（固定 2 条 SQL：文章数聚合 + 收藏夹全量）
     */
    private CategoryContext loadContext() {
        Map<Long, Long> articleCounts = categoryRepository.findAllWithArticleCount().stream()
                .collect(Collectors.toMap(
                        obj -> ((Category) obj[0]).getId(),
                        obj -> ((Number) obj[1]).longValue(),
                        (a, b) -> a));

        List<Category> all = categoryRepository.findAll();

        Map<Long, Category> categoriesById = all.stream()
                .collect(Collectors.toMap(Category::getId, c -> c));

        return new CategoryContext(articleCounts, categoriesById);
    }

    // ==================== 收藏夹创建与更新 ====================

    /**
     * 创建收藏夹
     */
    @CacheEvict(cacheNames = "category:list", allEntries = true)
    @Transactional(timeout = 30)
    public CategoryDTO createCategory(CategoryRequest request) {
        // 检查收藏夹名称是否已存在
        if (categoryRepository.findByName(request.getName()).isPresent()) {
            throw new IllegalArgumentException("收藏夹名称已存在");
        }

        // 构建收藏夹实体（记录创建者，收藏夹按创建者隔离展示）
        Category category = Category.builder()
                .name(request.getName())
                .description(request.getDescription())
                .owner(securityContextUtil.getCurrentUser())
                .build();

        Category savedCategory = categoryRepository.save(category);
        return convertToDTO(savedCategory, loadContext());
    }

    /**
     * 更新收藏夹信息
     */
    @CacheEvict(cacheNames = {"category:detail", "category:list"}, allEntries = true)
    @Transactional(timeout = 30)
    public CategoryDTO updateCategory(Long categoryId, CategoryRequest request) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("收藏夹不存在"));

        // 检查新名称是否与其他收藏夹重复
        categoryRepository.findByName(request.getName())
                .filter(c -> !c.getId().equals(categoryId))
                .ifPresent(c -> {
                    throw new IllegalArgumentException("收藏夹名称已存在");
                });

        // 更新字段
        category.setName(request.getName());
        category.setDescription(request.getDescription());

        Category updatedCategory = categoryRepository.save(category);
        return convertToDTO(updatedCategory, loadContext());
    }

    // ==================== 收藏夹查询 ====================

    /**
     * 根据 ID 获取收藏夹详情
     */
    @Cacheable(cacheNames = "category:detail", key = "#categoryId", sync = true)
    public CategoryDTO getCategoryById(Long categoryId) {
        CategoryContext ctx = loadContext();
        Category category = ctx.categoriesById().get(categoryId);
        if (category == null) {
            throw new EntityNotFoundException("收藏夹不存在");
        }
        return convertToDTO(category, ctx);
    }

    /**
     * 根据名称获取收藏夹
     */
    @Cacheable(cacheNames = "category:detail", key = "#name", sync = true)
    public CategoryDTO getCategoryByName(String name) {
        Category category = categoryRepository.findByName(name)
                .orElseThrow(() -> new EntityNotFoundException("收藏夹不存在"));
        return convertToDTO(category, loadContext());
    }

    /**
     * 分页查询所有收藏夹
     */
    @Cacheable(cacheNames = "category:list", sync = true)
    public PageResponseDTO<CategoryDTO> getAllCategories(int page, int size, String sortBy) {
        Pageable pageable = PageRequest.of(page, size, safeSort(sortBy));
        Page<Category> categoryPage = categoryRepository.findAll(pageable);

        CategoryContext ctx = loadContext();

        List<CategoryDTO> content = categoryPage.getContent().stream()
                .map(c -> convertToDTO(c, ctx))
                .collect(Collectors.toList());

        return PageResponseDTO.<CategoryDTO>builder()
                .content(content)
                .page(categoryPage.getNumber())
                .size(categoryPage.getSize())
                .totalElements(categoryPage.getTotalElements())
                .totalPages(categoryPage.getTotalPages())
                .first(categoryPage.isFirst())
                .last(categoryPage.isLast())
                .build();
    }

    // ==================== 我的收藏夹（按创建者隔离） ====================

    /**
     * 获取当前登录用户创建的全部收藏夹（平级列表）
     */
    public List<CategoryDTO> getMyCategories() {
        Long ownerId = securityContextUtil.getCurrentUserId();
        CategoryContext ctx = loadContext();
        return ctx.categoriesById().values().stream()
                .filter(c -> isOwnedBy(c, ownerId))
                .sorted(Comparator.comparing(Category::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(c -> convertToDTO(c, ctx))
                .collect(Collectors.toList());
    }

    /**
     * 判断收藏夹是否由指定用户创建
     */
    private boolean isOwnedBy(Category category, Long ownerId) {
        return category.getOwner() != null
                && category.getOwner().getId() != null
                && category.getOwner().getId().equals(ownerId);
    }

    // ==================== 收藏夹统计 ====================

    /**
     * 获取所有收藏夹及其文章数量
     */
    @Cacheable(cacheNames = "category:list", sync = true)
    public List<CategoryStatDTO> getCategoryStatistics() {
        List<Object[]> results = categoryRepository.findAllWithArticleCount();

        return results.stream()
                .map(obj -> {
                    if (obj[0] == null) {
                        throw new IllegalStateException("收藏夹统计结果异常");
                    }
                    Category category = (Category) obj[0];
                    Long articleCount = (Long) obj[1];
                    return CategoryStatDTO.builder()
                            .categoryName(category.getName())
                            .articleCount(articleCount)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取收藏夹的文章占比统计
     */
    @Cacheable(cacheNames = "category:list", sync = true)
    public List<CategoryStatDTO> getCategoryPercentageStats() {
        List<CategoryStatDTO> stats = getCategoryStatistics();

        long totalArticles = stats.stream()
                .mapToLong(CategoryStatDTO::getArticleCount)
                .sum();

        if (totalArticles == 0) {
            return stats;
        }

        stats.forEach(stat -> {
            double percentage = (stat.getArticleCount() * 100.0) / totalArticles;
            stat.setPercentage(Math.round(percentage * 100.0) / 100.0);
        });

        return stats;
    }

    // ==================== 收藏夹删除 ====================

    /**
     * 删除收藏夹（如果收藏夹下有文章，则不允许删除）
     */
    @CacheEvict(cacheNames = "category:list", allEntries = true)
    @Transactional(timeout = 30)
    public void deleteCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("收藏夹不存在"));

        Hibernate.initialize(category.getArticles());

        // 检查是否有文章关联
        if (!category.getArticles().isEmpty()) {
            throw new IllegalStateException("该收藏夹下还有文章，无法删除");
        }

        categoryRepository.delete(category);
    }

    /**
     * 删除收藏夹并转移文章到指定收藏夹
     */
    @CacheEvict(cacheNames = "category:list", allEntries = true)
    @Transactional(timeout = 30)
    public void deleteCategoryAndTransferArticles(Long categoryId, Long targetCategoryId) {
        Category sourceCategory = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("源收藏夹不存在"));

        Hibernate.initialize(sourceCategory.getArticles());

        // 如果有目标收藏夹，转移文章；否则直接移除关联
        if (targetCategoryId != null) {
            Category targetCategory = categoryRepository.findById(targetCategoryId)
                    .orElseThrow(() -> new EntityNotFoundException("目标收藏夹不存在"));

            transferArticlesToTargetCategory(sourceCategory, targetCategory);
        } else {
            removeCategoryAssociation(sourceCategory);
        }

        categoryRepository.delete(sourceCategory);
    }

    private void transferArticlesToTargetCategory(Category source, Category target) {
        source.getArticles().forEach(article -> {
            article.removeCategory(source);
            article.addCategory(target);
        });
    }

    private void removeCategoryAssociation(Category source) {
        source.getArticles().forEach(article ->
                article.removeCategory(source)
        );
    }

    // ==================== 收藏夹搜索 ====================

    /**
     * 搜索收藏夹（根据名称或描述，数据库模糊查询，避免全表加载后在内存过滤）
     */
    @Cacheable(cacheNames = "category:list", sync = true)
    public List<CategoryDTO> searchCategories(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return new ArrayList<>();
        }

        CategoryContext ctx = loadContext();
        return categoryRepository.searchByNameOrDescription(keyword).stream()
                .map(c -> convertToDTO(c, ctx))
                .collect(Collectors.toList());
    }

    /**
     * 获取有文章的收藏夹列表（SQL JOIN 过滤，避免全表加载后在内存判断）
     */
    @Cacheable(cacheNames = "category:list", sync = true)
    public List<CategoryDTO> getCategoriesWithArticles() {
        CategoryContext ctx = loadContext();
        return categoryRepository.findWithArticles().stream()
                .map(c -> convertToDTO(c, ctx))
                .collect(Collectors.toList());
    }

    // ==================== 辅助方法 ====================

    /**
     * 转换为 CategoryDTO（文章数取自预加载上下文，无懒加载查询）
     */
    private CategoryDTO convertToDTO(Category category, CategoryContext ctx) {
        return CategoryDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .articleCount(ctx.articleCounts().getOrDefault(category.getId(), 0L).intValue())
                .build();
    }

    /**
     * 检查收藏夹名称是否存在（排除指定 ID）
     */
    @Cacheable(cacheNames = "category:list", sync = true)
    public boolean existsByName(String name, Long excludeId) {
        return categoryRepository.findByName(name)
                .filter(c -> !c.getId().equals(excludeId))
                .isPresent();
    }

    /**
     * 检查收藏夹名称是否存在
     */
    @Cacheable(cacheNames = "category:list", sync = true)
    public boolean existsByName(String name) {
        return categoryRepository.findByName(name).isPresent();
    }

    /**
     * 获取收藏夹总数
     */
    @Cacheable(cacheNames = "category:list", sync = true)
    public long getTotalCategoryCount() {
        return categoryRepository.count();
    }
}
