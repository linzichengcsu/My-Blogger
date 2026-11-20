package csulzc.My_Personal_Blogger.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 文章-标签关联记录（中间表）。
 *
 * <p>仿 {@link ArticleLike} 的独立实体模式：不通过 JPA {@code @ManyToMany} 自动维护，
 * 而是以独立实体暴露 {@code articleId}/{@code tagId}，由 Service 层统一增删。
 * 唯一约束 {@code (article_id, tag_id)} 保证同一文章不会重复挂载同一标签。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "article_tag",
        uniqueConstraints = @UniqueConstraint(name = "uk_article_tag", columnNames = {"article_id", "tag_id"}))
public class ArticleTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "article_id", nullable = false)
    private Long articleId;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
