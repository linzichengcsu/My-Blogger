package csulzc.My_Personal_Blogger.api.dto.category;

import lombok.Data;
import lombok.Builder;
import jakarta.validation.constraints.*;

/**
 * 收藏夹创建/更新请求DTO
 */
@Data
@Builder
public class CategoryRequest {

    @NotBlank(message = "收藏夹名称不能为空")
    @Size(min = 2, max = 20, message = "收藏夹名称长度必须在2-20之间")
    private String name;

    private String description;

    private Long parentCategoryId;  // 父收藏夹ID
}