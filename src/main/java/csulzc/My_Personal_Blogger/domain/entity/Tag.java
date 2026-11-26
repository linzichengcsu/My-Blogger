package csulzc.My_Personal_Blogger.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * 文章标签（全局共享的轻量分类标记）。
 *
 * <p>与 {@link ArticleLike} 不同，标签是有主表记录的独立概念：一张文章可挂多个标签，
 * 一个标签可被多篇文章复用，二者通过 {@link ArticleTag} 中间表关联（仿点赞表的独立实体模式，
 * 不在 {@link Article} 上声明 JPA 双向关联，关联读写统一走 Repository 批量查询）。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@Entity
@Table(name = "tags")
public class Tag extends BaseEntity {

    /** 标签名称，全局唯一（大小写敏感由数据库排序规则决定，业务层按原值去重） */
    @Column(nullable = false, unique = true, length = 50)
    private String name;
}
