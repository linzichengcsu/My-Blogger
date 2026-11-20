package csulzc.My_Personal_Blogger.api.dto.notification;

import csulzc.My_Personal_Blogger.api.dto.user.UserProfileDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {

    private Long id;

    /** COMMENT / REPLY / LIKE_ARTICLE / LIKE_COMMENT / SYSTEM */
    private String type;

    private String title;

    private String content;

    /** 触发者，系统通知时为 null */
    private UserProfileDTO sender;

    private Long articleId;

    private Long commentId;

    private Boolean isRead;

    private Instant createdAt;
}
