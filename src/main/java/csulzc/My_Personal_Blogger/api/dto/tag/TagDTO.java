package csulzc.My_Personal_Blogger.api.dto.tag;

import csulzc.My_Personal_Blogger.api.dto.common.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 标签 DTO
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TagDTO extends BaseDTO {
    private String name;
    private Integer articleCount;
}
