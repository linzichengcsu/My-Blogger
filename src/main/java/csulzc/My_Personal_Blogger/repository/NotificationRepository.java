package csulzc.My_Personal_Blogger.repository;

import csulzc.My_Personal_Blogger.domain.entity.Notification;
import csulzc.My_Personal_Blogger.domain.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends BaseRepository<Notification, Long> {

    /** 按接收者分页查询，预加载 sender 用于展示触发者信息 */
    @EntityGraph(attributePaths = "sender")
    @Query("SELECT n FROM Notification n WHERE n.receiver = :receiver ORDER BY n.createdAt DESC")
    Page<Notification> findByReceiverOrderByCreatedAtDesc(@Param("receiver") User receiver, Pageable pageable);

    /** 统计接收者的未读数 */
    long countByReceiverAndIsReadFalse(User receiver);

    /** 统计接收者的全部通知数 */
    long countByReceiver(User receiver);

    /** 标记单条通知为已读（校验归属，只更新属于该接收者的） */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.id = :id AND n.receiver = :receiver")
    int markAsRead(@Param("id") Long id, @Param("receiver") User receiver);

    /** 批量标记已读（只更新属于该接收者的） */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.id IN :ids AND n.receiver = :receiver")
    int batchMarkAsRead(@Param("ids") List<Long> ids, @Param("receiver") User receiver);

    /** 将接收者全部通知标记为已读 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.receiver = :receiver AND n.isRead = false")
    int markAllAsRead(@Param("receiver") User receiver);

    /** 删除单条（校验归属） */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Notification n WHERE n.id = :id AND n.receiver = :receiver")
    int deleteByIdAndReceiver(@Param("id") Long id, @Param("receiver") User receiver);
}
