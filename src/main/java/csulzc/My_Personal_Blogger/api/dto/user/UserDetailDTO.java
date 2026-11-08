package csulzc.My_Personal_Blogger.api.dto.user;

import csulzc.My_Personal_Blogger.api.dto.common.BaseDTO;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.EqualsAndHashCode;
import csulzc.My_Personal_Blogger.domain.entity.User;

import java.time.Instant;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserDetailDTO extends BaseDTO {
    private Long id;
    private String username;
    private String email;
    private String displayName;
    private String avatar;
    private String bio;
    private User.UserStatus status;
    private User.UserRole role;
    private Instant lastLoginAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Long articleCount;
    private Long commentCount;
    private Long favoriteCount;
}