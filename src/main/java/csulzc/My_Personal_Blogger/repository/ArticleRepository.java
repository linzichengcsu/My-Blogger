package csulzc.My_Personal_Blogger.repository;

import csulzc.My_Personal_Blogger.domain.entity.Article;
import csulzc.My_Personal_Blogger.domain.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ArticleRepository extends BaseRepository<Article, Long> {

    // 1. 根据作者查询（分页）—— 预加载作者，避免列表转换时 N+1
    @EntityGraph(attributePaths = "author")
    Page<Article> findByAuthor(User author, Pageable pageable);

    // 2. 根据状态查询
    List<Article> findByStatus(Article.ArticleStatus status);

    // 3. 标题包含关键字 —— 预加载作者，避免列表转换时 N+1
    @EntityGraph(attributePaths = "author")
    Page<Article> findByTitleContaining(String keyword, Pageable pageable);

    // 4. 复杂查询：发布时间在之后，按点赞数排序
    @Query("SELECT a FROM Article a WHERE a.createdAt >= :since ORDER BY a.likeCount DESC")
    List<Article> findPopularArticlesSince(@Param("since") LocalDateTime since, Pageable pageable);

    // 5. 统计某作者的文章数
    long countByAuthor(User author);

    // 6. 更新文章状态
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Article a SET a.status = :status WHERE a.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") Article.ArticleStatus status);

    // 7. 批量查询（使用IN子句）
    @Query("SELECT a FROM Article a LEFT JOIN FETCH a.categories WHERE a.id IN :ids")
    List<Article> findByIdsWithCategories(@Param("ids") List<Long> ids);

    long countByStatus(Article.ArticleStatus status);

    @Query("SELECT SUM(a.likeCount) FROM Article a")
    Long sumLikeCount();

    @Query("SELECT SUM(a.viewCount) FROM Article a")
    Long sumViewCount();

    // 批量更新文章状态（根据ID集合）
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Article a SET a.status = :status WHERE a.id IN :ids")
    int batchUpdateStatus(@Param("ids") List<Long> ids, @Param("status") Article.ArticleStatus status);

    // 批量增加点赞数
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Article a SET a.likeCount = a.likeCount + 1 WHERE a.id = :id")
    int incrementLikeCount(@Param("id") Long id);

    // 批量增加浏览数
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Article a SET a.viewCount = a.viewCount + 1 WHERE a.id = :id")
    int incrementViewCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Article a WHERE a.id IN :articleIds")
    int batchDeleteByIds(List<Long> articleIds);

    // ==================== 批量删除优化新增方法 ====================

    // 删除单篇文章的分类关联（article_category），避免 JPA 级联逐条删除
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM article_category WHERE article_id = :articleId", nativeQuery = true)
    int deleteCategoryMappings(@Param("articleId") Long articleId);

    // 批量删除多篇文章的分类关联
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM article_category WHERE article_id IN :articleIds", nativeQuery = true)
    int batchDeleteCategoryMappings(@Param("articleIds") List<Long> articleIds);

    // 直接按 ID 删除文章（JPQL 批量删除，绕过 JPA 级联的逐条删除评论/关联）
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Article a WHERE a.id = :id")
    int deleteByIdDirect(@Param("id") Long id);

    // ==================== N+1 优化新增方法 ====================

    // 分页查询全部文章并预加载作者（to-one 关联，分页安全）
    @EntityGraph(attributePaths = "author")
    @Query(value = "SELECT a FROM Article a", countQuery = "SELECT COUNT(a) FROM Article a")
    Page<Article> findAllWithAuthor(Pageable pageable);

    // 查询文章详情并预加载作者与分类（无分页，可安全 fetch to-many）
    @EntityGraph(attributePaths = {"author", "categories"})
    @Query("SELECT a FROM Article a WHERE a.id = :id")
    Optional<Article> findDetailById(@Param("id") Long id);

    // 一次查询多篇文章的分类映射（articleId -> categories），供列表 DTO 组装，避免每篇一次懒加载
    @Query("SELECT a.id, c FROM Article a JOIN a.categories c WHERE a.id IN :ids")
    List<Object[]> findCategoryMappingByArticleIds(@Param("ids") List<Long> ids);

    // 批量统计：多个作者的文章数（authorId -> count）
    @Query("SELECT a.author.id, COUNT(a) FROM Article a WHERE a.author.id IN :userIds GROUP BY a.author.id")
    List<Object[]> countByAuthorIds(@Param("userIds") List<Long> userIds);

    // 批量统计：多个作者的获赞总数（authorId -> sum(likeCount)）
    @Query("SELECT a.author.id, COALESCE(SUM(a.likeCount), 0) FROM Article a WHERE a.author.id IN :userIds GROUP BY a.author.id")
    List<Object[]> sumLikeCountByAuthorIds(@Param("userIds") List<Long> userIds);

    // 单个作者的获赞总数（SQL 聚合，避免加载该作者全部文章实体）
    @Query("SELECT COALESCE(SUM(a.likeCount), 0) FROM Article a WHERE a.author = :author")
    long sumLikeCountByAuthor(@Param("author") User author);
}
