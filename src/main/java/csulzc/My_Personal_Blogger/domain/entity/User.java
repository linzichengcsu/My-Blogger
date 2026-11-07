package csulzc.My_Personal_Blogger.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)  // 重要：包含父类字段
@SuperBuilder  // 使用 @SuperBuilder
@Entity
@Table(name = "users")
public class User extends BaseEntity
{
    // 基础属性

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, length = 60)
    private String passwordHash;

    @Transient
    private String plainPassword;

    private Instant passwordUpdatedAt = Instant.now();

    private Instant lastLoginAt;

    @Column(nullable = false, unique = true)
    private String email;

    private String displayName;

    private String avatar;

    private String bio;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private UserRole role = UserRole.USER;

    public enum UserStatus
    {
        ACTIVE, INACTIVE, LOCKED
    }

    public enum UserRole
    {
        USER, ADMIN, SUPER_ADMIN
    }

    // 与其他类的关联关系（索引关系）

    @OneToMany(mappedBy = "author", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    @ToString.Exclude
    private List<Article> articles = new ArrayList<> ();

    @OneToMany(mappedBy = "commenter", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    @ToString.Exclude
    private List<Comment> comments = new ArrayList<>();

    // 辅助方法

    public void addArticle(Article article)
    {
        articles.add(article);
        article.setAuthor(this);
    }

    public void removeArticle(Article article)
    {
        articles.remove(article);
        article.setAuthor(null);
    }

    public void addComment(Comment comment)
    {
        comments.add(comment);
        comment.setCommenter(this);
    }
}
