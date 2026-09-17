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

    // 3. 查询某个收藏夹的所有子收藏夹
    List<Category> findByParentCategory(Category parent);

    // 4. 查询收藏夹及其文章数量
    @Query("SELECT c, COUNT(a) FROM Category c LEFT JOIN c.articles a GROUP BY c")
    List<Object[]> findAllWithArticleCount();

    // 5. 查询某篇文章的所有收藏夹
    @Query("SELECT c FROM Category c JOIN c.articles a WHERE a = :article")
    List<Category> findByArticle(@Param("article") Article article);
}