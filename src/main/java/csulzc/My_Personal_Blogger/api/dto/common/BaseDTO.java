package csulzc.My_Personal_Blogger.api.dto.common;

import lombok.Data;
import lombok.experimental.SuperBuilder;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 基础DTO，包含通用字段
 */
@Data
@SuperBuilder
@NoArgsConstructor
public abstract class BaseDTO {
    private Long id;
    private Instant createdAt;
    private Instant updatedAt;
}