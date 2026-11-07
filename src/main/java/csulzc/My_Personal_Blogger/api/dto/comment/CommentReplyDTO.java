package csulzc.My_Personal_Blogger.api.dto.comment;

import csulzc.My_Personal_Blogger.api.dto.user.UserProfileDTO;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * 评论回复DTO（扁平化结构）
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class CommentReplyDTO {
    private Long id;
    private String content;
    private UserProfileDTO commenter;
    private UserProfileDTO replyToUser;  // 回复给谁
    private Instant createdAt;
}