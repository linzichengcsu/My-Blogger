/**
 * 标签模块类型（对齐 openapi.yaml 标签管理相关 schema）。
 */

/** 标签 */
export interface TagDTO {
  id?: number
  createdAt?: string
  updatedAt?: string
  name?: string
  articleCount?: number
}

/** 创建/更新标签请求 */
export interface TagRequest {
  /** 1-30 字符，必填 */
  name: string
}
