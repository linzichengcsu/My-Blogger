package csulzc.My_Personal_Blogger.repository;

import csulzc.My_Personal_Blogger.domain.entity.Tag;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TagRepository extends BaseRepository<Tag, Long> {

    Optional<Tag> findByName(String name);

    List<Tag> findByNameIn(Collection<String> names);

    boolean existsByName(String name);

    /**
     * 一次查询多篇文章的标签映射：[articleId, Tag]，供列表 DTO 组装，避免逐篇懒加载（N+1）。
     * ArticleTag 与 Tag 之间无 JPA 关联，使用 theta join + ON 条件。
     */
    @Query("SELECT at.articleId, t FROM ArticleTag at, Tag t " +
            "WHERE at.tagId = t.id AND at.articleId IN :ids " +
            "ORDER BY at.articleId, t.id")
    List<Object[]> findTagMappingByArticleIds(@Param("ids") List<Long> ids);

    /**
     * 全量标签及其文章数（含 0）：[Tag, articleCount]，供标签列表/标签云组装。
     */
    @Query("SELECT t, COUNT(at) FROM Tag t LEFT JOIN ArticleTag at ON at.tagId = t.id GROUP BY t")
    List<Object[]> findAllWithArticleCount();
}
