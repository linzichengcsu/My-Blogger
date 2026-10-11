package csulzc.My_Personal_Blogger.search;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * 文章索引同步事件。
 *
 * <p>由 {@code ArticleService} 的写操作在事务内发布，
 * {@link ArticleIndexEventListener} 在事务提交后（AFTER_COMMIT）消费，
 * 保证 ES 与数据库最终一致且不影响主流程。</p>
 */
@Getter
public class ArticleIndexEvent extends ApplicationEvent {

    /** 同步操作类型 */
    public enum Operation {
        /** 索引/更新单篇文章 */
        INDEX,
        /** 删除单篇文章 */
        DELETE,
        /** 批量索引（按 ID 重新加载后写入） */
        BULK_INDEX,
        /** 批量删除 */
        BULK_DELETE
    }

    private final Operation operation;

    private final List<ArticleIndexPayload> payloads;

    public ArticleIndexEvent(Object source, Operation operation, List<ArticleIndexPayload> payloads) {
        super(source);
        this.operation = operation;
        this.payloads = payloads;
    }
}
