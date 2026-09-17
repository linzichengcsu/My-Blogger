package csulzc.My_Personal_Blogger.api.dto.article;

import lombok.Data;
import lombok.Builder;
import csulzc.My_Personal_Blogger.domain.entity.Article;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Set;

/**
 * 文章创建请求DTO
 */
@Data
@Builder
public class ArticleCreateRequest {

    @NotBlank(message = "标题不能为空")
    @Size(min = 5, max = 100, message = "标题长度必须在5-100之间")
    private String title;

    @NotBlank(message = "内容不能为空")
    @Size(min = 20, message = "内容至少20个字符")
    private String content;

    @Size(max = 200, message = "摘要不能超过200个字符")
    private String summary;

    private String coverImage;  // 封面图URL

    /**
     * 收藏夹 ID 列表（可选，原分类字段已降级为用户收藏夹）
     */
    private Set<Long> categoryIds;

    private Article.ArticleStatus status = Article.ArticleStatus.DRAFT;

    private List<String> tags;  // 标签
}
