package csulzc.My_Personal_Blogger.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import csulzc.My_Personal_Blogger.api.dto.article.ArticleListItemDTO;
import csulzc.My_Personal_Blogger.api.dto.common.PageResponseDTO;
import csulzc.My_Personal_Blogger.config.ElasticsearchProperties;
import csulzc.My_Personal_Blogger.domain.entity.Article;
import csulzc.My_Personal_Blogger.repository.ArticleRepository;
import csulzc.My_Personal_Blogger.service.business.ArticleListAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ArticleSearchService 搜索降级与熔断逻辑测试（纯 Mockito，不依赖真实 ES）。
 *
 * <p>注意：ElasticsearchClient.search 有 {@code (Function, Class)} 与
 * {@code (Function, Type)} 两个重载，业务代码走 Class 版，mock/verify 必须用
 * {@code eq(ArticleDocument.class)} 才能命中同一方法。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ArticleSearchService 搜索降级/熔断测试")
class ArticleSearchServiceTest {

    @Mock
    private ElasticsearchProperties properties;
    @Mock
    private ObjectProvider<ElasticsearchClient> clientProvider;
    @Mock
    private ElasticsearchClient client;
    @Mock
    private ArticleRepository articleRepository;
    @Mock
    private ArticleListAssembler articleListAssembler;

    private ArticleSearchService service;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getIndexName()).thenReturn("articles");
        service = new ArticleSearchService(clientProvider, properties, articleRepository, articleListAssembler);
    }

    private PageResponseDTO<ArticleListItemDTO> emptyResponse() {
        return PageResponseDTO.<ArticleListItemDTO>builder()
                .content(List.of()).totalElements(0).totalPages(0).page(0).size(10).build();
    }

    @Test
    @DisplayName("未启用 ES 时直接走数据库 LIKE")
    void search_fallsBackToJpa_whenDisabled() {
        when(properties.isEnabled()).thenReturn(false);
        when(articleListAssembler.buildPageResponse(any())).thenReturn(emptyResponse());

        PageResponseDTO<ArticleListItemDTO> result = service.search("关键字", anyPageable());

        assertThat(result).isNotNull();
        verify(articleRepository).findByTitleContainingAndStatus(
                eq("关键字"), eq(Article.ArticleStatus.RELEASE), any(Pageable.class));
        verifyNoInteractions(clientProvider);
    }

    @Test
    @DisplayName("启用但无 ES 客户端 Bean 时走数据库 LIKE")
    void search_fallsBackToJpa_whenClientBeanMissing() {
        when(properties.isEnabled()).thenReturn(true);
        when(clientProvider.getIfAvailable()).thenReturn(null);
        when(articleListAssembler.buildPageResponse(any())).thenReturn(emptyResponse());

        service.search("关键字", anyPageable());

        verify(articleRepository).findByTitleContainingAndStatus(
                eq("关键字"), eq(Article.ArticleStatus.RELEASE), any(Pageable.class));
    }

    @Test
    @DisplayName("ES 查询成功时走 ES，不查询数据库")
    void search_usesElasticsearch_whenAvailable() throws IOException {
        when(properties.isEnabled()).thenReturn(true);
        when(clientProvider.getIfAvailable()).thenReturn(client);
        when(clientProvider.getObject()).thenReturn(client);

        SearchResponse<ArticleDocument> response = mock(SearchResponse.class);
        HitsMetadata<ArticleDocument> hits = mock(HitsMetadata.class);
        when(response.hits()).thenReturn(hits);
        when(hits.hits()).thenReturn(List.of());
        when(hits.total()).thenReturn(null);
        doReturn(response).when(client).search(any(Function.class), eq(ArticleDocument.class));
        when(articleListAssembler.buildSearchResponse(anyList(), anyMap(), anyLong(), anyInt(), anyInt()))
                .thenReturn(emptyResponse());

        PageResponseDTO<ArticleListItemDTO> result = service.search("关键字", anyPageable());

        assertThat(result).isNotNull();
        verify(client).search(any(Function.class), eq(ArticleDocument.class));
        verifyNoInteractions(articleRepository);
    }

    @Test
    @DisplayName("显式指定结构化排序时直接走数据库，不调用 ES")
    void search_usesJpa_whenExplicitSort() {
        when(properties.isEnabled()).thenReturn(true);
        when(clientProvider.getIfAvailable()).thenReturn(client);
        when(articleListAssembler.buildPageResponse(any())).thenReturn(emptyResponse());
        Pageable sorted = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

        service.search("关键字", sorted);

        verify(articleRepository).findByTitleContainingAndStatus(
                eq("关键字"), eq(Article.ArticleStatus.RELEASE), eq(sorted));
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("ES 查询异常时降级为数据库 LIKE")
    void search_fallsBackToJpa_whenElasticsearchFails() throws IOException {
        when(properties.isEnabled()).thenReturn(true);
        when(clientProvider.getIfAvailable()).thenReturn(client);
        when(clientProvider.getObject()).thenReturn(client);
        doThrow(new IOException("connection refused"))
                .when(client).search(any(Function.class), eq(ArticleDocument.class));
        when(articleListAssembler.buildPageResponse(any())).thenReturn(emptyResponse());

        service.search("关键字", anyPageable());

        verify(articleRepository).findByTitleContainingAndStatus(
                eq("关键字"), eq(Article.ArticleStatus.RELEASE), any(Pageable.class));
    }

    @Test
    @DisplayName("ES 失败后熔断：下一次搜索直接走数据库，不再调用 ES")
    void search_circuitBreaks_afterFailure() throws IOException {
        when(properties.isEnabled()).thenReturn(true);
        when(clientProvider.getIfAvailable()).thenReturn(client);
        when(clientProvider.getObject()).thenReturn(client);
        doThrow(new IOException("boom"))
                .when(client).search(any(Function.class), eq(ArticleDocument.class));
        when(articleListAssembler.buildPageResponse(any())).thenReturn(emptyResponse());

        // 第一次：尝试 ES 失败 → 降级
        service.search("关键字", anyPageable());
        verify(client, times(1)).search(any(Function.class), eq(ArticleDocument.class));

        // 第二次：熔断生效，直接走 JPA，不再调用 ES
        service.search("关键字", anyPageable());
        verify(client, times(1)).search(any(Function.class), eq(ArticleDocument.class));
        verify(articleRepository, times(2)).findByTitleContainingAndStatus(
                eq("关键字"), eq(Article.ArticleStatus.RELEASE), any(Pageable.class));
    }

    private Pageable anyPageable() {
        return org.springframework.data.domain.PageRequest.of(0, 10);
    }
}
