package csulzc.My_Personal_Blogger.repository;

import csulzc.My_Personal_Blogger.domain.entity.ArticleLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ArticleLikeRepository extends JpaRepository<ArticleLike, Long> {

    boolean existsByArticleIdAndUserId(Long articleId, Long userId);

    /**
     * 幂等插入：已存在则忽略，返回 1 表示真正插入，0 表示重复。
     * 用于 Redis 不可用时的同步降级路径。
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO article_like (article_id, user_id, created_at) " +
            "VALUES (:articleId, :userId, :createdAt)", nativeQuery = true)
    int insertIgnore(@Param("articleId") Long articleId,
                     @Param("userId") Long userId,
                     @Param("createdAt") LocalDateTime createdAt);

    @Modifying
    @Query(value = "DELETE FROM article_like WHERE article_id = :articleId", nativeQuery = true)
    int deleteByArticleId(@Param("articleId") Long articleId);

    @Modifying
    @Query(value = "DELETE FROM article_like WHERE article_id IN (:articleIds)", nativeQuery = true)
    int deleteByArticleIdIn(@Param("articleIds") List<Long> articleIds);
}
