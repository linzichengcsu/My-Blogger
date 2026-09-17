/**
 * 收藏夹模块类型（原“分类”，已降级为用户收藏夹，对齐 openapi.yaml 相关 schema）。
 */

/** 收藏夹 */
export interface CategoryDTO {
  id?: number
  createdAt?: string
  updatedAt?: string
  name?: string
  description?: string
  parentCategoryId?: number
  parentCategoryName?: string
  articleCount?: number
  /** 子收藏夹（树形返回时嵌套） */
  subCategories?: CategoryDTO[]
}

/** 创建/更新收藏夹请求 */
export interface CategoryRequest {
  /** 2-20 字符，必填 */
  name: string
  description?: string
  parentCategoryId?: number
}

/** 收藏夹树节点（用于前端下拉选择器，children 为递归子节点） */
export interface CategoryTreeDTO {
  id?: number
  name?: string
  description?: string
  articleCount?: number
  /** 子节点（后端 buildCategoryTree 递归填充，叶子节点为空数组） */
  children?: CategoryTreeDTO[]
}

/** 收藏夹统计（文章数、占比） */
export interface CategoryStatDTO {
  categoryName?: string
  articleCount?: number
  /** 占比，0-100 的 double */
  percentage?: number
}
