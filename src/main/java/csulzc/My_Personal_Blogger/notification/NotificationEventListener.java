package csulzc.My_Personal_Blogger.notification;

import csulzc.My_Personal_Blogger.domain.entity.Notification;
import csulzc.My_Personal_Blogger.domain.entity.Notification.NotificationType;
import csulzc.My_Personal_Blogger.repository.NotificationRepository;
import csulzc.My_Personal_Blogger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 通知事件监听器。
 *
 * <p>在主业务事务提交后（AFTER_COMMIT）消费 {@link NotificationTriggeredEvent}，
 * 独立开启新事务写入 notifications 表。任何异常只记日志，绝不影响主流程。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    /**
     * 在主业务事务提交后落库通知；若主流程无事务（如点赞 NOT_SUPPORTED），则立即执行。
     * 任何异常只记日志，绝不影响主业务返回。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public void onNotificationTriggered(NotificationTriggeredEvent event) {
        if (event == null || event.getReceiverId() == null) {
            return;
        }
        try {
            // 接收者不存在（可能已注销）则静默跳过
            var receiverOpt = userRepository.findById(event.getReceiverId());
            if (receiverOpt.isEmpty()) {
                log.debug("通知接收者 {} 不存在，跳过写入", event.getReceiverId());
                return;
            }

            Notification.NotificationBuilder builder = Notification.builder()
                    .receiver(receiverOpt.get())
                    .type(event.getType())
                    .title(event.getTitle())
                    .content(event.getContent())
                    .articleId(event.getArticleId())
                    .commentId(event.getCommentId())
                    .isRead(false);

            // sender 允许为空（系统通知），只有非空时才加载
            if (event.getSenderId() != null) {
                userRepository.findById(event.getSenderId())
                        .ifPresent(builder::sender);
            }

            notificationRepository.save(builder.build());
        } catch (Exception e) {
            // AFTER_COMMIT 监听器异常会传播，必须吞掉
            log.warn("写入通知失败（不影响主流程） receiverId={} type={}: {}",
                    event.getReceiverId(), event.getType(), e.getMessage());
        }
    }
}
