package csulzc.My_Personal_Blogger.api.dto.comment;

import csulzc.My_Personal_Blogger.api.dto.common.BaseDTO;
import csulzc.My_Personal_Blogger.api.dto.user.UserProfileDTO;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.util.List;

/**
 * 评论DTO
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CommentDTO extends BaseDTO {
    private Long id;
    private String content;
    private UserProfileDTO commenter;
    private Long articleId;
    private Long parentCommentId;
    private Integer replyCount;
    private List<CommentDTO> replies;  // 回复列表（懒加载）
    private Integer likeCount;
    private Instant createdAt;
    private Boolean isLiked;
}