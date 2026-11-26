package csulzc.My_Personal_Blogger.repository;

import csulzc.My_Personal_Blogger.domain.entity.ArticleTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArticleTagRepository extends JpaRepository<ArticleTag, Long> {

    boolean existsByArticleIdAndTagId(Long articleId, Long tagId);

    List<ArticleTag> findByArticleId(Long articleId);

    /**
     * 文章删除时清理其标签关联（JPQL，兼容 H2 测试库与 MySQL 生产库）。
     * 仅 flush 不清空 PC：ArticleTag 是独立实体、Article 上无托管关联集合，
     * 无需 clearAutomatically（清空 PC 反而会导致同事务后续懒加载代理失效）。
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ArticleTag at WHERE at.articleId = :articleId")
    int deleteByArticleId(@Param("articleId") Long articleId);

    /** 批量删除多篇文章的标签关联 */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ArticleTag at WHERE at.articleId IN :articleIds")
    int deleteByArticleIdIn(@Param("articleIds") List<Long> articleIds);

    /** 统计某标签被多少篇文章引用，用于删除前校验 */
    long countByTagId(Long tagId);
}
