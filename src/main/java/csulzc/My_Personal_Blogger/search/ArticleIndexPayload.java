package csulzc.My_Personal_Blogger.search;

import csulzc.My_Personal_Blogger.domain.entity.Article;
import lombok.Builder;
import lombok.Data;

/**
 * 文章索引快照（事务提交后同步到 Elasticsearch 的载荷）。
 *
 * <p>ES 只保存全文检索所需的字符串字段（标题、摘要、正文、状态），
 * 计数、作者、分类、时间等结构化数据一律以数据库为准，由 API 层组装，
 * 避免索引中维护多份易失真的派生数据。</p>
 *
 * <p>删除场景只使用 {@link #articleId}，其余字段可为空。</p>
 */
@Data
@Builder
public class ArticleIndexPayload {

    /** 文章 ID（必填，作为 ES 文档 _id） */
    private Long articleId;

    private String title;

    private String summary;

    private String content;

    /** 文章状态：DRAFT / RELEASE / ARCHIVE */
    private String status;

    /**
     * 从文章实体组装索引快照。
     */
    public static ArticleIndexPayload from(Article article) {
        return ArticleIndexPayload.builder()
                .articleId(article.getId())
                .title(article.getTitle())
                .summary(article.getSummary())
                .content(article.getContent())
                .status(article.getStatus() != null ? article.getStatus().name() : null)
                .build();
    }
}
