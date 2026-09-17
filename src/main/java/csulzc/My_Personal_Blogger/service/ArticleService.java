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

    private final ArticleRepository articleRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
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
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new EntityNotFoundException("文章不存在"));
        
        return convertToDetailDTO(article);
    }

    /**
     * 获取文章列表（分页）
     */
    @Cacheable(cacheNames = "article:list", key = "#pageable")
    public PageResponseDTO<ArticleListItemDTO> getArticleList(Pageable pageable) {
        Page<Article> articlePage = articleRepository.findAll(pageable);
        
        List<ArticleListItemDTO> content = articlePage.getContent().stream()
                .map(this::convertToListItemDTO)
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
     * 根据作者获取文章列表
     */
    @Cacheable(cacheNames = "article:list", key = "#author.id + #pageable")
    public PageResponseDTO<ArticleListItemDTO> getArticlesByAuthor(User author, Pageable pageable) {
        Page<Article> articlePage = articleRepository.findByAuthor(author, pageable);
        
        List<ArticleListItemDTO> content = articlePage.getContent().stream()
                .map(this::convertToListItemDTO)
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
     * 搜索文章（根据标题）
     */
    public PageResponseDTO<ArticleListItemDTO> searchArticles(String keyword, Pageable pageable) {
        Page<Article> articlePage = articleRepository.findByTitleContaining(keyword, pageable);
        
        List<ArticleListItemDTO> content = articlePage.getContent().stream()
                .map(this::convertToListItemDTO)
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
     * 删除文章
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

        articleRepository.delete(article);
    }

    @CacheEvict(cacheNames = "article:list", allEntries = true)
    @Transactional(timeout = 120)
    public int batchDeleteArticles(List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return 0;
        }
        return articleRepository.batchDeleteByIds(articleIds);
    }

    @CacheEvict(cacheNames = "article:list", allEntries = true)
    @Transactional(timeout = 60)
    public int batchPublishArticles(List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return 0;
        }
        return articleRepository.batchUpdateStatus(articleIds, Article.ArticleStatus.RELEASE);
    }

    @CacheEvict(cacheNames = "article:list", allEntries = true)
    @Transactional(timeout = 60)
    public int batchArchiveArticles(List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return 0;
        }
        return articleRepository.batchUpdateStatus(articleIds, Article.ArticleStatus.ARCHIVE);
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
                .commentCount(article.getComments().size())
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
     * 转换为 ArticleListItemDTO
     */
    private ArticleListItemDTO convertToListItemDTO(Article article) {
        return ArticleListItemDTO.builder()
                .id(article.getId())
                .title(article.getTitle())
                .summary(article.getSummary())
                .coverImage(article.getCoverImage())
                .createdAt(article.getCreatedAt())
                .likeCount(article.getLikeCount())
                .commentCount(article.getComments().size())
                .author(convertToUserProfileDTO(article.getAuthor()))
                .categories(convertToCategoryDTOs(article.getCategories()))
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