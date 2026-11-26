package csulzc.My_Personal_Blogger.service.business;

import csulzc.My_Personal_Blogger.api.dto.common.PageResponseDTO;
import csulzc.My_Personal_Blogger.api.dto.notification.NotificationCountDTO;
import csulzc.My_Personal_Blogger.api.dto.notification.NotificationDTO;
import csulzc.My_Personal_Blogger.api.dto.user.UserProfileDTO;
import csulzc.My_Personal_Blogger.domain.entity.Notification;
import csulzc.My_Personal_Blogger.domain.entity.User;
import csulzc.My_Personal_Blogger.repository.NotificationRepository;
import csulzc.My_Personal_Blogger.security.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SecurityContextUtil securityContextUtil;

    /**
     * 获取当前登录用户的通知列表（按创建时间倒序）。
     */
    public PageResponseDTO<NotificationDTO> listNotifications(int page, int size) {
        User currentUser = securityContextUtil.getCurrentUserAndValidateStatus();
        Pageable pageable = PageRequest.of(page, size);
        Page<Notification> notificationPage =
                notificationRepository.findByReceiverOrderByCreatedAtDesc(currentUser, pageable);

        List<NotificationDTO> content = notificationPage.getContent().stream()
                .map(this::convertToDTO)
                .toList();

        return PageResponseDTO.<NotificationDTO>builder()
                .content(content)
                .page(notificationPage.getNumber())
                .size(notificationPage.getSize())
                .totalElements(notificationPage.getTotalElements())
                .totalPages(notificationPage.getTotalPages())
                .first(notificationPage.isFirst())
                .last(notificationPage.isLast())
                .build();
    }

    /**
     * 获取未读数量 + 总数。
     */
    public NotificationCountDTO getCounts() {
        User currentUser = securityContextUtil.getCurrentUserAndValidateStatus();
        long unread = notificationRepository.countByReceiverAndIsReadFalse(currentUser);
        long total = notificationRepository.countByReceiver(currentUser);
        return NotificationCountDTO.builder()
                .unread(unread)
                .total(total)
                .build();
    }

    /**
     * 标记单条通知为已读（校验归属）。
     */
    @Transactional(timeout = 10)
    public boolean markAsRead(Long notificationId) {
        User currentUser = securityContextUtil.getCurrentUserAndValidateStatus();
        int updated = notificationRepository.markAsRead(notificationId, currentUser);
        return updated > 0;
    }

    /**
     * 批量标记已读（校验归属）。
     */
    @Transactional(timeout = 15)
    public int batchMarkAsRead(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        User currentUser = securityContextUtil.getCurrentUserAndValidateStatus();
        return notificationRepository.batchMarkAsRead(ids, currentUser);
    }

    /**
     * 全部标记为已读。
     */
    @Transactional(timeout = 15)
    public int markAllAsRead() {
        User currentUser = securityContextUtil.getCurrentUserAndValidateStatus();
        return notificationRepository.markAllAsRead(currentUser);
    }

    /**
     * 删除单条通知（校验归属）。
     */
    @Transactional(timeout = 10)
    public boolean deleteNotification(Long notificationId) {
        User currentUser = securityContextUtil.getCurrentUserAndValidateStatus();
        int deleted = notificationRepository.deleteByIdAndReceiver(notificationId, currentUser);
        return deleted > 0;
    }

    // ==================== 私有转换 ====================

    private NotificationDTO convertToDTO(Notification n) {
        UserProfileDTO senderDTO = null;
        if (n.getSender() != null) {
            senderDTO = UserProfileDTO.builder()
                    .id(n.getSender().getId())
                    .username(n.getSender().getUsername())
                    .displayName(n.getSender().getDisplayName())
                    .avatar(n.getSender().getAvatar())
                    .bio(n.getSender().getBio())
                    .build();
        }

        return NotificationDTO.builder()
                .id(n.getId())
                .type(n.getType().name())
                .title(n.getTitle())
                .content(n.getContent())
                .sender(senderDTO)
                .articleId(n.getArticleId())
                .commentId(n.getCommentId())
                .isRead(n.getIsRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
