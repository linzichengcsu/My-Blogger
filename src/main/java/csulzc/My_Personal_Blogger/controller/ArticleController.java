package csulzc.My_Personal_Blogger.controller;

import csulzc.My_Personal_Blogger.api.dto.article.*;
import csulzc.My_Personal_Blogger.api.dto.common.PageRequestDTO;
import csulzc.My_Personal_Blogger.api.dto.common.PageResponseDTO;
import csulzc.My_Personal_Blogger.api.dto.common.BatchIdRequest;
import csulzc.My_Personal_Blogger.api.response.Result;
import csulzc.My_Personal_Blogger.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
@Tag(name = "文章管理", description = "文章CRUD及发布归档操作")
public class ArticleController {

    private final ArticleService articleService;
    private final UserService userService;

    @PostMapping
    @Operation(summary = "创建文章", description = "需要登录，自动使用当前用户作为作者")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<ArticleDetailDTO>> createArticle(
            @Valid @RequestBody ArticleCreateRequest request) {
        ArticleDetailDTO article = articleService.createArticle(request);
        return ResponseEntity.ok(Result.success(article, "文章创建成功"));
    }

    @PutMapping("/{articleId}")
    @Operation(summary = "更新文章", description = "需要登录且为文章作者或管理员")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<ArticleDetailDTO>> updateArticle(
            @PathVariable Long articleId,
            @Valid @RequestBody ArticleUpdateRequest request) {
        if (articleId == null || articleId <= 0) {
            throw new IllegalArgumentException("文章ID无效");
        }
        ArticleDetailDTO article = articleService.updateArticle(articleId, request);
        return ResponseEntity.ok(Result.success(article, "文章更新成功"));
    }

    @PostMapping("/{articleId}/publish")
    @Operation(summary = "发布文章", description = "需要登录且为文章作者或管理员")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<ArticleDetailDTO>> publishArticle(
            @PathVariable Long articleId) {
        if (articleId == null || articleId <= 0) {
            throw new IllegalArgumentException("文章ID无效");
        }
        ArticleDetailDTO article = articleService.publishArticle(articleId);
        return ResponseEntity.ok(Result.success(article, "文章发布成功"));
    }

    @PostMapping("/{articleId}/archive")
    @Operation(summary = "归档文章", description = "需要登录且为文章作者或管理员")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<ArticleDetailDTO>> archiveArticle(
            @PathVariable Long articleId) {
        if (articleId == null || articleId <= 0) {
            throw new IllegalArgumentException("文章ID无效");
        }
        ArticleDetailDTO article = articleService.archiveArticle(articleId);
        return ResponseEntity.ok(Result.success(article, "文章归档成功"));
    }

    @GetMapping("/{articleId}")
    @Operation(summary = "获取文章详情", description = "公开访问")
    public ResponseEntity<Result<ArticleDetailDTO>> getArticleById(@PathVariable Long articleId) {
        if (articleId == null || articleId <= 0) {
            throw new IllegalArgumentException("文章ID无效");
        }
        ArticleDetailDTO article = articleService.getArticleById(articleId);
        return ResponseEntity.ok(Result.success(article));
    }

    @GetMapping
    @Operation(summary = "获取文章列表", description = "公开访问，支持分页")
    public ResponseEntity<Result<PageResponseDTO<ArticleListItemDTO>>> getArticleList(
            @ModelAttribute PageRequestDTO pageRequest) {
        if (pageRequest == null) {
            throw new IllegalArgumentException("分页参数不能为空");
        }
        if (pageRequest.getPage() < 0) {
            throw new IllegalArgumentException("页码不能为负数");
        }
        if (pageRequest.getSize() <= 0 || pageRequest.getSize() > 100) {
            throw new IllegalArgumentException("每页大小必须在1-100之间");
        }
        Pageable pageable = pageRequest.toPageable();
        PageResponseDTO<ArticleListItemDTO> articles = articleService.getArticleList(pageable);
        return ResponseEntity.ok(Result.success(articles));
    }

    @GetMapping("/author/{authorId}")
    @Operation(summary = "获取作者的文章列表", description = "公开访问")
    public ResponseEntity<Result<PageResponseDTO<ArticleListItemDTO>>> getArticlesByAuthor(
            @PathVariable Long authorId,
            @ModelAttribute PageRequestDTO pageRequest) {
        if (authorId == null || authorId <= 0) {
            throw new IllegalArgumentException("作者ID无效");
        }
        if (pageRequest == null) {
            throw new IllegalArgumentException("分页参数不能为空");
        }
        if (pageRequest.getPage() < 0) {
            throw new IllegalArgumentException("页码不能为负数");
        }
        if (pageRequest.getSize() <= 0 || pageRequest.getSize() > 100) {
            throw new IllegalArgumentException("每页大小必须在1-100之间");
        }
        var user = userService.getUserDetail(authorId);
        var author = new csulzc.My_Personal_Blogger.domain.entity.User();
        author.setId(user.getId());
        author.setUsername(user.getUsername());
        author.setDisplayName(user.getDisplayName());
        author.setAvatar(user.getAvatar());
        author.setBio(user.getBio());
        author.setCreatedAt(user.getCreatedAt());
        Pageable pageable = pageRequest.toPageable();
        PageResponseDTO<ArticleListItemDTO> articles = articleService.getArticlesByAuthor(author, pageable);
        return ResponseEntity.ok(Result.success(articles));
    }

    @GetMapping("/manage")
    @Operation(summary = "管理端文章列表", description = "仅管理员，支持可选关键词搜索，返回全部状态文章（含草稿/归档）")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Result<PageResponseDTO<ArticleListItemDTO>>> getArticlesForManage(
            @RequestParam(required = false) String keyword,
            @ModelAttribute PageRequestDTO pageRequest) {
        if (pageRequest == null) {
            throw new IllegalArgumentException("分页参数不能为空");
        }
        if (pageRequest.getPage() < 0) {
            throw new IllegalArgumentException("页码不能为负数");
        }
        if (pageRequest.getSize() <= 0 || pageRequest.getSize() > 100) {
            throw new IllegalArgumentException("每页大小必须在1-100之间");
        }
        Pageable pageable = pageRequest.toPageable();
        PageResponseDTO<ArticleListItemDTO> articles = articleService.getArticlesForManage(keyword, pageable);
        return ResponseEntity.ok(Result.success(articles));
    }

    @GetMapping("/search")
    @Operation(summary = "搜索文章", description = "公开访问，仅返回已发布文章；默认按相关度走 Elasticsearch 全文检索，"
            + "未启用、异常或显式指定结构化排序时自动降级为标题模糊匹配")
    public ResponseEntity<Result<PageResponseDTO<ArticleListItemDTO>>> searchArticles(
            @RequestParam String keyword,
            @ModelAttribute PageRequestDTO pageRequest) {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("搜索关键词不能为空");
        }
        if (keyword.trim().length() > 100) {
            throw new IllegalArgumentException("搜索关键词长度不能超过100个字符");
        }
        if (pageRequest == null) {
            throw new IllegalArgumentException("分页参数不能为空");
        }
        if (pageRequest.getPage() < 0) {
            throw new IllegalArgumentException("页码不能为负数");
        }
        if (pageRequest.getSize() <= 0 || pageRequest.getSize() > 100) {
            throw new IllegalArgumentException("每页大小必须在1-100之间");
        }
        // 未显式指定排序字段时按相关性（ES _score）返回，而非默认时间倒序
        Pageable pageable = pageRequest.getSortBy() == null || pageRequest.getSortBy().trim().isEmpty()
                ? PageRequest.of(pageRequest.getPage(), pageRequest.getSize())
                : pageRequest.toPageable();
        PageResponseDTO<ArticleListItemDTO> articles = articleService.searchArticles(keyword.trim(), pageable);
        return ResponseEntity.ok(Result.success(articles));
    }

    @DeleteMapping("/{articleId}")
    @Operation(summary = "删除文章", description = "需要登录且为文章作者或管理员")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<Void>> deleteArticle(
            @PathVariable Long articleId) {
        if (articleId == null || articleId <= 0) {
            throw new IllegalArgumentException("文章ID无效");
        }
        articleService.deleteArticle(articleId);
        return ResponseEntity.ok(Result.success(null, "文章删除成功"));
    }

    @PostMapping("/batch/publish")
    @Operation(summary = "批量发布文章", description = "需要管理员权限")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<Integer>> batchPublishArticles(
            @Valid @RequestBody BatchIdRequest request) {
        if (request.getIds() == null || request.getIds().isEmpty()) {
            throw new IllegalArgumentException("文章ID列表不能为空");
        }
        int count = articleService.batchPublishArticles(request.getIds());
        return ResponseEntity.ok(Result.success(count, "批量发布成功，共发布 " + count + " 篇文章"));
    }

    @PostMapping("/batch/archive")
    @Operation(summary = "批量归档文章", description = "需要管理员权限")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<Integer>> batchArchiveArticles(
            @Valid @RequestBody BatchIdRequest request) {
        if (request.getIds() == null || request.getIds().isEmpty()) {
            throw new IllegalArgumentException("文章ID列表不能为空");
        }
        int count = articleService.batchArchiveArticles(request.getIds());
        return ResponseEntity.ok(Result.success(count, "批量归档成功，共归档 " + count + " 篇文章"));
    }

    @PostMapping("/batch/delete")
    @Operation(summary = "批量删除文章", description = "需要管理员权限")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<Integer>> batchDeleteArticles(
            @Valid @RequestBody BatchIdRequest request) {
        if (request.getIds() == null || request.getIds().isEmpty()) {
            throw new IllegalArgumentException("文章ID列表不能为空");
        }
        int count = articleService.batchDeleteArticles(request.getIds());
        return ResponseEntity.ok(Result.success(count, "批量删除成功，共删除 " + count + " 篇文章"));
    }

    @PostMapping("/{articleId}/like")
    @Operation(summary = "点赞文章", description = "需要登录，同一用户对同一文章只能点赞一次")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<Void>> likeArticle(@PathVariable Long articleId) {
        if (articleId == null || articleId <= 0) {
            throw new IllegalArgumentException("文章ID无效");
        }
        boolean accepted = articleService.likeArticle(articleId);
        return ResponseEntity.ok(Result.success(null, accepted ? "点赞成功" : "请勿重复点赞"));
    }

    @PostMapping("/{articleId}/view")
    @Operation(summary = "浏览文章", description = "公开访问，记录浏览数")
    public ResponseEntity<Result<Void>> viewArticle(@PathVariable Long articleId) {
        if (articleId == null || articleId <= 0) {
            throw new IllegalArgumentException("文章ID无效");
        }
        articleService.viewArticle(articleId);
        return ResponseEntity.ok(Result.success(null, "记录浏览成功"));
    }
}