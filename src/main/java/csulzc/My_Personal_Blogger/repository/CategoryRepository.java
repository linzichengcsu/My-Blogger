package csulzc.My_Personal_Blogger.repository;

import csulzc.My_Personal_Blogger.domain.entity.Category;
import csulzc.My_Personal_Blogger.domain.entity.Article;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends BaseRepository<Category, Long> {

    // 1. 根据名称查询
    Optional<Category> findByName(String name);

    // 2. 查询顶级收藏夹（没有父收藏夹）
    List<Category> findByParentCategoryIsNull();

    // 2.1 统计顶级收藏夹数量（避免加载全部顶级收藏夹再 size()）
    long countByParentCategoryIsNull();

    // 3. 查询某个收藏夹的所有子收藏夹
    List<Category> findByParentCategory(Category parent);

    // 4. 查询收藏夹及其文章数量
    @Query("SELECT c, COUNT(a) FROM Category c LEFT JOIN c.articles a GROUP BY c")
    List<Object[]> findAllWithArticleCount();

    // 5. 查询某篇文章的所有收藏夹
    @Query("SELECT c FROM Category c JOIN c.articles a WHERE a = :article")
    List<Category> findByArticle(@Param("article") Article article);

    // ==================== 全表加载优化新增方法 ====================

    // 按名称或描述模糊搜索（忽略大小写），避免 findAll 后在内存过滤
    @Query("SELECT c FROM Category c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR (c.description IS NOT NULL AND LOWER(c.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Category> searchByNameOrDescription(@Param("keyword") String keyword);

    // 查询有文章的分类（JOIN 去重），避免 findAll 后在内存判断文章集合
    @Query("SELECT DISTINCT c FROM Category c JOIN c.articles a")
    List<Category> findWithArticles();
}