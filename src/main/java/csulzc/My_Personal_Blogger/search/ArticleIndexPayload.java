package csulzc.My_Personal_Blogger.search;

import csulzc.My_Personal_Blogger.domain.entity.Article;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文章索引快照（事务提交后同步到 Elasticsearch 的载荷）。
 *
 * <p>在写操作的事务内组装好全部需要的字段（此时懒加载关联可用），
 * 由 {@link ArticleIndexEvent} 携带，{@link ArticleIndexEventListener}
 * 在事务提交后消费，避免提交后再访问已被关闭会话的懒加载属性。</p>
 *
 * <p>删除场景只使用 {@link #articleId}，其余字段可为空。</p>
 */
@Data
@Builder
public class ArticleIndexPayload {

    /** 文章 ID（必填） */
    private Long articleId;

    private String title;

    private String summary;

    private String content;

    /** 文章状态：DRAFT / RELEASE / ARCHIVE */
    private String status;

    private Long authorId;

    private String authorName;

    private List<Long> categoryIds;

    private Integer likeCount;

    private Integer viewCount;

    /** 创建时间（epoch millis，UTC），用于 ES 侧排序 */
    private Long createdAtEpochMillis;

    /**
     * 从文章实体组装索引快照。
     *
     * <p>调用方需保证 {@code article} 的作者与分类关联已加载（或在活跃事务内）。
     * 分类 ID 允许预计算后传入，避免重复触发懒加载。</p>
     */
    public static ArticleIndexPayload from(Article article, List<Long> categoryIds) {
        return ArticleIndexPayload.builder()
                .articleId(article.getId())
                .title(article.getTitle())
                .summary(article.getSummary())
                .content(article.getContent())
                .status(article.getStatus() != null ? article.getStatus().name() : null)
                .authorId(article.getAuthor() != null ? article.getAuthor().getId() : null)
                .authorName(article.getAuthor() != null ? article.getAuthor().getDisplayName() : null)
                .categoryIds(categoryIds != null ? categoryIds : article.getCategories().stream()
                        .map(c -> c.getId()).collect(Collectors.toList()))
                .likeCount(article.getLikeCount())
                .viewCount(article.getViewCount())
                .createdAtEpochMillis(toEpochMillis(article.getCreatedAt()))
                .build();
    }

    private static Long toEpochMillis(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.toInstant(ZoneOffset.UTC).toEpochMilli() : null;
    }
}
