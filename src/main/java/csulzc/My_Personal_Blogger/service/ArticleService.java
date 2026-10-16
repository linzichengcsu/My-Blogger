package csulzc.My_Personal_Blogger.service;

import csulzc.My_Personal_Blogger.api.dto.article.*;
import csulzc.My_Personal_Blogger.api.dto.common.PageResponseDTO;
import csulzc.My_Personal_Blogger.api.dto.category.CategoryDTO;
import csulzc.My_Personal_Blogger.api.dto.user.UserProfileDTO;
import csulzc.My_Personal_Blogger.domain.entity.*;
import csulzc.My_Personal_Blogger.repository.*;
import csulzc.My_Personal_Blogger.search.ArticleIndexEvent;
import csulzc.My_Personal_Blogger.search.ArticleIndexPayload;
import csulzc.My_Personal_Blogger.search.ArticleSearchService;
import csulzc.My_Personal_Blogger.security.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ArticleSearchService articleSearchService;
    private final ArticleListAssembler articleListAssembler;
    private final ApplicationEventPublisher applicationEventPublisher;

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

        // 事务提交后同步到 Elasticsearch（AFTER_COMMIT 监听器消费，失败不影响主流程）
        publishIndexEvent(savedArticle);

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

        // 事务提交后同步到 Elasticsearch（覆盖旧文档）
        publishIndexEvent(updatedArticle);

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

        // 状态变化同样需要同步索引（搜索侧文档中的 status 保持最新）
        publishIndexEvent(publishedArticle);

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

        // 状态变化同样需要同步索引
        publishIndexEvent(archivedArticle);

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
     * 获取文章列表（分页，公开列表只返回已发布文章）
     */
    @Cacheable(cacheNames = "article:list", key = "#pageable")
    public PageResponseDTO<ArticleListItemDTO> getArticleList(Pageable pageable) {
        Page<Article> articlePage = articleRepository.findAllByStatusWithAuthor(Article.ArticleStatus.RELEASE, pageable);
        return articleListAssembler.buildPageResponse(articlePage);
    }

    /**
     * 根据作者获取文章列表。
     * 作者本人或管理员可见全部状态（草稿/归档），其他访客仅可见已发布。
     * 缓存 key 包含可见性标识，避免不同身份串数据。
     */
    @Cacheable(cacheNames = "article:list",
            key = "#author.id + ':' + #pageable + ':' + @securityContextUtil.canViewAllStatuses(#author.id)")
    public PageResponseDTO<ArticleListItemDTO> getArticlesByAuthor(User author, Pageable pageable) {
        boolean viewAll = securityContextUtil.canViewAllStatuses(author.getId());
        Page<Article> articlePage = viewAll
                ? articleRepository.findByAuthorAndStatus(author, null, pageable)
                : articleRepository.findByAuthorAndStatus(author, Article.ArticleStatus.RELEASE, pageable);
        return articleListAssembler.buildPageResponse(articlePage);
    }

    /**
     * 管理端文章查询（仅管理员）：支持可选关键词，始终返回全部状态，
     * 直接走数据库（不经过 ES），保证草稿/归档也能检索到。
     */
    public PageResponseDTO<ArticleListItemDTO> getArticlesForManage(String keyword, Pageable pageable) {
        Page<Article> articlePage = (keyword != null && !keyword.isBlank())
                ? articleRepository.findByTitleContainingAndStatus(keyword.trim(), null, pageable)
                : articleRepository.findAllWithAuthor(pageable);
        return articleListAssembler.buildPageResponse(articlePage);
    }

    /**
     * 搜索文章（Elasticsearch 全文检索，未启用或异常时降级为标题 LIKE）
     */
    public PageResponseDTO<ArticleListItemDTO> searchArticles(String keyword, Pageable pageable) {
        return articleSearchService.search(keyword, pageable);
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

        // 事务提交后从 Elasticsearch 删除对应文档
        publishDeleteEvent(articleId);
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
            // 事务提交后从 Elasticsearch 批量删除
            publishBulkDeleteEvent(batch);
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
            // 批量更新为直接 SQL，需重新加载最新状态并同步索引
            publishBulkIndexEvent(batch);
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
            // 批量更新为直接 SQL，需重新加载最新状态并同步索引
            publishBulkIndexEvent(batch);
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

    /**
     * 点赞文章。
     * 注意：不再 @CacheEvict 详情缓存——每次点赞都失效缓存会让热点文章在频繁点赞时反复重建
     * 详情（每次重建还伴随额外查询），放大数据库压力。改为依赖缓存 TTL 自然过期，
     * 点赞数在缓存期内最多滞后一个 TTL 周期。
     */
    @Transactional(timeout = 10)
    public void likeArticle(Long articleId) {
        articleRepository.incrementLikeCount(articleId);
    }

    /**
     * 浏览文章。
     * 注意：同 likeArticle，不再 @CacheEvict 详情缓存，避免高并发浏览导致缓存穿透放大慢查询。
     */
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
                .author(articleListAssembler.convertToUserProfileDTO(article.getAuthor()))
                .categories(articleListAssembler.convertToCategoryDTOs(article.getCategories()))
                .build();
    }

    // ==================== Elasticsearch 同步事件发布 ====================
    // 事件在事务内发布，由 ArticleIndexEventListener 在事务提交后（AFTER_COMMIT）
    // 消费并同步到 ES；ES 不可用时自动跳过/失败降级，不影响主业务事务。

    /**
     * 发布单篇文章索引/更新事件（事务提交后覆盖 ES 文档）
     */
    private void publishIndexEvent(Article article) {
        if (article == null || article.getId() == null) {
            return;
        }
        applicationEventPublisher.publishEvent(new ArticleIndexEvent(
                this, ArticleIndexEvent.Operation.INDEX, List.of(ArticleIndexPayload.from(article, null))));
    }

    /**
     * 发布单篇文章删除事件（事务提交后删除 ES 文档）
     */
    private void publishDeleteEvent(Long articleId) {
        applicationEventPublisher.publishEvent(new ArticleIndexEvent(
                this, ArticleIndexEvent.Operation.DELETE,
                List.of(ArticleIndexPayload.builder().articleId(articleId).build())));
    }

    /**
     * 发布批量索引事件：重新加载最新文章数据（含作者与分类）后同步 ES。
     * 批量更新走直接 SQL，内存中的实体已过期，必须重新查询。
     */
    private void publishBulkIndexEvent(List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return;
        }
        List<ArticleIndexPayload> payloads = articleRepository.findByIdsWithAuthorAndCategories(articleIds).stream()
                .map(article -> ArticleIndexPayload.from(article, null))
                .toList();
        applicationEventPublisher.publishEvent(new ArticleIndexEvent(
                this, ArticleIndexEvent.Operation.BULK_INDEX, payloads));
    }

    /**
     * 发布批量删除事件（事务提交后批量删除 ES 文档）
     */
    private void publishBulkDeleteEvent(List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return;
        }
        List<ArticleIndexPayload> payloads = articleIds.stream()
                .map(id -> ArticleIndexPayload.builder().articleId(id).build())
                .toList();
        applicationEventPublisher.publishEvent(new ArticleIndexEvent(
                this, ArticleIndexEvent.Operation.BULK_DELETE, payloads));
    }
}