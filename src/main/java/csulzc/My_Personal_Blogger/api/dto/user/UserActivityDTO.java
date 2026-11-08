package csulzc.My_Personal_Blogger.api.dto.user;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Data
@SuperBuilder
@NoArgsConstructor
public class UserActivityDTO {
    private Long userId;
    private String username;
    private String displayName;
    private Long articleCount;
    private Long commentCount;
    private Long likeReceived;
    private Instant lastActiveAt;
}