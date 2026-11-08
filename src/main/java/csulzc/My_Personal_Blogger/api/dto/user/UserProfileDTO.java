package csulzc.My_Personal_Blogger.api.dto.user;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Data
@SuperBuilder
@NoArgsConstructor
public class UserProfileDTO {
    private Long id;
    private String username;
    private String displayName;
    private String avatar;  // 头像URL
    private String bio;      // 个人简介
    private Instant createdAt;
    private Long articleCount;
    private Long commentCount;
    private Long followerCount;
}