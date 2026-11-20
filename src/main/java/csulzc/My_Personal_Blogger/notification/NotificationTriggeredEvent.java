package csulzc.My_Personal_Blogger.notification;

import csulzc.My_Personal_Blogger.domain.entity.Notification.NotificationType;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * 通知触发事件。
 *
 * <p>业务服务（评论创建、点赞等）在主事务内发布本事件，
 * {@link NotificationEventListener} 在 AFTER_COMMIT 阶段消费并落库，
 * 保证通知写入不影响主业务事务且避免跨事务脏读。</p>
 */
@Getter
public class NotificationTriggeredEvent extends ApplicationEvent {

    private final Long receiverId;
    private final Long senderId;        // 系统通知时为 null
    private final NotificationType type;
    private final String title;
    private final String content;
    private final Long articleId;
    private final Long commentId;
    private final Instant occurredAt;

    public NotificationTriggeredEvent(Object source,
                                      Long receiverId,
                                      Long senderId,
                                      NotificationType type,
                                      String title,
                                      String content,
                                      Long articleId,
                                      Long commentId) {
        super(source);
        this.receiverId = receiverId;
        this.senderId = senderId;
        this.type = type;
        this.title = title;
        this.content = content;
        this.articleId = articleId;
        this.commentId = commentId;
        this.occurredAt = Instant.now();
    }

    // ==================== 便捷工厂方法 ====================

    /** 用户 A 评论了用户 B 的文章 */
    public static NotificationTriggeredEvent ofComment(Object source,
                                                      Long articleAuthorId,
                                                      Long commenterId,
                                                      String articleTitle,
                                                      String commentPreview,
                                                      Long articleId,
                                                      Long commentId) {
        return new NotificationTriggeredEvent(
                source,
                articleAuthorId,
                commenterId,
                NotificationType.COMMENT,
                "你的文章收到了新评论",
                String.format("在《%s》中评论：%s", articleTitle, truncate(commentPreview)),
                articleId,
                commentId
        );
    }

    /** 用户 A 回复了用户 B 的评论 */
    public static NotificationTriggeredEvent ofReply(Object source,
                                                     Long parentCommenterId,
                                                     Long replierId,
                                                     String articleTitle,
                                                     String replyPreview,
                                                     Long articleId,
                                                     Long replyId) {
        return new NotificationTriggeredEvent(
                source,
                parentCommenterId,
                replierId,
                NotificationType.REPLY,
                "你的评论收到了新回复",
                String.format("在《%s》中回复：%s", articleTitle, truncate(replyPreview)),
                articleId,
                replyId
        );
    }

    /** 用户 A 点赞了用户 B 的文章 */
    public static NotificationTriggeredEvent ofLikeArticle(Object source,
                                                           Long articleAuthorId,
                                                           Long likerId,
                                                           String articleTitle,
                                                           Long articleId) {
        return new NotificationTriggeredEvent(
                source,
                articleAuthorId,
                likerId,
                NotificationType.LIKE_ARTICLE,
                "你的文章被点赞了",
                String.format("《%s》", articleTitle),
                articleId,
                null
        );
    }

    /** 用户 A 点赞了用户 B 的评论 */
    public static NotificationTriggeredEvent ofLikeComment(Object source,
                                                           Long commenterId,
                                                           Long likerId,
                                                           String articleTitle,
                                                           Long articleId,
                                                           Long commentId) {
        return new NotificationTriggeredEvent(
                source,
                commenterId,
                likerId,
                NotificationType.LIKE_COMMENT,
                "你的评论被点赞了",
                String.format("在《%s》", articleTitle),
                articleId,
                commentId
        );
    }

    private static String truncate(String text) {
        if (text == null) return "";
        return text.length() > 120 ? text.substring(0, 120) + "…" : text;
    }
}
