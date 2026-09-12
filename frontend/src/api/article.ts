/**
 * 文章管理接口（CRUD / 发布归档 / 点赞浏览 / 批量操作 / 搜索）。
 * 对应 openapi.yaml：/api/articles/**
 * 权限提示：SecurityConfig 仅对 GET /api/articles/** 放行（公开读取），
 * 其余方法（POST/PUT/DELETE，包括 view 与 like）都需要认证。
 */
import { http } from './request'
import type { BatchIdRequest, PageQuery, PageResponse } from '../types/common'
import type {
  ArticleCreateRequest,
  ArticleDetailDTO,
  ArticleListItemDTO,
  ArticleUpdateRequest,
} from '../types/article'

/** 获取文章列表（公开，支持分页） */
export function getArticleList(query: PageQuery = {}): Promise<PageResponse<ArticleListItemDTO>> {
  return http.get<PageResponse<ArticleListItemDTO>>('/articles', { params: query })
}

/** 获取文章详情（公开） */
export function getArticleById(articleId: number): Promise<ArticleDetailDTO> {
  return http.get<ArticleDetailDTO>(`/articles/${articleId}`)
}

/** 创建文章（需登录，当前用户为作者） */
export function createArticle(data: ArticleCreateRequest): Promise<ArticleDetailDTO> {
  return http.post<ArticleDetailDTO>('/articles', data)
}

/** 更新文章（作者或管理员） */
export function updateArticle(articleId: number, data: ArticleUpdateRequest): Promise<ArticleDetailDTO> {
  return http.put<ArticleDetailDTO>(`/articles/${articleId}`, data)
}

/** 删除文章（作者或管理员） */
export function deleteArticle(articleId: number): Promise<void> {
  return http.del<void>(`/articles/${articleId}`)
}

/* ---------------- 单篇操作 ---------------- */

/** 发布文章（作者或管理员） */
export function publishArticle(articleId: number): Promise<ArticleDetailDTO> {
  return http.post<ArticleDetailDTO>(`/articles/${articleId}/publish`)
}

/** 归档文章（作者或管理员） */
export function archiveArticle(articleId: number): Promise<ArticleDetailDTO> {
  return http.post<ArticleDetailDTO>(`/articles/${articleId}/archive`)
}

/** 点赞文章（需登录） */
export function likeArticle(articleId: number): Promise<void> {
  return http.post<void>(`/articles/${articleId}/like`)
}

/** 浏览文章（需登录，SecurityConfig 仅对 GET /api/articles/** 放行） */
export function viewArticle(articleId: number): Promise<void> {
  return http.post<void>(`/articles/${articleId}/view`)
}

/* ---------------- 批量操作 ---------------- */

/** 批量发布文章（后端要求登录，业务上供管理员使用） */
export function batchPublishArticles(ids: number[]): Promise<number> {
  return http.post<number>('/articles/batch/publish', { ids } satisfies BatchIdRequest)
}

/** 批量删除文章（后端要求登录，业务上供管理员使用） */
export function batchDeleteArticles(ids: number[]): Promise<number> {
  return http.post<number>('/articles/batch/delete', { ids } satisfies BatchIdRequest)
}

/** 批量归档文章（后端要求登录，业务上供管理员使用） */
export function batchArchiveArticles(ids: number[]): Promise<number> {
  return http.post<number>('/articles/batch/archive', { ids } satisfies BatchIdRequest)
}

/* ---------------- 搜索 / 按作者 ---------------- */

/** 搜索文章（公开） */
export function searchArticles(
  keyword: string,
  query: PageQuery = {},
): Promise<PageResponse<ArticleListItemDTO>> {
  return http.get<PageResponse<ArticleListItemDTO>>('/articles/search', { params: { keyword, ...query } })
}

/** 获取作者的文章列表（公开） */
export function getArticlesByAuthor(
  authorId: number,
  query: PageQuery = {},
): Promise<PageResponse<ArticleListItemDTO>> {
  return http.get<PageResponse<ArticleListItemDTO>>(`/articles/author/${authorId}`, { params: query })
}
