/**
 * 收藏夹管理接口（CRUD / 树与层级 / 统计 / 搜索）。
 * 对应 openapi.yaml：/api/categories/**
 * 注意：SecurityConfig 要求 /api/categories/** 全部认证（无公开路径），
 * 所有调用都需要携带 Token，未登录会收到 401。
 */
import { http } from './request'
import type { PageQuery, PageResponse } from '../types/common'
import type {
  CategoryDTO,
  CategoryRequest,
  CategoryStatDTO,
  CategoryTreeDTO,
} from '../types/category'

/* ---------------- CRUD ---------------- */

/** 获取所有收藏夹（分页，需管理员；后端仅支持 page/size/sortBy，sortDirection 不生效） */
export function getAllCategories(query: PageQuery = {}): Promise<PageResponse<CategoryDTO>> {
  return http.get<PageResponse<CategoryDTO>>('/categories', { params: query })
}

/** 创建收藏夹（需登录） */
export function createCategory(data: CategoryRequest): Promise<CategoryDTO> {
  return http.post<CategoryDTO>('/categories', data)
}

/** 更新收藏夹（需登录） */
export function updateCategory(categoryId: number, data: CategoryRequest): Promise<CategoryDTO> {
  return http.put<CategoryDTO>(`/categories/${categoryId}`, data)
}

/** 删除收藏夹（需登录） */
export function deleteCategory(categoryId: number): Promise<void> {
  return http.del<void>(`/categories/${categoryId}`)
}

/** 删除收藏夹并转移其文章到目标收藏夹（需登录） */
export function deleteCategoryAndTransferArticles(
  categoryId: number,
  targetCategoryId?: number,
): Promise<void> {
  return http.del<void>(`/categories/${categoryId}/transfer`, {
    params: { targetCategoryId },
  })
}

/** 获取收藏夹详情（需登录） */
export function getCategoryById(categoryId: number): Promise<CategoryDTO> {
  return http.get<CategoryDTO>(`/categories/${categoryId}`)
}

/** 通过名称获取收藏夹详情（需登录） */
export function getCategoryByName(name: string): Promise<CategoryDTO> {
  return http.get<CategoryDTO>(`/categories/name/${encodeURIComponent(name)}`)
}

/* ---------------- 层级 / 树 ---------------- */

/** 获取所有顶级收藏夹（需管理员） */
export function getAllTopLevelCategories(): Promise<CategoryDTO[]> {
  return http.get<CategoryDTO[]>('/categories/top-level')
}

/** 获取指定收藏夹的子收藏夹（需登录） */
export function getSubCategories(categoryId: number): Promise<CategoryDTO[]> {
  return http.get<CategoryDTO[]>(`/categories/${categoryId}/subcategories`)
}

/** 获取收藏夹完整路径（根到当前，需管理员） */
export function getCategoryPath(categoryId: number): Promise<CategoryDTO[]> {
  return http.get<CategoryDTO[]>(`/categories/${categoryId}/path`)
}

/** 获取收藏夹树（需管理员；返回嵌套 children，用于下拉选择器） */
export function buildCategoryTree(): Promise<CategoryTreeDTO[]> {
  return http.get<CategoryTreeDTO[]>('/categories/tree')
}

/** 获取有文章的收藏夹（需管理员） */
export function getCategoriesWithArticles(): Promise<CategoryDTO[]> {
  return http.get<CategoryDTO[]>('/categories/with-articles')
}

/* ---------------- 统计 / 搜索 ---------------- */

/** 获取收藏夹总数（需管理员） */
export function getTotalCategoryCount(): Promise<number> {
  return http.get<number>('/categories/stats/total')
}

/** 获取顶级收藏夹数量（需管理员） */
export function getTopLevelCategoryCount(): Promise<number> {
  return http.get<number>('/categories/stats/top-level')
}

/** 获取指定收藏夹的文章数量（含子收藏夹，需登录） */
export function countArticlesInCategory(categoryId: number): Promise<number> {
  return http.get<number>(`/categories/${categoryId}/article-count`)
}

/** 获取所有收藏夹及其文章数统计（需管理员） */
export function getCategoryStatistics(): Promise<CategoryStatDTO[]> {
  return http.get<CategoryStatDTO[]>('/categories/statistics')
}

/** 获取所有收藏夹的文章占比统计（需管理员） */
export function getCategoryPercentageStats(): Promise<CategoryStatDTO[]> {
  return http.get<CategoryStatDTO[]>('/categories/statistics/percentage')
}

/** 根据关键词搜索收藏夹（需登录） */
export function searchCategories(keyword: string): Promise<CategoryDTO[]> {
  return http.get<CategoryDTO[]>('/categories/search', { params: { keyword } })
}
