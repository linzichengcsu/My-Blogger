package csulzc.My_Personal_Blogger.service;

import csulzc.My_Personal_Blogger.api.dto.category.*;
import csulzc.My_Personal_Blogger.api.dto.common.PageResponseDTO;
import csulzc.My_Personal_Blogger.domain.entity.Category;
import csulzc.My_Personal_Blogger.repository.CategoryRepository;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    /**
     * 分类查询上下文：一次加载全量分类、文章数聚合与父子关系映射，
     * 供各查询方法在内存中组装 DTO，避免逐分类懒加载（N+1）。
     */
    private record CategoryContext(
            Map<Long, Long> articleCounts,      // categoryId -> 文章数（含 0）
            Map<Long, Category> categoriesById, // categoryId -> 分类实体
            Map<Long, List<Category>> childrenMap) { // parentCategoryId -> 直接子分类列表
    }

    /**
     * 加载分类查询上下文（固定 2 条 SQL：分类全量 + 文章数聚合）
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

        Map<Long, List<Category>> childrenMap = all.stream()
                .filter(c -> c.getParentCategory() != null)
                .collect(Collectors.groupingBy(c -> c.getParentCategory().getId()));

        return new CategoryContext(articleCounts, categoriesById, childrenMap);
    }

    // ==================== 收藏夹创建与更新 ====================

    /**
     * 创建收藏夹
     */
    @CacheEvict(cacheNames = {"category:list", "category:tree"}, allEntries = true)
    @Transactional(timeout = 30)
    public CategoryDTO createCategory(CategoryRequest request) {
        // 检查收藏夹名称是否已存在
        if (categoryRepository.findByName(request.getName()).isPresent()) {
            throw new IllegalArgumentException("收藏夹名称已存在");
        }

        // 构建收藏夹实体
        Category.CategoryBuilder categoryBuilder = Category.builder()
                .name(request.getName())
                .description(request.getDescription());

        // 设置父收藏夹
        if (request.getParentCategoryId() != null) {
            Category parentCategory = categoryRepository.findById(request.getParentCategoryId())
                    .orElseThrow(() -> new EntityNotFoundException("父收藏夹不存在"));
            categoryBuilder.parentCategory(parentCategory);
        } else {
            categoryBuilder.parentCategory(null);
        }

        Category category = categoryBuilder.build();
        Category savedCategory = categoryRepository.save(category);

        return convertToDTO(savedCategory, loadContext());
    }

    /**
     * 更新收藏夹信息
     */
    @CacheEvict(cacheNames = {"category:detail", "category:tree", "category:list"}, allEntries = true)
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

        CategoryContext ctx = loadContext();

        // 更新父收藏夹
        if (request.getParentCategoryId() != null) {
            if (request.getParentCategoryId().equals(categoryId)) {
                throw new IllegalArgumentException("不能将自己设置为父收藏夹");
            }

            Category parentCategory = ctx.categoriesById().get(request.getParentCategoryId());
            if (parentCategory == null) {
                throw new EntityNotFoundException("父收藏夹不存在");
            }

            // 检查是否会形成循环引用（纯内存遍历，不再逐层懒加载）
            if (isChildCategory(parentCategory, category, ctx.categoriesById())) {
                throw new IllegalArgumentException("不能将子收藏夹设置为父收藏夹，会形成循环引用");
            }

            category.setParentCategory(parentCategory);
        } else {
            category.setParentCategory(null);
        }

        Category updatedCategory = categoryRepository.save(category);
        return convertToDTO(updatedCategory, ctx);
    }

    // ==================== 收藏夹查询 ====================

    /**
     * 根据 ID 获取收藏夹详情
     */
    @Cacheable(cacheNames = "category:detail", key = "#categoryId")
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
    @Cacheable(cacheNames = "category:detail", key = "#name")
    public CategoryDTO getCategoryByName(String name) {
        Category category = categoryRepository.findByName(name)
                .orElseThrow(() -> new EntityNotFoundException("收藏夹不存在"));
        return convertToDTO(category, loadContext());
    }

    /**
     * 获取所有顶级收藏夹（没有父收藏夹的收藏夹）
     */
    @Cacheable(cacheNames = "category:list")
    public List<CategoryDTO> getAllTopLevelCategories() {
        CategoryContext ctx = loadContext();
        return ctx.categoriesById().values().stream()
                .filter(c -> c.getParentCategory() == null)
                .map(c -> convertToDTO(c, ctx))
                .collect(Collectors.toList());
    }

    /**
     * 获取某个收藏夹的所有子收藏夹
     */
    @Cacheable(cacheNames = "category:list")
    public List<CategoryDTO> getSubCategories(Long parentCategoryId) {
        CategoryContext ctx = loadContext();
        if (!ctx.categoriesById().containsKey(parentCategoryId)) {
            throw new EntityNotFoundException("收藏夹不存在");
        }
        return ctx.childrenMap().getOrDefault(parentCategoryId, List.of()).stream()
                .map(c -> convertToDTO(c, ctx))
                .collect(Collectors.toList());
    }

    /**
     * 分页查询所有收藏夹
     */
    @Cacheable(cacheNames = "category:list")
    public PageResponseDTO<CategoryDTO> getAllCategories(int page, int size, String sortBy) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy).descending());
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

    // ==================== 收藏夹树管理 ====================

    /**
     * 构建收藏夹树（用于前端下拉树形选择器）
     */
    @Cacheable(cacheNames = "category:tree")
    public List<CategoryTreeDTO> buildCategoryTree() {
        CategoryContext ctx = loadContext();

        // 找到所有顶级收藏夹
        List<Category> topCategories = ctx.categoriesById().values().stream()
                .filter(c -> c.getParentCategory() == null)
                .collect(Collectors.toList());

        // 递归构建树形结构（子分类关系取自预加载的 childrenMap，不再逐层懒加载）
        return topCategories.stream()
                .map(c -> buildCategoryTreeNode(c, ctx))
                .collect(Collectors.toList());
    }

    /**
     * 递归构建收藏夹树节点
     */
    private CategoryTreeDTO buildCategoryTreeNode(Category category, CategoryContext ctx) {
        CategoryTreeDTO node = CategoryTreeDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .articleCount(ctx.articleCounts().getOrDefault(category.getId(), 0L).intValue())
                .children(new ArrayList<>())
                .build();

        // 查找直接子收藏夹（内存映射，无查询）
        List<CategoryTreeDTO> children = ctx.childrenMap().getOrDefault(category.getId(), List.of()).stream()
                .map(child -> buildCategoryTreeNode(child, ctx))
                .collect(Collectors.toList());

        node.setChildren(children);
        return node;
    }

    /**
     * 获取收藏夹的完整路径（从根到当前收藏夹）
     */
    @Cacheable(cacheNames = "category:list")
    public List<CategoryDTO> getCategoryPath(Long categoryId) {
        CategoryContext ctx = loadContext();
        if (!ctx.categoriesById().containsKey(categoryId)) {
            throw new EntityNotFoundException("收藏夹不存在");
        }

        List<CategoryDTO> path = new ArrayList<>();
        Long currentId = categoryId;
        while (currentId != null) {
            Category current = ctx.categoriesById().get(currentId);
            if (current == null) {
                break;
            }
            path.add(0, convertToDTO(current, ctx));
            currentId = current.getParentCategory() != null ? current.getParentCategory().getId() : null;
        }

        return path;
    }

    // ==================== 收藏夹统计 ====================

    /**
     * 获取所有收藏夹及其文章数量
     */
    @Cacheable(cacheNames = "category:list")
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
     * 计算收藏夹的文章数量（包含子收藏夹的文章）
     */
    @Cacheable(cacheNames = "category:list")
    public long countArticlesInCategoryIncludingSubCategories(Long categoryId) {
        CategoryContext ctx = loadContext();
        Category category = ctx.categoriesById().get(categoryId);
        if (category == null) {
            throw new EntityNotFoundException("收藏夹不存在");
        }
        return countArticlesRecursive(category, ctx);
    }

    /**
     * 递归计算收藏夹及其子收藏夹的文章总数（基于预加载的文章数映射，无查询）
     */
    private long countArticlesRecursive(Category category, CategoryContext ctx) {
        long count = ctx.articleCounts().getOrDefault(category.getId(), 0L);

        for (Category subCategory : ctx.childrenMap().getOrDefault(category.getId(), List.of())) {
            count += countArticlesRecursive(subCategory, ctx);
        }

        return count;
    }

    /**
     * 获取收藏夹的文章占比统计
     */
    @Cacheable(cacheNames = "category:list")
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
     * 删除收藏夹（如果收藏夹下有文章或子收藏夹，则不允许删除）
     */
    @CacheEvict(cacheNames = {"category:list", "category:tree"}, allEntries = true)
    @Transactional(timeout = 30)
    public void deleteCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("收藏夹不存在"));

        Hibernate.initialize(category.getArticles());
        Hibernate.initialize(category.getSubCategories());

        // 检查是否有文章关联
        if (!category.getArticles().isEmpty()) {
            throw new IllegalStateException("该收藏夹下还有文章，无法删除");
        }

        // 检查是否有子收藏夹
        if (!category.getSubCategories().isEmpty()) {
            throw new IllegalStateException("该收藏夹还有子收藏夹，无法删除");
        }

        // 如果有父收藏夹，需要从父收藏夹的子收藏夹列表中移除
        Category parentCategory = category.getParentCategory();
        if (parentCategory != null) {
            if (parentCategory.getSubCategories() != null) {
                parentCategory.getSubCategories().remove(category);
            }
            categoryRepository.save(parentCategory);
        }

        categoryRepository.delete(category);
    }

    /**
     * 删除收藏夹并转移文章到指定收藏夹
     */
    @CacheEvict(cacheNames = {"category:list", "category:tree"}, allEntries = true)
    @Transactional(timeout = 30)
    public void deleteCategoryAndTransferArticles(Long categoryId, Long targetCategoryId) {
        Category sourceCategory = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("源收藏夹不存在"));

        Hibernate.initialize(sourceCategory.getArticles());
        Hibernate.initialize(sourceCategory.getSubCategories());

        // 检查是否有子收藏夹
        if (!sourceCategory.getSubCategories().isEmpty()) {
            throw new IllegalStateException("该收藏夹还有子收藏夹，请先删除或转移子收藏夹");
        }

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
    @Cacheable(cacheNames = "category:list")
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
    @Cacheable(cacheNames = "category:list")
    public List<CategoryDTO> getCategoriesWithArticles() {
        CategoryContext ctx = loadContext();
        return categoryRepository.findWithArticles().stream()
                .map(c -> convertToDTO(c, ctx))
                .collect(Collectors.toList());
    }

    // ==================== 辅助方法 ====================

    /**
     * 检查是否是子收藏夹（避免循环引用，基于预加载映射纯内存遍历）
     */
    private boolean isChildCategory(Category potentialChild, Category potentialParent, Map<Long, Category> categoriesById) {
        Long currentId = potentialChild.getId();
        while (currentId != null) {
            Category current = categoriesById.get(currentId);
            if (current == null) {
                return false;
            }
            if (current.getId().equals(potentialParent.getId())) {
                return true;
            }
            currentId = current.getParentCategory() != null ? current.getParentCategory().getId() : null;
        }
        return false;
    }

    /**
     * 转换为 CategoryDTO（文章数、父分类、子分类均取自预加载上下文，无懒加载查询）
     */
    private CategoryDTO convertToDTO(Category category, CategoryContext ctx) {
        CategoryDTO dto = CategoryDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .articleCount(ctx.articleCounts().getOrDefault(category.getId(), 0L).intValue())
                .build();

        // 设置父收藏夹信息（父分类名称从预加载映射解析，避免访问懒加载代理属性）
        if (category.getParentCategory() != null) {
            Long parentId = category.getParentCategory().getId();
            Category parent = parentId != null ? ctx.categoriesById().get(parentId) : null;
            if (parent != null) {
                dto.setParentCategoryId(parent.getId());
                dto.setParentCategoryName(parent.getName());
            }
        }

        // 设置子收藏夹列表（只转换一层，避免无限递归）
        List<Category> subCategories = ctx.childrenMap().getOrDefault(category.getId(), List.of());
        if (!subCategories.isEmpty()) {
            List<CategoryDTO> subCategoryDTOs = subCategories.stream()
                    .map(sub -> CategoryDTO.builder()
                            .id(sub.getId())
                            .name(sub.getName())
                            .description(sub.getDescription())
                            .articleCount(ctx.articleCounts().getOrDefault(sub.getId(), 0L).intValue())
                            .build())
                    .collect(Collectors.toList());
            dto.setSubCategories(subCategoryDTOs);
        }

        return dto;
    }

    /**
     * 检查收藏夹名称是否存在（排除指定 ID）
     */
    @Cacheable(cacheNames = "category:list")
    public boolean existsByName(String name, Long excludeId) {
        return categoryRepository.findByName(name)
                .filter(c -> !c.getId().equals(excludeId))
                .isPresent();
    }

    /**
     * 检查收藏夹名称是否存在
     */
    @Cacheable(cacheNames = "category:list")
    public boolean existsByName(String name) {
        return categoryRepository.findByName(name).isPresent();
    }

    /**
     * 获取收藏夹总数
     */
    @Cacheable(cacheNames = "category:list")
    public long getTotalCategoryCount() {
        return categoryRepository.count();
    }

    /**
     * 获取顶级收藏夹数量（SQL count，避免加载全部顶级收藏夹实体）
     */
    @Cacheable(cacheNames = "category:list")
    public long getTopLevelCategoryCount() {
        return categoryRepository.countByParentCategoryIsNull();
    }
}
