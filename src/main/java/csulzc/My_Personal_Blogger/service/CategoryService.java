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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

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

        return convertToDTO(savedCategory);
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

        // 更新父收藏夹
        if (request.getParentCategoryId() != null) {
            if (request.getParentCategoryId().equals(categoryId)) {
                throw new IllegalArgumentException("不能将自己设置为父收藏夹");
            }

            Category parentCategory = categoryRepository.findById(request.getParentCategoryId())
                    .orElseThrow(() -> new EntityNotFoundException("父收藏夹不存在"));

            // 检查是否会形成循环引用
            if (isChildCategory(parentCategory, category)) {
                throw new IllegalArgumentException("不能将子收藏夹设置为父收藏夹，会形成循环引用");
            }

            category.setParentCategory(parentCategory);
        } else {
            category.setParentCategory(null);
        }

        Category updatedCategory = categoryRepository.save(category);
        return convertToDTO(updatedCategory);
    }

    // ==================== 收藏夹查询 ====================

    /**
     * 根据 ID 获取收藏夹详情
     */
    @Cacheable(cacheNames = "category:detail", key = "#categoryId")
    public CategoryDTO getCategoryById(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("收藏夹不存在"));
        return convertToDTO(category);
    }

    /**
     * 根据名称获取收藏夹
     */
    @Cacheable(cacheNames = "category:detail", key = "#name")
    public CategoryDTO getCategoryByName(String name) {
        Category category = categoryRepository.findByName(name)
                .orElseThrow(() -> new EntityNotFoundException("收藏夹不存在"));
        return convertToDTO(category);
    }

    /**
     * 获取所有顶级收藏夹（没有父收藏夹的收藏夹）
     */
    @Cacheable(cacheNames = "category:list")
    public List<CategoryDTO> getAllTopLevelCategories() {
        List<Category> categories = categoryRepository.findByParentCategoryIsNull();
        return categories.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取某个收藏夹的所有子收藏夹
     */
    @Cacheable(cacheNames = "category:list")
    public List<CategoryDTO> getSubCategories(Long parentCategoryId) {
        Category parent = categoryRepository.findById(parentCategoryId)
                .orElseThrow(() -> new EntityNotFoundException("收藏夹不存在"));

        return categoryRepository.findByParentCategory(parent).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 分页查询所有收藏夹
     */
    @Cacheable(cacheNames = "category:list")
    public PageResponseDTO<CategoryDTO> getAllCategories(int page, int size, String sortBy) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy).descending());
        Page<Category> categoryPage = categoryRepository.findAll(pageable);

        List<CategoryDTO> content = categoryPage.getContent().stream()
                .map(this::convertToDTO)
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
        List<Category> allCategories = categoryRepository.findAll();

        // 找到所有顶级收藏夹
        List<Category> topCategories = allCategories.stream()
                .filter(c -> c.getParentCategory() == null)
                .collect(Collectors.toList());

        // 递归构建树形结构
        return topCategories.stream()
                .map(c -> buildCategoryTreeNode(c, allCategories))
                .collect(Collectors.toList());
    }

    /**
     * 递归构建收藏夹树节点
     */
    private CategoryTreeDTO buildCategoryTreeNode(Category category, List<Category> allCategories) {
        CategoryTreeDTO node = CategoryTreeDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .articleCount(countArticlesInCategory(category))
                .children(new ArrayList<>())
                .build();

        // 查找直接子收藏夹
        List<Category> directChildren = allCategories.stream()
                .filter(c -> category.equals(c.getParentCategory()))
                .collect(Collectors.toList());

        // 递归构建子节点
        List<CategoryTreeDTO> children = directChildren.stream()
                .map(child -> buildCategoryTreeNode(child, allCategories))
                .collect(Collectors.toList());

        node.setChildren(children);
        return node;
    }

    /**
     * 获取收藏夹的完整路径（从根到当前收藏夹）
     */
    @Cacheable(cacheNames = "category:list")
    public List<CategoryDTO> getCategoryPath(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("收藏夹不存在"));

        List<CategoryDTO> path = new ArrayList<>();
        Category current = category;

        while (current != null) {
            path.add(0, convertToDTO(current));
            current = current.getParentCategory();
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
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("收藏夹不存在"));

        return countArticlesRecursive(category);
    }

    /**
     * 递归计算收藏夹及其子收藏夹的文章总数
     */
    private long countArticlesRecursive(Category category) {
        long count = category.getArticles().size();

        if (category.getSubCategories() != null) {
            for (Category subCategory : category.getSubCategories()) {
                count += countArticlesRecursive(subCategory);
            }
        }


        return count;
    }

    /**
     * 计算收藏夹的文章数量（不包含子收藏夹）
     */
    private int countArticlesInCategory(Category category) {
        return category.getArticles() != null ? category.getArticles().size() : 0;
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
     * 搜索收藏夹（根据名称或描述）
     */
    @Cacheable(cacheNames = "category:list")
    public List<CategoryDTO> searchCategories(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return new ArrayList<>();
        }

        String lowerCaseKeyword = keyword.toLowerCase();

        return categoryRepository.findAll().stream()
                .filter(c -> c.getName().toLowerCase().contains(lowerCaseKeyword) ||
                        (c.getDescription() != null &&
                                c.getDescription().toLowerCase().contains(lowerCaseKeyword)))
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取有文章的收藏夹列表
     */
    @Cacheable(cacheNames = "category:list")
    public List<CategoryDTO> getCategoriesWithArticles() {
        return categoryRepository.findAll().stream()
                .filter(c -> !c.getArticles().isEmpty())
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // ==================== 辅助方法 ====================

    /**
     * 检查是否是子收藏夹（避免循环引用）
     */
    private boolean isChildCategory(Category potentialChild, Category potentialParent) {
        Category current = potentialChild;
        while (current != null) {
            if (current.equals(potentialParent)) {
                return true;
            }
            current = current.getParentCategory();
        }
        return false;
    }

    /**
     * 转换为 CategoryDTO
     */
    private CategoryDTO convertToDTO(Category category) {
        CategoryDTO dto = CategoryDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .articleCount(countArticlesInCategory(category))
                .build();

        // 设置父收藏夹信息
        if (category.getParentCategory() != null) {
            dto.setParentCategoryId(category.getParentCategory().getId());
            dto.setParentCategoryName(category.getParentCategory().getName());
        }

        // 设置子收藏夹列表（只转换一层，避免无限递归）
        if (!category.getSubCategories().isEmpty()) {
            List<CategoryDTO> subCategoryDTOs = category.getSubCategories().stream()
                    .map(sub -> CategoryDTO.builder()
                            .id(sub.getId())
                            .name(sub.getName())
                            .description(sub.getDescription())
                            .articleCount(countArticlesInCategory(sub))
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
     * 获取顶级收藏夹数量
     */
    @Cacheable(cacheNames = "category:list")
    public long getTopLevelCategoryCount() {
        return categoryRepository.findByParentCategoryIsNull().size();
    }
}
