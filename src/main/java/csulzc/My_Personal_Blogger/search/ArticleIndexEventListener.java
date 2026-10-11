package csulzc.My_Personal_Blogger.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 文章索引同步监听器。
 *
 * <p>在事务提交后（AFTER_COMMIT）消费 {@link ArticleIndexEvent}，将写操作同步到
 * Elasticsearch，保证 ES 与数据库最终一致。同步失败只记日志，绝不向上抛出，
 * 不影响主业务事务的提交结果。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleIndexEventListener {

    private final ArticleSearchService articleSearchService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onArticleIndexEvent(ArticleIndexEvent event) {
        if (event == null || event.getPayloads() == null || event.getPayloads().isEmpty()) {
            return;
        }
        try {
            switch (event.getOperation()) {
                case INDEX -> articleSearchService.indexArticle(event.getPayloads().get(0));
                case DELETE -> articleSearchService.deleteArticle(event.getPayloads().get(0).getArticleId());
                case BULK_INDEX -> articleSearchService.bulkIndex(event.getPayloads());
                case BULK_DELETE -> articleSearchService.bulkDelete(
                        event.getPayloads().stream().map(ArticleIndexPayload::getArticleId).toList());
                default -> log.warn("未知的文章索引事件类型：{}", event.getOperation());
            }
        } catch (Exception e) {
            // AFTER_COMMIT 阶段的异常会传播回调用方，必须吞掉以保证主流程不受影响
            log.warn("同步文章到 Elasticsearch 失败（不影响主流程）：{}", e.getMessage(), e);
        }
    }
}
