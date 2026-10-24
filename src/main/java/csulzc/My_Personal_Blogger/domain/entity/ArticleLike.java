package csulzc.My_Personal_Blogger.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文章点赞记录（用于点赞去重）。
 *
 * <p>唯一约束 {@code (article_id, user_id)} 保证同一用户对同一文章只能点赞一次；
 * 记录由异步批量落库写入，请求路径只依赖 Redis。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "article_like",
        uniqueConstraints = @UniqueConstraint(name = "uk_article_like", columnNames = {"article_id", "user_id"}))
public class ArticleLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "article_id", nullable = false)
    private Long articleId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
