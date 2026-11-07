package csulzc.My_Personal_Blogger.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.Date;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EqualsAndHashCode(callSuper = true)  // 重要：包含父类字段
@SuperBuilder  // 使用 @SuperBuilder
@Table(name = "articles")
public class Article extends BaseEntity
{
    // 基础属性

    @Column(nullable = false)
    private String title;

    private String content = "";

    private String summary = "";

    private String coverImage = "";

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Article.ArticleStatus status = Article.ArticleStatus.DRAFT;

    public enum ArticleStatus
    {
        DRAFT, RELEASE, ARCHIVE
    }

    private int likeCount = 0;

    private int favoriteCount = 0;

    private int viewCount = 0;

    // 与其他类的关联关系（索引关系）

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    @ToString.Exclude
    private List<Comment> comments = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "article_category",
            joinColumns = @JoinColumn(name = "article_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private Set<Category> categories = new HashSet<>();

    // 辅助方法

    public void addComment(Comment comment) {
        comments.add(comment);
        comment.setArticle(this);
    }

    public void removeComment(Comment comment) {
        comments.remove(comment);
        comment.setArticle(null);
    }

    // Article.java 中修改
    public void addCategory(Category category) {
        if (category == null || categories.contains(category)) {
            return;
        }
        categories.add(category);
        if (category.getArticles() != null) {
            category.getArticles().add(this);
        }
    }

    public void removeCategory(Category category) {
        if (category == null || !categories.contains(category)) {
            return;
        }
        categories.remove(category);

        // 安全地移除双向关系
        if (category.getArticles() != null) {
            category.getArticles().remove(this);
        }
    }
}
