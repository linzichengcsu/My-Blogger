package csulzc.My_Personal_Blogger.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * 站内通知。
 *
 * <p>当用户的文章被评论/点赞、评论被回复/点赞时，系统自动创建一条通知推送给接收者。
 * 通知由 {@code NotificationTriggeredEvent} 领域事件在事务提交后异步创建，不阻塞主业务流程。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_receiver_read", columnList = "receiver_id, is_read"),
        @Index(name = "idx_notifications_receiver_created", columnList = "receiver_id, created_at")
})
public class Notification extends BaseEntity {

    public enum NotificationType {
        /** 你的文章被他人评论 */
        COMMENT,
        /** 你的评论被他人回复 */
        REPLY,
        /** 你的文章被点赞 */
        LIKE_ARTICLE,
        /** 你的评论被点赞 */
        LIKE_COMMENT,
        /** 系统通知（sender 为 null） */
        SYSTEM
    }

    /** 通知接收者 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    /** 触发者，系统通知时为 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationType type;

    /** 短标题，用于通知卡片 */
    private String title;

    /** 通知正文 */
    @Column(length = 1000)
    private String content;

    @Column(name = "article_id")
    private Long articleId;

    @Column(name = "comment_id")
    private Long commentId;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;
}
