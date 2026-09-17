package csulzc.My_Personal_Blogger.api.dto.category;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * 收藏夹树DTO（用于前端下拉树）
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class CategoryTreeDTO {
    private Long id;
    private String name;
    private String description;
    private Integer articleCount;
    private List<CategoryTreeDTO> children;
}