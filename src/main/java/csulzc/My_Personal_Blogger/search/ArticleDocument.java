package csulzc.My_Personal_Blogger.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Elasticsearch 索引文档（与 articles 索引 mapping 一一对应）。
 *
 * <p>仅作为 ES 客户端序列化/反序列化的载体，不参与 JPA 持久化。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleDocument {

    /** 文章 ID */
    private Long id;

    /** 标题（ik_max_word 分词） */
    private String title;

    /** 摘要（ik_max_word 分词） */
    private String summary;

    /** 正文（ik_max_word 分词） */
    private String content;

    /** 状态 keyword：DRAFT / RELEASE / ARCHIVE */
    private String status;

    /** 作者 ID */
    private Long authorId;

    /** 作者显示名（keyword，展示用） */
    private String authorName;

    /** 分类 ID 列表 */
    private List<Long> categoryIds;

    /** 点赞数（可排序） */
    private Integer likeCount;

    /** 浏览数（可排序） */
    private Integer viewCount;

    /** 创建时间 epoch millis（可排序） */
    private Long createdAt;
}
