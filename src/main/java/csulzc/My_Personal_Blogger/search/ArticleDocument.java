package csulzc.My_Personal_Blogger.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Elasticsearch 索引文档（与 articles 索引 mapping 一一对应）。
 *
 * <p>只保留全文检索用的字符串字段；文章 ID 使用 ES 文档的 {@code _id}，
 * 计数、作者、分类、时间等结构化字段不再冗余进索引，统一由 API 层从数据库合并。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleDocument {

    /** 标题（ik_max_word 分词） */
    private String title;

    /** 摘要（ik_max_word 分词） */
    private String summary;

    /** 正文（ik_max_word 分词） */
    private String content;

    /** 状态 keyword：DRAFT / RELEASE / ARCHIVE */
    private String status;
}
