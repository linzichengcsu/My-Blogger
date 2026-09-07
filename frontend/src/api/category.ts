/**
 * 分类管理接口（CRUD / 树与层级 / 统计 / 搜索）。
 * 对应 openapi.yaml：/api/categories/**
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

/** 获取所有分类（分页） */
export function getAllCategories(query: PageQuery = {}): Promise<PageResponse<CategoryDTO>> {
  return http.get<PageResponse<CategoryDTO>>('/categories', { params: query })
}

/** 创建分类 */
export function createCategory(data: CategoryRequest): Promise<CategoryDTO> {
  return http.post<CategoryDTO>('/categories', data)
}

/** 更新分类 */
export function updateCategory(categoryId: number, data: CategoryRequest): Promise<CategoryDTO> {
  return http.put<CategoryDTO>(`/categories/${categoryId}`, data)
}

/** 删除分类 */
export function deleteCategory(categoryId: number): Promise<void> {
  return http.del<void>(`/categories/${categoryId}`)
}

/** 删除分类并转移其文章到目标分类 */
export function deleteCategoryAndTransferArticles(
  categoryId: number,
  targetCategoryId?: number,
): Promise<void> {
  return http.del<void>(`/categories/${categoryId}/transfer`, {
    params: { targetCategoryId },
  })
}

/** 获取分类详情 */
export function getCategoryById(categoryId: number): Promise<CategoryDTO> {
  return http.get<CategoryDTO>(`/categories/${categoryId}`)
}

/** 通过名称获取分类详情 */
export function getCategoryByName(name: string): Promise<CategoryDTO> {
  return http.get<CategoryDTO>(`/categories/name/${encodeURIComponent(name)}`)
}

/* ---------------- 层级 / 树 ---------------- */

/** 获取所有顶级分类 */
export function getAllTopLevelCategories(): Promise<CategoryDTO[]> {
  return http.get<CategoryDTO[]>('/categories/top-level')
}

/** 获取指定分类的子分类 */
export function getSubCategories(categoryId: number): Promise<CategoryDTO[]> {
  return http.get<CategoryDTO[]>(`/categories/${categoryId}/subcategories`)
}

/** 获取分类完整路径（根到当前） */
export function getCategoryPath(categoryId: number): Promise<CategoryDTO[]> {
  return http.get<CategoryDTO[]>(`/categories/${categoryId}/path`)
}

/** 获取分类树（用于下拉选择器） */
export function buildCategoryTree(): Promise<CategoryTreeDTO[]> {
  return http.get<CategoryTreeDTO[]>('/categories/tree')
}

/** 获取有文章的分类 */
export function getCategoriesWithArticles(): Promise<CategoryDTO[]> {
  return http.get<CategoryDTO[]>('/categories/with-articles')
}

/* ---------------- 统计 / 搜索 ---------------- */

/** 获取分类总数 */
export function getTotalCategoryCount(): Promise<number> {
  return http.get<number>('/categories/stats/total')
}

/** 获取顶级分类数量 */
export function getTopLevelCategoryCount(): Promise<number> {
  return http.get<number>('/categories/stats/top-level')
}

/** 获取指定分类的文章数量（含子分类） */
export function countArticlesInCategory(categoryId: number): Promise<number> {
  return http.get<number>(`/categories/${categoryId}/article-count`)
}

/** 获取所有分类及其文章数统计 */
export function getCategoryStatistics(): Promise<CategoryStatDTO[]> {
  return http.get<CategoryStatDTO[]>('/categories/statistics')
}

/** 获取所有分类的文章占比统计 */
export function getCategoryPercentageStats(): Promise<CategoryStatDTO[]> {
  return http.get<CategoryStatDTO[]>('/categories/statistics/percentage')
}

/** 根据关键词搜索分类 */
export function searchCategories(keyword: string): Promise<CategoryDTO[]> {
  return http.get<CategoryDTO[]>('/categories/search', { params: { keyword } })
}
