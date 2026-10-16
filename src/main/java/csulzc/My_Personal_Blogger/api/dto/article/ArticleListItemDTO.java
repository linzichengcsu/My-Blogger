package csulzc.My_Personal_Blogger.api.dto.article;

import csulzc.My_Personal_Blogger.api.dto.common.BaseDTO;
import csulzc.My_Personal_Blogger.api.dto.category.CategoryDTO;
import csulzc.My_Personal_Blogger.api.dto.user.UserProfileDTO;
import csulzc.My_Personal_Blogger.domain.entity.Article;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章列表项DTO（用于列表页）
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class ArticleListItemDTO extends BaseDTO {
    private Long id;
    private String title;
    private String summary;
    private String coverImage;
    private UserProfileDTO author;
    private List<CategoryDTO> categories;
    private LocalDateTime createdAt;
    private Article.ArticleStatus status;
    private Integer likeCount;
    private Integer commentCount;
    private Integer favoriteCount;

    /** Elasticsearch 命中高亮后的标题片段（含 &lt;em&gt; 标签，可为空） */
    private String highlightTitle;

    /** Elasticsearch 命中高亮后的摘要片段（含 &lt;em&gt; 标签，可为空） */
    private String highlightSummary;
}