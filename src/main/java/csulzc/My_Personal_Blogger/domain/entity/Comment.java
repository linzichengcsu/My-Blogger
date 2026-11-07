package csulzc.My_Personal_Blogger.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

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
@Table(name = "comments")
public class Comment extends BaseEntity {

    private String content;

    // 与其他类的关联关系（索引关系）

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commenter_id", nullable = false)
    private User commenter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    private Comment parentComment;

    @OneToMany(mappedBy = "parentComment", cascade = CascadeType.ALL)
    @Builder.Default
    @ToString.Exclude
    private List<Comment> replies = new ArrayList<>();

    private Integer likeCount;

    private Boolean isApproved;
}
