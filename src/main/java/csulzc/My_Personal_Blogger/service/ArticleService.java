package csulzc.My_Personal_Blogger.service;

import csulzc.My_Personal_Blogger.api.dto.article.*;
import csulzc.My_Personal_Blogger.api.dto.common.PageResponseDTO;
import csulzc.My_Personal_Blogger.api.dto.category.CategoryDTO;
import csulzc.My_Personal_Blogger.api.dto.user.UserProfileDTO;
import csulzc.My_Personal_Blogger.domain.entity.*;
import csulzc.My_Personal_Blogger.repository.*;
import csulzc.My_Personal_Blogger.security.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArticleService {

    /** 批量 IN 子句的单批上限，避免超长 IN 子句导致性能退化或超限 */
    private static final int BATCH_SIZE = 500;

    private final ArticleRepository articleRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final SecurityContextUtil securityContextUtil;

    /**
     * 创建文章
     */
    @CacheEvict(cacheNames = "article:list", allEntries = true)
    @Transactional(timeout = 30)
    public ArticleDetailDTO createArticle(@Valid ArticleCreateRequest request) {
        User author = securityContextUtil.getCurrentUserAndValidateStatus();

        Article article = Article.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .summary(generateSummary(request.getContent()))
                .coverImage(request.getCoverImage())
                .status(request.getStatus() != null ? request.getStatus() : Article.ArticleStatus.DRAFT)
                .author(author)
                .likeCount(0)
                .favoriteCount(0)
                .build();

        if (request.getCategoryIds() != null && !request.getCategoryIds().isEmpty()) {
            Set<Category> categories = categoryRepository.findAllById(request.getCategoryIds()).stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            if (categories.size() != request.getCategoryIds().size()) {
                throw new EntityNotFoundException("部分收藏夹不存在");
            }

            categories.forEach(article::addCategory);
        }

        Article savedArticle = articleRepository.save(article);

        return convertToDetailDTO(savedArticle);
    }

    /**
     * 更新文章
     */
    @CacheEvict(cacheNames = "article:detail", key = "#articleId")
    @Transactional(timeout = 30)
    public ArticleDetailDTO updateArticle(Long articleId, @Valid ArticleUpdateRequest request) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new EntityNotFoundException("文章不存在"));

        Long currentUserId = securityContextUtil.getCurrentUserId();
        User author = article.getAuthor();
        if (author == null) {
            throw new IllegalStateException("文章作者信息缺失");
        }
        securityContextUtil.validateOwnershipOrAdmin(author.getId(), "文章");

        if (request.getTitle() != null) {
            article.setTitle(request.getTitle());
        }

        if (request.getContent() != null) {
            article.setContent(request.getContent());
            article.setSummary(generateSummary(request.getContent()));
        }

        if (request.getSummary() != null) {
            article.setSummary(request.getSummary());
        }

        if (request.getCoverImage() != null) {
            article.setCoverImage(request.getCoverImage());
        }

        if (request.getStatus() != null) {
            article.setStatus(request.getStatus());
        }

        if (request.getCategoryIds() != null) {
            Set<Category> existingCategories = new HashSet<>(article.getCategories());
            existingCategories.forEach(article::removeCategory);

            Set<Category> newCategories = new HashSet<>(categoryRepository.findAllById(request.getCategoryIds()));

            if (newCategories.size() != request.getCategoryIds().size()) {
                throw new EntityNotFoundException("部分收藏夹不存在");
            }

            newCategories.forEach(article::addCategory);
        }

        Article updatedArticle = articleRepository.save(article);
        return convertToDetailDTO(updatedArticle);
    }

    /**
     * 发布文章（将状态改为 RELEASE）
     */
    @CacheEvict(cacheNames = "article:detail", key = "#articleId")
    @Transactional(timeout = 30)
    public ArticleDetailDTO publishArticle(Long articleId) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new EntityNotFoundException("文章不存在"));

        User author = article.getAuthor();
        if (author == null) {
            throw new IllegalStateException("文章作者信息缺失");
        }
        securityContextUtil.validateOwnershipOrAdmin(author.getId(), "文章");

        article.setStatus(Article.ArticleStatus.RELEASE);
        Article publishedArticle = articleRepository.save(article);

        return convertToDetailDTO(publishedArticle);
    }

    /**
     * 归档文章
     */
    @CacheEvict(cacheNames = "article:detail", key = "#articleId")
    @Transactional(timeout = 30)
    public ArticleDetailDTO archiveArticle(Long articleId) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new EntityNotFoundException("文章不存在"));

        User author = article.getAuthor();
        if (author == null) {
            throw new IllegalStateException("文章作者信息缺失");
        }
        securityContextUtil.validateOwnershipOrAdmin(author.getId(), "文章");

        article.setStatus(Article.ArticleStatus.ARCHIVE);
        Article archivedArticle = articleRepository.save(article);

        return convertToDetailDTO(archivedArticle);
    }

    /**
     * 根据 ID 获取文章详情
     */
    @Cacheable(cacheNames = "article:detail", key = "#articleId")
    public ArticleDetailDTO getArticleById(Long articleId) {
        Article article = articleRepository.findDetailById(articleId)
                .orElseThrow(() -> new EntityNotFoundException("文章不存在"));

        return convertToDetailDTO(article);
    }

    /**
     * 获取文章列表（分页）
     */
    @Cacheable(cacheNames = "article:list", key = "#pageable")
    public PageResponseDTO<ArticleListItemDTO> getArticleList(Pageable pageable) {
        Page<Article> articlePage = articleRepository.findAllWithAuthor(pageable);
        return buildListResponse(articlePage);
    }

    /**
     * 根据作者获取文章列表
     */
    @Cacheable(cacheNames = "article:list", key = "#author.id + #pageable")
    public PageResponseDTO<ArticleListItemDTO> getArticlesByAuthor(User author, Pageable pageable) {
        Page<Article> articlePage = articleRepository.findByAuthor(author, pageable);
        return buildListResponse(articlePage);
    }

    /**
     * 搜索文章（根据标题）
     */
    public PageResponseDTO<ArticleListItemDTO> searchArticles(String keyword, Pageable pageable) {
        Page<Article> articlePage = articleRepository.findByTitleContaining(keyword, pageable);
        return buildListResponse(articlePage);
    }

    /**
     * 删除文章（先批量清理分类关联与评论，再删除文章本身，避免 JPA 级联逐条删除）
     */
    @CacheEvict(cacheNames = "article:detail", key = "#articleId")
    @Transactional(timeout = 30)
    public void deleteArticle(Long articleId) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new EntityNotFoundException("文章不存在"));

        User author = article.getAuthor();
        if (author == null) {
            throw new IllegalStateException("文章作者信息缺失");
        }
        securityContextUtil.validateOwnershipOrAdmin(author.getId(), "文章");

        // 先删分类关联与评论（各一条批量 SQL），再删文章本身；
        // 评论先删回复再删顶级，避免自引用外键约束冲突
        articleRepository.deleteCategoryMappings(articleId);
        commentRepository.deleteRepliesByArticle(article);
        commentRepository.deleteTopLevelByArticle(article);
        articleRepository.deleteByIdDirect(articleId);
    }

    @CacheEvict(cacheNames = "article:list", allEntries = true)
    @Transactional(timeout = 120)
    public int batchDeleteArticles(List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (List<Long> batch : partition(articleIds, BATCH_SIZE)) {
            // 先清理评论（先回复后顶级）与分类关联，避免外键约束失败
            commentRepository.batchDeleteRepliesByArticleIds(batch);
            commentRepository.batchDeleteTopLevelByArticleIds(batch);
            articleRepository.batchDeleteCategoryMappings(batch);
            total += articleRepository.batchDeleteByIds(batch);
        }
        return total;
    }

    @CacheEvict(cacheNames = "article:list", allEntries = true)
    @Transactional(timeout = 60)
    public int batchPublishArticles(List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (List<Long> batch : partition(articleIds, BATCH_SIZE)) {
            total += articleRepository.batchUpdateStatus(batch, Article.ArticleStatus.RELEASE);
        }
        return total;
    }

    @CacheEvict(cacheNames = "article:list", allEntries = true)
    @Transactional(timeout = 60)
    public int batchArchiveArticles(List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (List<Long> batch : partition(articleIds, BATCH_SIZE)) {
            total += articleRepository.batchUpdateStatus(batch, Article.ArticleStatus.ARCHIVE);
        }
        return total;
    }

    /**
     * 将大列表切分为指定大小的批次（避免超长 IN 子句）
     */
    private List<List<Long>> partition(List<Long> ids, int size) {
        List<List<Long>> parts = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += size) {
            parts.add(new ArrayList<>(ids.subList(i, Math.min(i + size, ids.size()))));
        }
        return parts;
    }

    @CacheEvict(cacheNames = "article:detail", key = "#articleId")
    @Transactional(timeout = 10)
    public void likeArticle(Long articleId) {
        articleRepository.incrementLikeCount(articleId);
    }

    @CacheEvict(cacheNames = "article:detail", key = "#articleId")
    @Transactional(timeout = 10)
    public void viewArticle(Long articleId) {
        articleRepository.incrementViewCount(articleId);
    }

    /**
     * 生成摘要（从内容中提取前 200 个字符）
     */
    private String generateSummary(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }

        int maxLength = 200;
        if (content.length() <= maxLength) {
            return content;
        }

        return content.substring(0, maxLength) + "...";
    }

    /**
     * 转换为 ArticleDetailDTO
     */
    private ArticleDetailDTO convertToDetailDTO(Article article) {
        return ArticleDetailDTO.builder()
                .id(article.getId())
                .title(article.getTitle())
                .content(article.getContent())
                .summary(article.getSummary())
                .coverImage(article.getCoverImage())
                .status(article.getStatus())
                .likeCount(article.getLikeCount())
                .favoriteCount(article.getFavoriteCount())
                .commentCount((int) commentRepository.countByArticle(article))
                .author(convertToUserProfileDTO(article.getAuthor()))
                .categories(convertToCategoryDTOs(article.getCategories()))
                .build();
    }

    private UserProfileDTO convertToUserProfileDTO(User user) {
        if (user == null) {
            return null;
        }

        if (user.getId() == null) {
            return UserProfileDTO.builder()
                    .username(user.getUsername())
                    .displayName(user.getDisplayName())
                    .avatar(user.getAvatar())
                    .bio(user.getBio())
                    .createdAt(user.getCreatedAt())
                    .build();
        }

        return UserProfileDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .avatar(user.getAvatar())
                .bio(user.getBio())
                .createdAt(user.getCreatedAt())
                .build();
    }
    /**
     * 组装分页文章列表：批量预加载评论数与分类映射，避免逐篇懒加载（N+1）
     */
    private PageResponseDTO<ArticleListItemDTO> buildListResponse(Page<Article> articlePage) {
        List<Article> articles = articlePage.getContent();
        List<Long> articleIds = articles.stream().map(Article::getId).collect(Collectors.toList());

        Map<Long, Long> commentCounts = loadCommentCounts(articleIds);
        Map<Long, Set<Category>> categoryMap = loadCategoryMappings(articleIds);

        List<ArticleListItemDTO> content = articles.stream()
                .map(article -> convertToListItemDTO(
                        article,
                        commentCounts.getOrDefault(article.getId(), 0L),
                        categoryMap.getOrDefault(article.getId(), Set.of())))
                .collect(Collectors.toList());

        return PageResponseDTO.<ArticleListItemDTO>builder()
                .content(content)
                .totalElements(articlePage.getTotalElements())
                .totalPages(articlePage.getTotalPages())
                .page(articlePage.getNumber())
                .size(articlePage.getSize())
                .build();
    }

    /**
     * 批量查询评论数：articleId -> count（一条 SQL 替代逐篇懒加载评论集合）
     */
    private Map<Long, Long> loadCommentCounts(List<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return new HashMap<>();
        }
        return commentRepository.countByArticleIds(articleIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).longValue()));
    }

    /**
     * 批量查询文章分类映射：articleId -> Set<Category>（一条 SQL 替代逐篇懒加载分类集合）
     */
    private Map<Long, Set<Category>> loadCategoryMappings(List<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return new HashMap<>();
        }
        Map<Long, Set<Category>> categoryMap = new HashMap<>();
        for (Object[] row : articleRepository.findCategoryMappingByArticleIds(articleIds)) {
            Long articleId = (Long) row[0];
            categoryMap.computeIfAbsent(articleId, k -> new HashSet<>()).add((Category) row[1]);
        }
        return categoryMap;
    }

    /**
     * 转换为 ArticleListItemDTO（列表场景：评论数与分类由批量查询预加载，不再访问懒加载集合）
     */
    private ArticleListItemDTO convertToListItemDTO(Article article, long commentCount, Set<Category> categories) {
        return ArticleListItemDTO.builder()
                .id(article.getId())
                .title(article.getTitle())
                .summary(article.getSummary())
                .coverImage(article.getCoverImage())
                .createdAt(article.getCreatedAt())
                .likeCount(article.getLikeCount())
                .commentCount((int) commentCount)
                .author(convertToUserProfileDTO(article.getAuthor()))
                .categories(convertToCategoryDTOs(categories))
                .build();
    }

    /**
     * 转换 Category 列表为 CategoryDTO 列表
     */
    private List<CategoryDTO> convertToCategoryDTOs(Set<Category> categories) {
        if (categories == null) {
            return new ArrayList<>();
        }

        return categories.stream()
                .map(category -> CategoryDTO.builder()
                        .name(category.getName())
                        .build())
                .collect(Collectors.toList());
    }
}