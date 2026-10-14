package csulzc.My_Personal_Blogger.api.dto.common;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PageRequestDTO {
    @Min(value = 0, message = "页码不能小于0")
    private int page;

    @Min(value = 1, message = "每页数量不能小于1")
    @Max(value = 100, message = "每页数量不能大于100")
    private int size;

    @Pattern(regexp = "^[a-zA-Z0-9_\\-.]+$", message = "排序字段只能包含字母、数字和下划线")
    private String sortBy;

    @Pattern(regexp = "^(asc|desc|ASC|DESC)$", message = "只能按照升序或降序排列")
    private String sortDirection;

    /** 允许排序的字段白名单（文章相关实体），避免无索引/不存在字段排序导致 filesort 或 500 */
    private static final Map<String, String> SORT_WHITELIST = Map.of(
            "createdAt", "createdAt",
            "updatedAt", "updatedAt",
            "likeCount", "likeCount",
            "viewCount", "viewCount",
            "favoriteCount", "favoriteCount",
            "id", "id"
    );

    public Pageable toPageable() {
        String safeSortBy = sortBy == null ? "createdAt" : SORT_WHITELIST.getOrDefault(sortBy, "createdAt");
        String safeDirection = sortDirection != null && "asc".equalsIgnoreCase(sortDirection) ? "asc" : "desc";
        Sort sort = Sort.by(Sort.Direction.fromString(safeDirection), safeSortBy);
        return PageRequest.of(page, size, sort);
    }
}
