/**
 * 文章模块类型（对齐 openapi.yaml 文章管理相关 schema）。
 */

/** 文章状态 */
export const ARTICLE_STATUS = ['DRAFT', 'RELEASE', 'ARCHIVE'] as const
export type ArticleStatus = (typeof ARTICLE_STATUS)[number]

/** 文章列表项 */
export interface ArticleListItemDTO {
  id?: number
  createdAt?: string
  updatedAt?: string
  title?: string
  summary?: string
  coverImage?: string
  author?: import('./user').UserProfileDTO
  categories?: import('./category').CategoryDTO[]
  likeCount?: number
  commentCount?: number
  favoriteCount?: number
}

/** 文章详情（含正文、标签与交互状态） */
export interface ArticleDetailDTO {
  id?: number
  createdAt?: string
  updatedAt?: string
  title?: string
  content?: string
  summary?: string
  coverImage?: string
  author?: import('./user').UserProfileDTO
  categories?: import('./category').CategoryDTO[]
  tags?: string[]
  status?: ArticleStatus
  likeCount?: number
  favoriteCount?: number
  commentCount?: number
  /** 当前登录用户是否已点赞 */
  isLiked?: boolean
  /** 当前登录用户是否已收藏 */
  isFavorite?: boolean
}

/** 创建文章请求 */
export interface ArticleCreateRequest {
  /** 5-100 字符 */
  title: string
  /** 至少 20 字符（Markdown 正文） */
  content: string
  /** 摘要，最多 200 字符 */
  summary?: string
  coverImage?: string
  /** 收藏夹 ID 列表（可选，原分类字段已降级为用户收藏夹） */
  categoryIds?: number[]
  /** 默认 DRAFT */
  status?: ArticleStatus
  tags?: string[]
}

/** 更新文章请求（字段均可选） */
export interface ArticleUpdateRequest {
  title?: string
  content?: string
  summary?: string
  coverImage?: string
  categoryIds?: number[]
  status?: ArticleStatus
  tags?: string[]
}
