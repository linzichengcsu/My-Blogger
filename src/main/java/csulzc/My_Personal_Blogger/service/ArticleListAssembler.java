package csulzc.My_Personal_Blogger.service;

import csulzc.My_Personal_Blogger.api.dto.article.ArticleListItemDTO;
import csulzc.My_Personal_Blogger.api.dto.category.CategoryDTO;
import csulzc.My_Personal_Blogger.api.dto.common.PageResponseDTO;
import csulzc.My_Personal_Blogger.api.dto.user.UserProfileDTO;
import csulzc.My_Personal_Blogger.domain.entity.Article;
import csulzc.My_Personal_Blogger.domain.entity.Category;
import csulzc.My_Personal_Blogger.domain.entity.User;
import csulzc.My_Personal_Blogger.repository.ArticleRepository;
import csulzc.My_Personal_Blogger.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文章列表 DTO 组装器。
 *
 * <p>从 {@link ArticleService} 抽取的公共组装逻辑，供数据库列表/搜索与
 * Elasticsearch 搜索结果共用：批量预加载评论数与分类映射，避免逐篇懒加载（N+1）。</p>
 */
@Component
@RequiredArgsConstructor
public class ArticleListAssembler {

    private final ArticleRepository articleRepository;
    private final CommentRepository commentRepository;

    /**
     * 由 JPA 分页结果组装列表响应（列表页 / 作者列表 / LIKE 降级搜索共用）。
     */
    public PageResponseDTO<ArticleListItemDTO> buildPageResponse(Page<Article> articlePage) {
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
     * 由 ES 搜索结果组装列表响应：按 {@code orderedIds}（ES 命中顺序）排序，
     * 并用高亮片段覆盖标题/摘要（无高亮时回退原文）。
     *
     * @param orderedIds    ES 命中顺序的文章 ID 列表
     * @param highlights    文章 ID → 字段高亮片段（可空）
     * @param totalElements 命中总数
     * @param page          当前页
     * @param size          每页大小
     */
    public PageResponseDTO<ArticleListItemDTO> buildSearchResponse(
            List<Long> orderedIds,
            Map<Long, Map<String, String>> highlights,
            long totalElements,
            int page,
            int size) {
        if (orderedIds.isEmpty()) {
            return PageResponseDTO.<ArticleListItemDTO>builder()
                    .content(List.of())
                    .totalElements(0)
                    .totalPages(0)
                    .page(page)
                    .size(size)
                    .build();
        }

        // 批量加载实体（含分类映射），按 ES 命中顺序重排
        Map<Long, Article> byId = articleRepository.findByIdsWithCategories(orderedIds).stream()
                .collect(Collectors.toMap(Article::getId, Function.identity()));
        List<Article> orderedArticles = orderedIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, Long> commentCounts = loadCommentCounts(orderedIds);
        Map<Long, Set<Category>> categoryMap = loadCategoryMappings(orderedIds);

        List<ArticleListItemDTO> content = orderedArticles.stream()
                .map(article -> {
                    ArticleListItemDTO dto = convertToListItemDTO(
                            article,
                            commentCounts.getOrDefault(article.getId(), 0L),
                            categoryMap.getOrDefault(article.getId(), Set.of()));
                    Map<String, String> hl = highlights != null ? highlights.get(article.getId()) : null;
                    if (hl != null) {
                        dto.setHighlightTitle(hl.get("title"));
                        dto.setHighlightSummary(hl.get("summary"));
                    }
                    return dto;
                })
                .collect(Collectors.toList());

        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        return PageResponseDTO.<ArticleListItemDTO>builder()
                .content(content)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .page(page)
                .size(size)
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
     * 转换为 UserProfileDTO
     */
    public UserProfileDTO convertToUserProfileDTO(User user) {
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
     * 转换 Category 列表为 CategoryDTO 列表
     */
    public List<CategoryDTO> convertToCategoryDTOs(Set<Category> categories) {
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
