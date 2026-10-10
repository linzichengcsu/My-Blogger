package csulzc.My_Personal_Blogger.repository;

import csulzc.My_Personal_Blogger.domain.entity.Comment;
import csulzc.My_Personal_Blogger.domain.entity.Article;
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
public interface CommentRepository extends BaseRepository<Comment, Long> {

    // 1. 查询文章的所有顶级评论（不是回复）—— 预加载评论者，避免转换时 N+1
    @EntityGraph(attributePaths = "commenter")
    @Query("SELECT c FROM Comment c WHERE c.article = :article AND c.parentComment IS NULL")
    Page<Comment> findByArticleAndParentCommentIsNull(@Param("article") Article article, Pageable pageable);

    // 2. 查询某个评论的所有回复 —— 预加载评论者、父评论及其评论者，避免转换时 N+1
    @EntityGraph(attributePaths = {"commenter", "parentComment", "parentComment.commenter"})
    @Query("SELECT c FROM Comment c WHERE c.parentComment = :parent")
    Page<Comment> findByParentComment(@Param("parent") Comment parent, Pageable pageable);

    // 3. 查询用户的所有评论 —— 预加载评论者，避免转换时 N+1
    @EntityGraph(attributePaths = "commenter")
    Page<Comment> findByCommenter(User commenter, Pageable pageable);

    // 4. 统计文章评论数
    long countByArticle(Article article);

    // 在 CommentRepository.java 中添加
    List<Comment> findByArticle(Article article);


    // 5. 批量删除文章的评论
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Comment c WHERE c.article = :article")
    int deleteByArticle(@Param("article") Article article);

    // 批量删除指定ID列表的评论
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Comment c WHERE c.id IN :ids")
    int batchDeleteByIds(@Param("ids") List<Long> ids);

    // 批量删除多篇文章的所有回复评论（先于顶级评论删除，避免自引用外键约束冲突）
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Comment c WHERE c.article.id IN :articleIds AND c.parentComment IS NOT NULL")
    int batchDeleteRepliesByArticleIds(@Param("articleIds") List<Long> articleIds);

    // 批量删除多篇文章的顶级评论（在回复删除后执行）
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Comment c WHERE c.article.id IN :articleIds AND c.parentComment IS NULL")
    int batchDeleteTopLevelByArticleIds(@Param("articleIds") List<Long> articleIds);

    // 删除单篇文章的所有回复评论（先于顶级评论删除，避免自引用外键约束冲突）
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Comment c WHERE c.article = :article AND c.parentComment IS NOT NULL")
    int deleteRepliesByArticle(@Param("article") Article article);

    // 删除单篇文章的顶级评论（在回复删除后执行）
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Comment c WHERE c.article = :article AND c.parentComment IS NULL")
    int deleteTopLevelByArticle(@Param("article") Article article);

    // 批量更新评论审核状态
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Comment c SET c.isApproved = :approved WHERE c.id IN :ids")
    int batchUpdateApprovalStatus(@Param("ids") List<Long> ids, @Param("approved") boolean approved);

    long countByCommenter(User user);

    long countByIsApproved(boolean isApproved);

    // ==================== N+1 优化新增方法 ====================

    // 批量统计：多篇文章的评论数（articleId -> count），供文章列表组装
    @Query("SELECT c.article.id, COUNT(c) FROM Comment c WHERE c.article.id IN :articleIds GROUP BY c.article.id")
    List<Object[]> countByArticleIds(@Param("articleIds") List<Long> articleIds);

    // 批量统计：多个顶级评论的回复数（parentCommentId -> count）
    @Query("SELECT c.parentComment.id, COUNT(c) FROM Comment c WHERE c.parentComment.id IN :parentIds GROUP BY c.parentComment.id")
    List<Object[]> countRepliesByParentIds(@Param("parentIds") List<Long> parentIds);

    // 批量查询：多个顶级评论的全部回复（预加载评论者与被回复人，内存分组后取前几条做预览）
    @EntityGraph(attributePaths = {"commenter", "parentComment", "parentComment.commenter"})
    @Query("SELECT c FROM Comment c WHERE c.parentComment.id IN :parentIds")
    List<Comment> findRepliesByParentIds(@Param("parentIds") List<Long> parentIds);

    // 批量统计：多个用户的评论数（commenterId -> count）
    @Query("SELECT c.commenter.id, COUNT(c) FROM Comment c WHERE c.commenter.id IN :userIds GROUP BY c.commenter.id")
    List<Object[]> countByCommenterIds(@Param("userIds") List<Long> userIds);

    // 管理后台：最近评论预加载评论者与文章（均 to-one，分页安全）
    @EntityGraph(attributePaths = {"commenter", "article"})
    @Query("SELECT c FROM Comment c")
    Page<Comment> findAllWithDetails(Pageable pageable);
}
